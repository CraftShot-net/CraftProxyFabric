package net.craftproxy.mod.client.screens;

import com.mojang.blaze3d.platform.InputConstants;
import net.craftproxy.mod.CraftProxy;
import net.craftproxy.mod.client.chat.ChatMessage;
import net.craftproxy.mod.client.chat.ChatService;
import net.craftproxy.mod.client.chat.ChatServices;
import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.compat.Gfx;
import net.craftproxy.mod.client.managers.FriendManager;
import net.craftproxy.mod.client.managers.FriendManager.UserDto;
import net.craftproxy.mod.client.managers.FriendManager.WorldInviteDto;
import net.craftproxy.mod.client.social.WorldInvites;
import net.craftproxy.mod.client.ui.Anim;
import net.craftproxy.mod.client.ui.ContextMenu;
import net.craftproxy.mod.client.ui.Hits;
import net.craftproxy.mod.client.ui.Icon;
import net.craftproxy.mod.client.ui.ScrollArea;
import net.craftproxy.mod.client.ui.Theme;
import net.craftproxy.mod.client.ui.UiButton;
import net.craftproxy.mod.client.ui.UiDialog;
import net.craftproxy.mod.client.ui.UiRender;
import net.craftproxy.mod.client.ui.UiScreen;
import net.craftproxy.mod.client.ui.UiTextField;
import net.craftproxy.mod.client.utils.SkinManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Social hub: friend list with search, requests and world invites on the left, direct chat
 * with the selected friend on the right. Right-clicking a friend opens a context menu
 * (message, world invite, copy name, remove).
 */
public class SocialScreen extends UiScreen {
    private static final String TRANSLATION_PREFIX = "screen.craftproxy.social.";
    private static final Identifier FRIENDS_ICON = CraftProxy.id("textures/gui/icon_friends.png");

    private static SocialScreen INSTANCE;

    public static SocialScreen getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SocialScreen();
        }
        return INSTANCE;
    }

    // --- Layout constants ---
    private static final int SECTION_H = 18;
    private static final int FRIEND_H = 26;
    private static final int INVITE_H = 32;
    private static final int EMPTY_H = 40;
    private static final int ADD_H = 58;
    private static final int CHAT_HEADER_H = 36;
    private static final int COMPOSER_H = 20;
    private static final long GROUP_WINDOW_MS = 3 * 60 * 1000L;
    private static final long STATUS_REFRESH_INTERVAL_MS = 5000L;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // --- Data ---
    private final List<UserDto> friends = new CopyOnWriteArrayList<>();
    private final List<UserDto> requests = new CopyOnWriteArrayList<>();
    private final List<WorldInviteDto> worldInvites = new CopyOnWriteArrayList<>();
    private final Map<String, Boolean> onlineStatusByName = new ConcurrentHashMap<>();
    private final Map<String, Long> statusFetchAtMillis = new ConcurrentHashMap<>();
    private final Set<String> inFlightStatusFetches = ConcurrentHashMap.newKeySet();

    private Consumer<List<UserDto>> friendsListener;
    private Consumer<List<UserDto>> requestsListener;
    private Consumer<List<WorldInviteDto>> worldInvitesListener;
    private Consumer<Integer> chatListener;

    // --- State ---
    private int selectedFriendId = -1;
    // The search field doubles as "add friend" input after the add button focused it.
    private boolean addHintMode;
    private boolean scrollChatToBottom = true;
    private int lastMessageCount = -1;
    private final Set<String> collapsedSections = new HashSet<>();
    private final Map<Integer, String> drafts = new HashMap<>();

    // --- Layout ---
    private int wx, wy, ww, wh, sideW;
    private int headerBottom;

    // --- Widgets ---
    private UiTextField searchField;
    private UiTextField chatField;
    private UiButton closeButton;
    private UiButton addButton;
    private UiButton sendButton;
    private UiButton inviteButton;
    private UiButton moreButton;
    private final ScrollArea listScroll = new ScrollArea();
    private final ScrollArea chatScroll = new ScrollArea();
    private final Hits hits = new Hits();
    private final Anim.Group<String> hover = new Anim.Group<>();
    private final Anim chatAppear = new Anim();

    private SocialScreen() {
        super(tr("title"));
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(TRANSLATION_PREFIX + key, args);
    }

    private static String trs(String key, Object... args) {
        return tr(key, args).getString();
    }

    private static FriendManager friendManager() {
        return FriendManager.isInitialized() ? FriendManager.getInstance() : null;
    }

    private static ChatService chat() {
        return ChatServices.get();
    }

    // =====================================================================================
    // Lifecycle
    // =====================================================================================

    @Override
    protected void init() {
        super.init();
        this.ww = Math.min(560, this.width - 16);
        this.wh = Math.min(330, this.height - 16);
        this.wx = (this.width - ww) / 2;
        this.wy = (this.height - wh) / 2;
        this.sideW = Mth.clamp(ww * 36 / 100, 130, 180);
        this.headerBottom = wy + Theme.HEADER_HEIGHT;

        registerListeners();

        String search = searchField != null ? searchField.getValue() : "";
        String message = chatField != null ? chatField.getValue() : "";

        searchField = new UiTextField(this.font, 32, tr("search.hint"), Icon.SEARCH);
        chatField = new UiTextField(this.font, chat().maxLength(), tr("chat.hint_generic"), null);
        searchField.setValue(search);
        chatField.setValue(message);
        searchField.box.setResponder(value -> listScroll.scrollToTop());
        this.addRenderableWidget(searchField.box);
        this.addRenderableWidget(chatField.box);

        closeButton = UiButton.icon(Icon.CLOSE, UiButton.Style.GHOST, null, () -> ClientCompat.setScreen(null));
        addButton = UiButton.icon(Icon.USER_ADD, UiButton.Style.SECONDARY, null, this::addFromSearch)
                .tooltip(() -> {
                    String name = searchName();
                    if (name.isEmpty()) return trs("add.tooltip");
                    return findFriend(name) != null ? trs("add.tooltip_open", name) : trs("add.tooltip_name", name);
                });
        sendButton = UiButton.icon(Icon.SEND, UiButton.Style.PRIMARY, trs("chat.send"), this::sendChatMessage)
                .enabledWhen(() -> !chatField.getValue().isBlank());
        inviteButton = new UiButton(tr("invite.button"), Icon.WORLD, UiButton.Style.PRIMARY, () -> {
            UserDto friend = selectedFriend();
            if (friend != null) inviteFriend(friend);
        }).enabledWhen(() -> {
            UserDto friend = selectedFriend();
            return friend != null && inviteBlockReason(friend) == null;
        }).tooltip(() -> {
            UserDto friend = selectedFriend();
            String reason = friend != null ? inviteBlockReason(friend) : null;
            return reason != null ? reason : trs("invite.tooltip");
        });
        moreButton = UiButton.icon(Icon.MORE, UiButton.Style.GHOST, trs("more"), () -> {
            UserDto friend = selectedFriend();
            if (friend != null) openFriendMenu(friend, moreButton.x, moreButton.y + moreButton.h + 2);
        });

        layoutWidgets();
        addHintMode = false;
        UserDto selected = selectedFriend();
        if (selected != null) chatField.box.setHint(hint(tr("chat.hint", selected.name())));
        scrollChatToBottom = true;
        refreshData();
    }

    private void layoutWidgets() {
        int sx = wx + 8;
        int sideInner = sideW - 16;
        closeButton.bounds(wx + ww - 24, wy + 6, 18, 18);
        searchField.setBounds(sx, headerBottom + 8, sideInner - 22, 18);
        addButton.bounds(sx + sideInner - 18, headerBottom + 8, 18, 18);

        int px = wx + sideW;
        int pw = ww - sideW;
        moreButton.bounds(px + pw - 26, headerBottom + 9, 18, 18);
        int inviteW = UiButton.widthFor(this.font, tr("invite.button"), Icon.WORLD);
        inviteButton.bounds(moreButton.x - 4 - inviteW, headerBottom + 9, inviteW, 18);
        int composerY = wy + wh - COMPOSER_H - 10;
        chatField.setBounds(px + 10, composerY, pw - 20 - 26, COMPOSER_H);
        sendButton.bounds(px + pw - 10 - 22, composerY, 22, COMPOSER_H);
    }

    private void registerListeners() {
        FriendManager manager = friendManager();
        unregisterListeners();
        if (manager != null) {
            manager.setScreenOpen(true);
            friendsListener = fresh -> this.minecraft.execute(() -> {
                replace(friends, fresh);
                refreshStatusesForCurrentFriends();
            });
            requestsListener = fresh -> this.minecraft.execute(() -> replace(requests, fresh));
            worldInvitesListener = fresh -> this.minecraft.execute(() -> replace(worldInvites, fresh));
            manager.addFriendsListener(friendsListener);
            manager.addPendingRequestsListener(requestsListener);
            manager.addWorldInvitesListener(worldInvitesListener);
        }
        chatListener = friendId -> this.minecraft.execute(() -> {
            if (friendId == selectedFriendId && ClientCompat.currentScreen() == this) chat().markRead(friendId);
        });
        chat().addListener(chatListener);
    }

    private void unregisterListeners() {
        FriendManager manager = friendManager();
        if (manager != null) {
            if (friendsListener != null) manager.removeFriendsListener(friendsListener);
            if (requestsListener != null) manager.removePendingRequestsListener(requestsListener);
            if (worldInvitesListener != null) manager.removeWorldInvitesListener(worldInvitesListener);
        }
        if (chatListener != null) chat().removeListener(chatListener);
    }

    @Override
    public void removed() {
        super.removed();
        unregisterListeners();
        FriendManager manager = friendManager();
        if (manager != null) manager.setScreenOpen(false);
        if (selectedFriendId >= 0 && chatField != null) drafts.put(selectedFriendId, chatField.getValue());
    }

    private static <T> void replace(List<T> target, List<T> fresh) {
        target.clear();
        target.addAll(fresh);
    }

    private void refreshData() {
        FriendManager manager = friendManager();
        if (manager == null) return;

        replace(friends, manager.getCachedFriends());
        replace(requests, manager.getCachedPendingRequests());
        replace(worldInvites, manager.getCachedWorldInvites());
        refreshStatusesForCurrentFriends();

        manager.getFriends().thenAccept(fresh -> this.minecraft.execute(() -> {
            replace(friends, fresh);
            refreshStatusesForCurrentFriends();
        })).exceptionally(ex -> null);
        manager.getPendingRequests().thenAccept(fresh -> this.minecraft.execute(() -> replace(requests, fresh))).exceptionally(ex -> null);
        manager.getWorldInvites().thenAccept(fresh -> this.minecraft.execute(() -> replace(worldInvites, fresh))).exceptionally(ex -> null);
    }

    private void refreshStatusesForCurrentFriends() {
        Set<String> validNames = new HashSet<>();
        for (UserDto friend : friends) {
            validNames.add(friend.name());
            refreshOnlineStatus(friend.name(), true);
        }
        onlineStatusByName.keySet().removeIf(name -> !validNames.contains(name));
        statusFetchAtMillis.keySet().removeIf(name -> !validNames.contains(name));
        inFlightStatusFetches.removeIf(name -> !validNames.contains(name));
    }

    private void refreshOnlineStatus(String username, boolean force) {
        FriendManager manager = friendManager();
        if (manager == null) return;
        long now = System.currentTimeMillis();
        Long lastFetchedAt = statusFetchAtMillis.get(username);
        if (!force && lastFetchedAt != null && now - lastFetchedAt < STATUS_REFRESH_INTERVAL_MS) return;
        if (!inFlightStatusFetches.add(username)) return;

        statusFetchAtMillis.put(username, now);
        manager.getIngameOnlineStatus(username)
                .thenAccept(status -> this.minecraft.execute(() -> onlineStatusByName.put(username, status.is_ingame_online())))
                .exceptionally(ex -> null)
                .whenComplete((result, error) -> inFlightStatusFetches.remove(username));
    }

    private boolean isOnline(String name) {
        return onlineStatusByName.getOrDefault(name, false);
    }

    private UserDto selectedFriend() {
        if (selectedFriendId < 0) return null;
        for (UserDto friend : friends) {
            if (friend.id() == selectedFriendId) return friend;
        }
        return null;
    }

    private static Component hint(Component text) {
        return Component.literal(text.getString()).withStyle(s -> s.withColor(Theme.TEXT_MUTED & 0xFFFFFF));
    }

    // =====================================================================================
    // Actions
    // =====================================================================================

    private String searchName() {
        return searchField == null ? "" : searchField.getValue().trim();
    }

    private UserDto findFriend(String name) {
        for (UserDto friend : friends) {
            if (friend.name().equalsIgnoreCase(name)) return friend;
        }
        return null;
    }

    /** The exact-name friend, else the only friend matching the search, else null. */
    private UserDto singleFriendMatch() {
        String name = searchName();
        if (name.isEmpty()) return null;
        UserDto exact = findFriend(name);
        if (exact != null) return exact;
        String q = query();
        UserDto match = null;
        for (UserDto friend : friends) {
            if (!matches(friend.name(), q)) continue;
            if (match != null) return null;
            match = friend;
        }
        return match;
    }

    private UserDto findRequest(String name) {
        for (UserDto request : requests) {
            if (request.name().equalsIgnoreCase(name)) return request;
        }
        return null;
    }

    /**
     * The add button works on the search text: empty focuses the field for typing a name,
     * an existing friend opens the chat, an incoming request gets accepted, anything else
     * sends a friend request.
     */
    private void addFromSearch() {
        String name = searchName();
        if (name.isEmpty()) {
            addHintMode = true;
            this.setFocused(searchField.box);
            return;
        }
        UserDto friend = findFriend(name);
        if (friend != null) {
            selectFriend(friend);
            return;
        }
        UserDto request = findRequest(name);
        if (request != null) {
            answerRequest(request, true);
            searchField.setValue("");
            return;
        }
        sendFriendRequest(name);
    }

    private void sendFriendRequest(String name) {
        FriendManager manager = friendManager();
        if (manager == null || name.isEmpty() || addButton.loading) return;
        addButton.loading = true;
        manager.sendRequestByName(name).thenAccept(success -> this.minecraft.execute(() -> {
            addButton.loading = false;
            if (success) {
                if (searchName().equalsIgnoreCase(name)) searchField.setValue("");
                addHintMode = false;
                showNotice(tr("notice.request_sent", name), Icon.CHECK, Theme.SUCCESS);
            } else {
                showNotice(tr("notice.request_failed", name), Icon.CLOSE, Theme.DANGER);
            }
        })).exceptionally(ex -> {
            this.minecraft.execute(() -> {
                addButton.loading = false;
                showNotice(tr("notice.request_failed", name), Icon.CLOSE, Theme.DANGER);
            });
            return null;
        });
    }

    private void selectFriend(UserDto friend) {
        if (friend.id() == selectedFriendId) {
            this.setFocused(chatField.box);
            return;
        }
        if (selectedFriendId >= 0) drafts.put(selectedFriendId, chatField.getValue());
        selectedFriendId = friend.id();
        chatField.setVisible(true);
        chatField.setValue(drafts.getOrDefault(friend.id(), ""));
        chatField.box.setHint(hint(tr("chat.hint", friend.name())));
        chat().markRead(friend.id());
        scrollChatToBottom = true;
        lastMessageCount = -1;
        chatAppear.set(0.0F);
        this.setFocused(chatField.box);
    }

    private void sendChatMessage() {
        UserDto friend = selectedFriend();
        String text = chatField.getValue().strip();
        if (friend == null || text.isEmpty()) return;
        chatField.setValue("");
        drafts.remove(friend.id());
        scrollChatToBottom = true;
        chat().send(friend.id(), text).exceptionally(ex -> {
            this.minecraft.execute(() -> showNotice(tr("notice.message_failed"), Icon.CLOSE, Theme.DANGER));
            return null;
        });
    }

    /** Why a world invite to {@code friend} is not possible right now, or {@code null}. */
    private String inviteBlockReason(UserDto friend) {
        if (!WorldInvites.isInSingleplayerWorld()) return trs("invite.need_singleplayer");
        if (!isOnline(friend.name())) return trs("invite.offline");
        if (WorldInvites.isOnCooldown(friend.name())) return trs("invite.cooldown");
        return null;
    }

    private void inviteFriend(UserDto friend) {
        if (inviteBlockReason(friend) != null) return;
        String name = friend.name();
        showNotice(tr("notice.invite_sending", name), Icon.WORLD, Theme.ACCENT);
        WorldInvites.sendInvite(name, success -> this.minecraft.execute(() -> {
            if (success) showNotice(tr("notice.invite_sent", name), Icon.CHECK, Theme.SUCCESS);
            else showNotice(tr("notice.invite_failed", name), Icon.CLOSE, Theme.DANGER);
        }));
    }

    private void confirmRemove(UserDto friend) {
        openDialog(new UiDialog(tr("remove.title"), tr("remove.message", friend.name()), tr("remove.confirm"), true, () -> {
            FriendManager manager = friendManager();
            if (manager == null) return;
            manager.removeFriend(friend.id()).thenAccept(success -> this.minecraft.execute(() -> {
                if (success) {
                    if (selectedFriendId == friend.id()) selectedFriendId = -1;
                    refreshData();
                    showNotice(tr("notice.removed", friend.name()), Icon.CHECK, Theme.SUCCESS);
                } else {
                    showNotice(tr("notice.remove_failed", friend.name()), Icon.CLOSE, Theme.DANGER);
                }
            })).exceptionally(ex -> {
                this.minecraft.execute(() -> showNotice(tr("notice.remove_failed", friend.name()), Icon.CLOSE, Theme.DANGER));
                return null;
            });
        }));
    }

    private void answerRequest(UserDto request, boolean accept) {
        FriendManager manager = friendManager();
        if (manager == null) return;
        (accept ? manager.acceptRequest(request.id()) : manager.declineRequest(request.id()))
                .thenAccept(success -> this.minecraft.execute(() -> {
                    if (success) {
                        refreshData();
                        if (accept) showNotice(tr("notice.accepted", request.name()), Icon.CHECK, Theme.SUCCESS);
                        else showNotice(tr("notice.declined", request.name()), Icon.CLOSE, Theme.TEXT_SECONDARY);
                    } else {
                        showNotice(tr("notice.action_failed"), Icon.CLOSE, Theme.DANGER);
                    }
                }))
                .exceptionally(ex -> {
                    this.minecraft.execute(() -> showNotice(tr("notice.action_failed"), Icon.CLOSE, Theme.DANGER));
                    return null;
                });
    }

    private void joinInvite(WorldInviteDto invite) {
        if (this.minecraft.level != null) {
            openDialog(new UiDialog(tr("join.title"), tr("join.message", invite.worldName(), invite.hostName()),
                    tr("join.confirm"), false, () -> WorldInvites.join(invite)));
        } else {
            WorldInvites.join(invite);
        }
    }

    private void copyToClipboard(String text) {
        this.minecraft.keyboardHandler.setClipboard(text);
        showNotice(tr("notice.copied"), Icon.COPY, Theme.ACCENT);
    }

    private void openFriendMenu(UserDto friend, int x, int y) {
        String reason = inviteBlockReason(friend);
        ContextMenu menu = new ContextMenu()
                .header(friend.name())
                .add(trs("menu.message"), Icon.CHAT, () -> selectFriend(friend))
                .add(trs("menu.invite"), Icon.WORLD, reason == null, reason, () -> inviteFriend(friend))
                .add(trs("menu.copy_name"), Icon.COPY, () -> copyToClipboard(friend.name()))
                .separator()
                .danger(trs("menu.remove"), Icon.USER_REMOVE, () -> confirmRemove(friend));
        openContextMenu(menu, x, y);
    }

    private void openRequestMenu(UserDto request, int x, int y) {
        openContextMenu(new ContextMenu()
                .header(request.name())
                .add(trs("request.accept"), Icon.CHECK, () -> answerRequest(request, true))
                .add(trs("menu.copy_name"), Icon.COPY, () -> copyToClipboard(request.name()))
                .separator()
                .danger(trs("request.decline"), Icon.CLOSE, () -> answerRequest(request, false)), x, y);
    }

    private void openInviteMenu(WorldInviteDto invite, int x, int y) {
        openContextMenu(new ContextMenu()
                .header(invite.worldName())
                .add(trs("menu.join"), Icon.PLAY, () -> joinInvite(invite))
                .add(trs("menu.copy_address"), Icon.COPY, () -> copyToClipboard(invite.tunnel_ip())), x, y);
    }

    // =====================================================================================
    // Rendering
    // =====================================================================================

    @Override
    protected int noticeY() {
        int below = wy + wh + 16;
        return below + 9 < this.height ? below : wy + wh - 46;
    }

    @Override
    protected int noticeCenterX() {
        // Inside the window the pill belongs to the chat pane, not across the sidebar divider.
        boolean inside = noticeY() < wy + wh;
        return inside ? wx + sideW + (ww - sideW) / 2 : super.noticeCenterX();
    }

    @Override
    protected int noticeMaxWidth() {
        return noticeY() < wy + wh ? ww - sideW - 24 : super.noticeMaxWidth();
    }

    @Override
    protected int noticeMaxRight() {
        return noticeY() < wy + wh ? wx + ww - 8 : super.noticeMaxRight();
    }

    @Override
    protected void drawBackdrop(Gfx g, int mx, int my) {
        hits.clear();
        for (UserDto friend : friends) refreshOnlineStatus(friend.name(), false);

        drawWindow(g, wx, wy, ww, wh);
        drawHeader(g, mx, my);
        drawSidebar(g, mx, my);
        drawChatPane(g, mx, my);
    }

    private void drawHeader(Gfx g, int mx, int my) {
        g.blit(FRIENDS_ICON, wx + 10, wy + 7, 0.0F, 0.0F, 16, 16, 16, 16, 16, 16);
        Component title = tr("title").withStyle(s -> s.withBold(true));
        UiRender.text(g, this.font, title, wx + 32, wy + 11, Theme.TEXT);
        int online = 0;
        for (UserDto friend : friends) if (isOnline(friend.name())) online++;
        String summary = trs(friends.size() == 1 ? "header.summary.one" : "header.summary", online, friends.size());
        int sx = wx + 32 + this.font.width(title) + 8;
        UiRender.roundRect(g, sx, wy + 10, 3, 3, 1, online > 0 ? Theme.ONLINE : Theme.OFFLINE);
        UiRender.text(g, this.font, UiRender.ellipsize(this.font, summary, closeButton.x - sx - 16), sx + 7, wy + 11, Theme.TEXT_MUTED);
        drawButton(g, closeButton, mx, my);
        UiRender.divider(g, wx + 1, wx + ww - 1, headerBottom - 1, Theme.BORDER);
    }

    // --- Sidebar ---

    private enum RowKind {SECTION, INVITE, REQUEST, FRIEND, EMPTY, ADD}

    private record Row(RowKind kind, String key, String label, int count, int accent, UserDto user, WorldInviteDto invite) {
        int height() {
            return switch (kind) {
                case SECTION -> SECTION_H;
                case INVITE -> INVITE_H;
                case REQUEST, FRIEND -> FRIEND_H;
                case EMPTY -> EMPTY_H;
                case ADD -> ADD_H;
            };
        }
    }

    private String query() {
        return searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(String value, String query) {
        return query.isEmpty() || value.toLowerCase(Locale.ROOT).contains(query);
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        if (friendManager() == null) {
            rows.add(new Row(RowKind.EMPTY, "offline", trs("empty.not_connected"), 0, 0, null, null));
            return rows;
        }
        String q = query();
        // Vanilla hides the field hint while focused, so the add mode explains itself here.
        if (addHintMode && q.isEmpty()) rows.add(new Row(RowKind.EMPTY, "add_hint", trs("add.hint"), 0, 0, null, null));

        List<WorldInviteDto> invites = worldInvites.stream()
                .filter(i -> matches(i.hostName(), q) || matches(i.worldName(), q)).toList();
        List<UserDto> pending = requests.stream().filter(r -> matches(r.name(), q)).toList();
        List<UserDto> online = new ArrayList<>();
        List<UserDto> offline = new ArrayList<>();
        for (UserDto friend : friends) {
            if (!matches(friend.name(), q)) continue;
            (isOnline(friend.name()) ? online : offline).add(friend);
        }
        Comparator<UserDto> byName = Comparator.comparing(u -> u.name().toLowerCase(Locale.ROOT));
        online.sort(byName);
        offline.sort(byName);

        section(rows, "invites", trs("section.invites"), invites.size(), Theme.ACCENT, () -> {
            for (WorldInviteDto invite : invites) rows.add(new Row(RowKind.INVITE, null, null, 0, 0, null, invite));
        });
        section(rows, "requests", trs("section.requests"), pending.size(), Theme.WARNING, () -> {
            for (UserDto request : pending) rows.add(new Row(RowKind.REQUEST, null, null, 0, 0, request, null));
        });
        section(rows, "online", trs("section.online"), online.size(), 0, () -> {
            for (UserDto friend : online) rows.add(new Row(RowKind.FRIEND, null, null, 0, 0, friend, null));
        });
        section(rows, "offline", trs("section.offline"), offline.size(), 0, () -> {
            for (UserDto friend : offline) rows.add(new Row(RowKind.FRIEND, null, null, 0, 0, friend, null));
        });

        if (rows.isEmpty()) {
            if (q.isEmpty()) {
                rows.add(new Row(RowKind.EMPTY, "empty", trs("empty.no_friends"), 0, 0, null, null));
            } else {
                // No local match: offer sending a friend request to the typed name.
                rows.add(new Row(RowKind.ADD, "add", searchName(), 0, 0, null, null));
            }
        }
        return rows;
    }

    private void section(List<Row> rows, String key, String label, int count, int accent, Runnable children) {
        if (count == 0) return;
        rows.add(new Row(RowKind.SECTION, key, label, count, accent, null, null));
        if (!collapsedSections.contains(key)) children.run();
    }

    private void drawSidebar(Gfx g, int mx, int my) {
        int top = headerBottom;
        int bottom = wy + wh - 1;
        // Sidebar background: only the bottom-left corner follows the rounded window.
        UiRender.roundRect(g, wx + 1, top, sideW - 1, bottom - top, 5, Theme.SIDEBAR);
        UiRender.fill(g, wx + 1, top, wx + sideW, top + 6, Theme.SIDEBAR);
        UiRender.fill(g, wx + sideW - 6, top, wx + sideW, bottom, Theme.SIDEBAR);
        UiRender.fill(g, wx + sideW, top, wx + sideW + 1, bottom, Theme.BORDER);

        searchField.draw(g, mx, my);
        // Leaving the field ends "add mode".
        if (addHintMode && !searchField.isFocused()) addHintMode = false;
        drawButton(g, addButton, mx, my);
        if (addHintMode) UiRender.roundBorder(g, addButton.x, addButton.y, addButton.w, addButton.h, 3, Theme.ACCENT);

        int listTop = headerBottom + 32;
        listScroll.setBounds(wx + 4, listTop, sideW - 6, bottom - 4 - listTop);
        List<Row> rows = buildRows();
        int contentH = 2;
        for (Row row : rows) contentH += row.height();
        listScroll.setContentHeight(contentH);
        listScroll.update();

        listScroll.begin(g);
        hits.clip(listScroll.x, listScroll.y, listScroll.w, listScroll.h);
        boolean inList = listScroll.contains(mx, my);
        int rx = listScroll.x + 2;
        int rw = listScroll.w - 8;
        int ry = listScroll.y - listScroll.offset();
        for (Row row : rows) {
            int h = row.height();
            if (ry + h >= listScroll.y && ry <= listScroll.y + listScroll.h) {
                boolean hovered = inList && UiRender.inside(mx, my, rx, ry, rw, h);
                switch (row.kind()) {
                    case SECTION -> drawSectionRow(g, row, rx, ry, rw, hovered);
                    case INVITE -> drawInviteRow(g, row.invite(), rx, ry, rw, hovered, inList, mx, my);
                    case REQUEST -> drawRequestRow(g, row.user(), rx, ry, rw, hovered, inList, mx, my);
                    case FRIEND -> drawFriendRow(g, row.user(), rx, ry, rw, hovered);
                    case EMPTY -> drawEmptyRow(g, row.label(), rx, ry, rw);
                    case ADD -> drawAddRow(g, row.label(), rx, ry, rw, inList, mx, my);
                }
            }
            ry += h;
        }
        hits.clearClip();
        listScroll.end(g, mx, my);
    }

    private void drawSectionRow(Gfx g, Row row, int x, int y, int w, boolean hovered) {
        boolean collapsed = collapsedSections.contains(row.key());
        float t = hover.update("section:" + row.key(), hovered);
        int color = Theme.mix(Theme.TEXT_MUTED, Theme.TEXT_SECONDARY, t);
        Icon chevron = collapsed ? Icon.CHEVRON_RIGHT : Icon.CHEVRON_DOWN;
        chevron.drawCentered(g, x + 6, y + 9, 1, color);
        String label = row.label().toUpperCase(Locale.ROOT);
        UiRender.text(g, this.font, label, x + 13, y + 6, color);
        int cx = x + 13 + this.font.width(label) + 5;
        if (row.accent() != 0) {
            UiRender.pill(g, this.font, Integer.toString(row.count()), cx, y + 4, Theme.withAlpha(row.accent(), 0x40), Theme.shade(row.accent(), 0.35F));
        } else {
            UiRender.text(g, this.font, Integer.toString(row.count()), cx, y + 6, Theme.withAlpha(Theme.TEXT_MUTED, 0xB0));
        }
        hits.add(x, y, w, SECTION_H, () -> {
            if (!collapsedSections.remove(row.key())) collapsedSections.add(row.key());
        });
    }

    private void drawFriendRow(Gfx g, UserDto friend, int x, int y, int w, boolean hovered) {
        boolean selected = friend.id() == selectedFriendId;
        float t = hover.update("friend:" + friend.id(), hovered);
        int bg = selected ? Theme.SURFACE_ACTIVE : Theme.mix(Theme.SIDEBAR, Theme.SURFACE_HOVER, t);
        if (selected || t > 0.01F) UiRender.roundRect(g, x, y + 1, w, FRIEND_H - 2, 4, bg);
        if (selected) UiRender.roundRect(g, x, y + 7, 2, FRIEND_H - 14, 1, Theme.ACCENT);

        boolean online = isOnline(friend.name());
        Identifier skin = SkinManager.getSkinAsyncPublic(friend.name()).get().texture();
        UiRender.head(g, skin, x + 6, y + 4, 18, bg);
        UiRender.statusDot(g, x + 23, y + 21, 5, online ? Theme.ONLINE : Theme.OFFLINE, bg);

        int unread = chat().unreadCount(friend.id());
        int badgeSpace = unread > 0 ? 18 : 0;
        int textX = x + 30;
        int textW = w - 30 - 4 - badgeSpace;
        UiRender.text(g, this.font, UiRender.ellipsize(this.font, friend.name(), textW), textX, y + 4, online || selected ? Theme.TEXT : Theme.TEXT_SECONDARY);

        List<ChatMessage> history = chat().history(friend.id());
        String sub;
        int subColor;
        if (!history.isEmpty()) {
            ChatMessage last = history.get(history.size() - 1);
            sub = (last.outgoing() ? trs("chat.you_prefix") : "") + last.text();
            subColor = unread > 0 ? Theme.TEXT_SECONDARY : Theme.TEXT_MUTED;
        } else {
            sub = trs(online ? "status.online" : "status.offline");
            subColor = online ? Theme.mix(Theme.ONLINE, Theme.TEXT_MUTED, 0.35F) : Theme.TEXT_MUTED;
        }
        UiRender.text(g, this.font, UiRender.ellipsize(this.font, sub, textW), textX, y + 14, subColor);
        UiRender.countBadge(g, this.font, unread, x + w - 5, y + 9);

        hits.add(x, y, w, FRIEND_H, (mx, my) -> selectFriend(friend), (mx, my) -> openFriendMenu(friend, mx, my));
    }

    private void drawRequestRow(Gfx g, UserDto request, int x, int y, int w, boolean hovered, boolean inList, int mx, int my) {
        float t = hover.update("request:" + request.id(), hovered);
        int bg = Theme.mix(Theme.SIDEBAR, Theme.SURFACE_HOVER, t);
        if (t > 0.01F) UiRender.roundRect(g, x, y + 1, w, FRIEND_H - 2, 4, bg);

        Identifier skin = SkinManager.getSkinAsyncPublic(request.name()).get().texture();
        UiRender.head(g, skin, x + 6, y + 4, 18, bg);

        int buttonsX = x + w - 36;
        int textW = buttonsX - (x + 30) - 4;
        UiRender.text(g, this.font, UiRender.ellipsize(this.font, request.name(), textW), x + 30, y + 4, Theme.TEXT);
        UiRender.text(g, this.font, UiRender.ellipsize(this.font, trs("request.subtitle"), textW), x + 30, y + 14, Theme.TEXT_MUTED);

        hits.add(x, y, w, FRIEND_H, null, (cx, cy) -> openRequestMenu(request, cx, cy));
        miniButton(g, "accept:" + request.id(), buttonsX, y + 6, Icon.CHECK, Theme.SUCCESS, trs("request.accept"),
                inList, mx, my, () -> answerRequest(request, true));
        miniButton(g, "decline:" + request.id(), buttonsX + 18, y + 6, Icon.CLOSE, Theme.DANGER, trs("request.decline"),
                inList, mx, my, () -> answerRequest(request, false));
    }

    private void drawInviteRow(Gfx g, WorldInviteDto invite, int x, int y, int w, boolean hovered, boolean inList, int mx, int my) {
        String key = invite.hostName() + "|" + invite.worldName() + "|" + invite.tunnel_ip();
        float t = hover.update("invite:" + key, hovered);
        int bg = Theme.mix(0xFF1B2140, 0xFF222A50, t);
        UiRender.card(g, x, y + 2, w, INVITE_H - 4, 4, bg, Theme.mix(0xFF2B3566, Theme.ACCENT, t * 0.6F));

        Identifier skin = SkinManager.getSkinAsyncPublic(invite.hostName()).get().texture();
        UiRender.head(g, skin, x + 6, y + 7, 18, bg);

        int joinW = Math.max(30, this.font.width(trs("invite.join")) + 10);
        int joinX = x + w - joinW - 5;
        int textW = joinX - (x + 30) - 4;
        UiRender.text(g, this.font, UiRender.ellipsize(this.font, invite.worldName(), textW), x + 30, y + 7, Theme.TEXT);
        UiRender.text(g, this.font, UiRender.ellipsize(this.font, trs("invite.from", invite.hostName()), textW), x + 30, y + 17, Theme.mix(Theme.ACCENT_HOVER, Theme.TEXT_MUTED, 0.4F));

        hits.add(x, y, w, INVITE_H, null, (cx, cy) -> openInviteMenu(invite, cx, cy));
        boolean joinHovered = inList && UiRender.inside(mx, my, joinX, y + 9, joinW, 14);
        float jt = hover.update("join:" + key, joinHovered);
        UiRender.roundRect(g, joinX, y + 9, joinW, 14, 3, Theme.mix(Theme.ACCENT, Theme.ACCENT_HOVER, jt));
        UiRender.textCentered(g, this.font, trs("invite.join"), joinX + joinW / 2, y + 12, Theme.TEXT_ON_ACCENT);
        hits.add(joinX, y + 9, joinW, 14, () -> {
            UiButton.playClick();
            joinInvite(invite);
        });
    }

    private void miniButton(Gfx g, String key, int x, int y, Icon icon, int color, String tooltip,
                            boolean inList, int mx, int my, Runnable action) {
        boolean hovered = inList && UiRender.inside(mx, my, x, y, 14, 14);
        float t = hover.update(key, hovered);
        UiRender.roundRect(g, x, y, 14, 14, 3, Theme.mix(Theme.withAlpha(color, 0x33), color, t));
        icon.drawCentered(g, x + 7, y + 7, 1, Theme.mix(Theme.shade(color, 0.3F), Theme.TEXT_ON_ACCENT, t));
        if (hovered) showTooltip(tooltip);
        hits.add(x, y, 14, 14, () -> {
            UiButton.playClick();
            action.run();
        });
    }

    /** "No friend named X" plus a button that sends X a friend request. */
    private void drawAddRow(Gfx g, String name, int x, int y, int w, boolean inList, int mx, int my) {
        // Name on its own line: the sidebar is too narrow for "No friend named <16 chars>".
        String text = trs("empty.no_friend_named");
        UiRender.text(g, this.font, text, x + (w - this.font.width(text)) / 2, y + 6, Theme.TEXT_MUTED);
        String shown = UiRender.ellipsize(this.font, name, w - 8);
        UiRender.text(g, this.font, shown, x + (w - this.font.width(shown)) / 2, y + 17, Theme.TEXT);

        int bx = x + 6;
        int by = y + 32;
        int bw = w - 12;
        int bh = 18;
        boolean hovered = inList && UiRender.inside(mx, my, bx, by, bw, bh);
        float t = hover.update("add-request", hovered && !addButton.loading);
        UiRender.card(g, bx, by, bw, bh, 3, Theme.mix(Theme.ACCENT_DIM, Theme.ACCENT, t), 0);
        if (addButton.loading) {
            UiRender.spinner(g, bx + bw / 2, by + bh / 2, Theme.TEXT_ON_ACCENT);
        } else {
            String label = UiRender.ellipsize(this.font, trs("add.request_button"), bw - 24);
            int contentW = Icon.USER_ADD.width + 5 + this.font.width(label);
            int cx = bx + (bw - contentW) / 2;
            Icon.USER_ADD.draw(g, cx, by + (bh - Icon.USER_ADD.height + 1) / 2, 1, Theme.TEXT_ON_ACCENT);
            UiRender.text(g, this.font, label, cx + Icon.USER_ADD.width + 5, by + 6, Theme.TEXT_ON_ACCENT);
        }
        hits.add(bx, by, bw, bh, () -> {
            if (addButton.loading) return;
            UiButton.playClick();
            sendFriendRequest(name);
        });
    }

    private void drawEmptyRow(Gfx g, String text, int x, int y, int w) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), w - 16);
        int ty = y + 8;
        for (FormattedCharSequence line : lines) {
            UiRender.text(g, this.font, line, x + (w - this.font.width(line)) / 2, ty, Theme.TEXT_MUTED);
            ty += 10;
        }
    }

    // --- Chat pane ---

    private void drawChatPane(Gfx g, int mx, int my) {
        int px = wx + sideW + 1;
        int pw = ww - sideW - 2;
        UserDto friend = selectedFriend();

        boolean hasFriend = friend != null;
        chatField.setVisible(hasFriend);
        sendButton.visible = hasFriend;
        inviteButton.visible = hasFriend;
        moreButton.visible = hasFriend;

        if (!hasFriend) {
            drawNoChatState(g, px, pw);
            return;
        }

        float appear = Anim.easeOutCubic(chatAppear.update(1.0F, 14.0F));
        float saved = UiRender.alpha;
        UiRender.alpha = saved * (0.35F + 0.65F * appear);

        // Header
        boolean online = isOnline(friend.name());
        int ay = headerBottom + 7;
        Identifier skin = SkinManager.getSkinAsyncPublic(friend.name()).get().texture();
        UiRender.head(g, skin, px + 10, ay, 22, Theme.WINDOW);
        UiRender.statusDot(g, px + 30, ay + 20, 6, online ? Theme.ONLINE : Theme.OFFLINE, Theme.WINDOW);
        Component name = Component.literal(friend.name()).withStyle(s -> s.withBold(true));
        UiRender.text(g, this.font, name, px + 40, ay + 2, Theme.TEXT);
        UiRender.text(g, this.font, trs(online ? "status.online" : "status.offline"), px + 40, ay + 13,
                online ? Theme.ONLINE : Theme.TEXT_MUTED);
        hits.add(px + 6, ay - 3, 40 + this.font.width(name), 28, null, (cx, cy) -> openFriendMenu(friend, cx, cy));
        drawButton(g, inviteButton, mx, my);
        drawButton(g, moreButton, mx, my);
        UiRender.divider(g, px, px + pw, headerBottom + CHAT_HEADER_H, Theme.DIVIDER);

        // Placeholder banner while there is no chat backend
        int messagesTop = headerBottom + CHAT_HEADER_H + 1;
        if (!chat().isConnected()) {
            int by = messagesTop + 6;
            // Wrapped instead of ellipsized so the whole notice stays readable on narrow windows.
            List<FormattedCharSequence> bannerLines = this.font.split(tr("chat.preview_banner"), pw - 46);
            int bannerH = 6 + bannerLines.size() * 10;
            UiRender.card(g, px + 10, by, pw - 20, bannerH, 3, 0xFF161B2E, 0xFF252D4D);
            Icon.INFO.draw(g, px + 17, by + 6, 1, Theme.ACCENT_HOVER);
            int lineY = by + 4;
            for (FormattedCharSequence line : bannerLines) {
                UiRender.text(g, this.font, line, px + 26, lineY, Theme.TEXT_SECONDARY);
                lineY += 10;
            }
            messagesTop = by + bannerH + 2;
        }

        // Messages
        int composerY = chatField.y;
        chatScroll.setBounds(px + 4, messagesTop, pw - 8, composerY - 6 - messagesTop);
        List<ChatMessage> history = chat().history(friend.id());
        List<ChatItem> items = layoutMessages(history, chatScroll.w - 16);
        int contentH = items.isEmpty() ? 0 : items.get(items.size() - 1).y() + items.get(items.size() - 1).h() + 10;
        chatScroll.setContentHeight(contentH);
        if (history.size() != lastMessageCount) {
            if (scrollChatToBottom || chatScroll.isAtBottom() || lastMessageCount < 0) {
                chatScroll.scrollToBottom(lastMessageCount < 0);
            }
            lastMessageCount = history.size();
        }
        if (scrollChatToBottom) {
            chatScroll.scrollToBottom(false);
            scrollChatToBottom = false;
        }
        chatScroll.update();

        if (history.isEmpty()) {
            int cy = chatScroll.y + chatScroll.h / 2;
            Icon.CHAT.drawCentered(g, px + pw / 2, cy - 18, 2, Theme.withAlpha(Theme.TEXT_MUTED, 0x90));
            drawCenteredBold(g, trs("chat.empty.title"), px + pw / 2, cy - 2, Theme.TEXT_SECONDARY);
            UiRender.textCentered(g, this.font, trs("chat.empty.subtitle", friend.name()), px + pw / 2, cy + 10, Theme.TEXT_MUTED);
        } else {
            chatScroll.begin(g);
            hits.clip(chatScroll.x, chatScroll.y, chatScroll.w, chatScroll.h);
            int left = chatScroll.x + 6;
            int right = chatScroll.x + chatScroll.w - 8;
            int baseY = chatScroll.y + 6 - chatScroll.offset();
            for (ChatItem item : items) {
                int iy = baseY + item.y();
                if (iy + item.h() < chatScroll.y || iy > chatScroll.y + chatScroll.h) continue;
                if (item.day() != null) drawDaySeparator(g, item.day(), left, right, iy);
                else drawBubble(g, item, left, right, iy);
            }
            hits.clearClip();
            chatScroll.end(g, mx, my);
        }

        // Composer
        chatField.draw(g, mx, my);
        drawButton(g, sendButton, mx, my);
        UiRender.alpha = saved;
    }

    private void drawNoChatState(Gfx g, int px, int pw) {
        int cx = px + pw / 2;
        int cy = headerBottom + (wy + wh - headerBottom) / 2 - 8;
        UiRender.roundRect(g, cx - 20, cy - 44, 40, 40, 8, Theme.ACCENT_DIM);
        Icon.CHAT.drawCentered(g, cx, cy - 24, 3, Theme.ACCENT_HOVER);
        drawCenteredBold(g, trs("nochat.title"), cx, cy + 6, Theme.TEXT);
        UiRender.textCentered(g, this.font, trs("nochat.subtitle"), cx, cy + 19, Theme.TEXT_SECONDARY);
        UiRender.textCentered(g, this.font, trs("nochat.hint"), cx, cy + 31, Theme.TEXT_MUTED);
    }

    private void drawCenteredBold(Gfx g, String text, int cx, int y, int color) {
        Component bold = Component.literal(text).withStyle(s -> s.withBold(true));
        UiRender.text(g, this.font, bold, cx - this.font.width(bold) / 2, y, color);
    }

    private record ChatItem(int y, int h, String day, ChatMessage message, List<FormattedCharSequence> lines,
                            int bubbleW, boolean showMeta) {
    }

    private static LocalDate dateOf(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private List<ChatItem> layoutMessages(List<ChatMessage> history, int width) {
        List<ChatItem> items = new ArrayList<>();
        int maxBubble = Math.max(60, width * 72 / 100);
        int y = 0;
        LocalDate previousDay = null;
        ChatMessage previous = null;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < history.size(); i++) {
            ChatMessage message = history.get(i);
            ChatMessage next = i + 1 < history.size() ? history.get(i + 1) : null;
            LocalDate day = dateOf(message.timestamp());
            if (!day.equals(previousDay)) {
                if (previous != null) y += 6;
                String label = day.equals(today) ? trs("chat.today")
                        : day.equals(today.minusDays(1)) ? trs("chat.yesterday")
                        : day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
                items.add(new ChatItem(y, 14, label, null, null, 0, false));
                y += 18;
                previous = null;
                previousDay = day;
            }
            boolean grouped = previous != null && previous.outgoing() == message.outgoing()
                    && message.timestamp() - previous.timestamp() < GROUP_WINDOW_MS;
            if (previous != null) y += grouped ? 2 : 6;

            List<FormattedCharSequence> lines = this.font.split(Component.literal(message.text()), maxBubble - 12);
            int textW = 0;
            for (FormattedCharSequence line : lines) textW = Math.max(textW, this.font.width(line));
            int bubbleH = lines.size() * 10 + 6;
            boolean lastOfGroup = next == null || next.outgoing() != message.outgoing()
                    || next.timestamp() - message.timestamp() >= GROUP_WINDOW_MS
                    || !dateOf(next.timestamp()).equals(day);
            int h = bubbleH + (lastOfGroup ? 11 : 0);
            items.add(new ChatItem(y, h, null, message, lines, textW + 12, lastOfGroup));
            y += h;
            previous = message;
        }
        return items;
    }

    private void drawDaySeparator(Gfx g, String label, int left, int right, int y) {
        int cx = (left + right) / 2;
        int w = this.font.width(label) + 12;
        UiRender.divider(g, left + 4, cx - w / 2 - 6, y + 7, Theme.DIVIDER);
        UiRender.divider(g, cx + w / 2 + 6, right - 4, y + 7, Theme.DIVIDER);
        UiRender.roundRect(g, cx - w / 2, y + 1, w, 12, 3, Theme.SURFACE);
        UiRender.textCentered(g, this.font, label, cx, y + 3, Theme.TEXT_MUTED);
    }

    private void drawBubble(Gfx g, ChatItem item, int left, int right, int y) {
        ChatMessage message = item.message();
        boolean out = message.outgoing();
        int bubbleH = item.lines().size() * 10 + 6;
        int bx = out ? right - item.bubbleW() : left;
        int bg = message.state() == ChatMessage.State.FAILED ? Theme.withAlpha(Theme.DANGER, 0x80) : (out ? Theme.BUBBLE_OUT : Theme.BUBBLE_IN);
        UiRender.roundRect(g, bx, y, item.bubbleW(), bubbleH, 5, bg);
        int ty = y + 4;
        for (FormattedCharSequence line : item.lines()) {
            UiRender.text(g, this.font, line, bx + 6, ty, out ? Theme.TEXT_ON_ACCENT : Theme.TEXT);
            ty += 10;
        }
        if (item.showMeta()) {
            String meta = Instant.ofEpochMilli(message.timestamp()).atZone(ZoneId.systemDefault()).format(TIME_FORMAT);
            if (message.state() == ChatMessage.State.SENDING) meta = trs("chat.sending");
            else if (message.state() == ChatMessage.State.FAILED) meta = trs("chat.failed");
            int color = message.state() == ChatMessage.State.FAILED ? Theme.DANGER_HOVER : Theme.TEXT_MUTED;
            if (out) UiRender.textRight(g, this.font, meta, right - 2, y + bubbleH + 2, color);
            else UiRender.text(g, this.font, meta, bx + 2, y + bubbleH + 2, color);
        }
        hits.add(bx, y, item.bubbleW(), bubbleH, null, (cx, cy) -> openContextMenu(new ContextMenu()
                .add(trs("chat.copy"), Icon.COPY, () -> copyToClipboard(message.text())), cx, cy));
    }

    // =====================================================================================
    // Input
    // =====================================================================================

    @Override
    protected boolean onMouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (listScroll.mouseClicked(mx, my) || chatScroll.mouseClicked(mx, my)) return true;
            for (UiButton b : new UiButton[]{closeButton, addButton, sendButton, inviteButton, moreButton}) {
                if (b.click(mx, my)) return true;
            }
            if (clickField(searchField, mx, my) || clickField(chatField, mx, my)) return true;
        }
        return hits.click(mx, my, button);
    }

    @Override
    protected boolean onMouseDragged(double mx, double my, int button) {
        return listScroll.mouseDragged(my) || chatScroll.mouseDragged(my);
    }

    @Override
    protected void onMouseReleased(double mx, double my, int button) {
        listScroll.mouseReleased();
        chatScroll.mouseReleased();
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        return listScroll.scroll(mx, my, amount) || chatScroll.scroll(mx, my, amount);
    }

    @Override
    protected boolean onKey(int key) {
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            if (searchField.isFocused()) {
                // Enter only opens a chat; requests always need an explicit click.
                UserDto match = singleFriendMatch();
                if (match != null) selectFriend(match);
                return true;
            }
            if (chatField.isFocused()) {
                sendChatMessage();
                return true;
            }
        }
        return false;
    }
}
