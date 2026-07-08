package net.craftproxy.mod.client.screens;

import net.craftproxy.mod.client.MCTunnelClient;
import net.craftproxy.mod.client.managers.FriendManager;
import net.craftproxy.mod.client.managers.FriendManager.UserDto;
import net.craftproxy.mod.client.managers.TunnelManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static net.craftproxy.mod.client.utils.SkinManager.getSkinAsyncPublic;

public class FriendsScreen extends Screen {
    private static final String TRANSLATION_PREFIX = "screen.craftproxy.friends.";

    private static FriendsScreen INSTANCE;

    public static FriendsScreen getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FriendsScreen();
        }
        return INSTANCE;
    }

    // --- State & Navigation ---
    private enum Tab {FRIENDS, REQUESTS, WORLD_INVITES}

    private Tab activeTab = Tab.FRIENDS;
    private int scrollY = 0;

    // --- Dimensions & Layout ---
    private static final int MODAL_WIDTH = 360;
    private static final int MODAL_HEIGHT = 240;
    private static final int LIST_ITEM_HEIGHT = 40;
    private int leftPos;
    private int topPos;

    private TextFieldWidget addFriendField;
    private ButtonWidget addFriendButton;
    private ButtonWidget tabFriendsButton;
    private ButtonWidget tabRequestsButton;
    private ButtonWidget tabWorldInvitesButton;

    private final List<UserDto> friends = new CopyOnWriteArrayList<>();
    private final List<UserDto> requests = new CopyOnWriteArrayList<>();
    private final List<FriendManager.WorldInviteDto> worldInvites = new CopyOnWriteArrayList<>();
    private final Map<String, Boolean> onlineStatusByName = new ConcurrentHashMap<>();
    private final Map<String, Long> statusFetchAtMillis = new ConcurrentHashMap<>();
    private final Set<String> inFlightStatusFetches = ConcurrentHashMap.newKeySet();
    private static final long STATUS_REFRESH_INTERVAL_MS = 5000L;

    private final Map<String, Long> inviteButtonCooldowns = new ConcurrentHashMap<>();
    private static final long INVITE_COOLDOWN_MS = 2000L;

    private Text addFriendStatusMessage;
    private int addFriendStatusColor;
    private long addFriendStatusExpiresAtMillis;
    private static final long ADD_FRIEND_STATUS_DURATION_MS = 4000L;

    private FriendsScreen() {
        super(tr("title"));
    }

    private Consumer<List<UserDto>> friendsListener;
    private Consumer<List<UserDto>> requestsListener;
    private Consumer<List<FriendManager.WorldInviteDto>> worldInvitesListener;

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - MODAL_WIDTH) / 2;
        this.topPos = (this.height - MODAL_HEIGHT) / 2;

        int padding = 10;
        int buttonWidth = (MODAL_WIDTH - (padding * 4)) / 3;

        FriendManager manager = getFriendManagerOrClose();
        if (manager == null) {
            return;
        }
        manager.setScreenOpen(true);

        friendsListener = fresh -> this.client.execute(() -> {
            this.friends.clear();
            this.friends.addAll(fresh);
            refreshStatusesForCurrentFriends();
            updateState();
        });
        requestsListener = fresh -> this.client.execute(() -> {
            this.requests.clear();
            this.requests.addAll(fresh);
            updateState();
        });
        worldInvitesListener = fresh -> this.client.execute(() -> {
            this.worldInvites.clear();
            this.worldInvites.addAll(fresh);
            updateState();
        });

        manager.addFriendsListener(friendsListener);
        manager.addPendingRequestsListener(requestsListener);
        manager.addWorldInvitesListener(worldInvitesListener);

        // Tabs
        this.tabFriendsButton = ButtonWidget.builder(tr("tab.friends"), button -> {
            activeTab = Tab.FRIENDS;
            scrollY = 0;
            updateState();
        }).dimensions(leftPos + padding, topPos + 25, buttonWidth, 20).build();
        this.addDrawableChild(this.tabFriendsButton);

        this.tabRequestsButton = ButtonWidget.builder(tr("tab.requests", requests.size()), button -> {
            activeTab = Tab.REQUESTS;
            scrollY = 0;
            updateState();
        }).dimensions(leftPos + (padding * 2) + buttonWidth, topPos + 25, buttonWidth, 20).build();
        this.addDrawableChild(this.tabRequestsButton);

        this.tabWorldInvitesButton = ButtonWidget.builder(tr("tab.world_invites", worldInvites.size()), button -> {
            activeTab = Tab.WORLD_INVITES;
            scrollY = 0;
            updateState();
        }).dimensions(leftPos + (padding * 3) + (buttonWidth * 2), topPos + 25, buttonWidth, 20).build();
        this.addDrawableChild(this.tabWorldInvitesButton);

        // Add Friend Area
        int bottomY = topPos + MODAL_HEIGHT - 30;
        this.addFriendField = new TextFieldWidget(this.textRenderer, leftPos + padding, bottomY, MODAL_WIDTH - 90, 20, tr("input.player_name"));
        this.addFriendField.setMaxLength(16);
        this.addDrawableChild(this.addFriendField);

        this.addFriendButton = ButtonWidget.builder(tr("button.add"), button -> sendFriendRequest()).dimensions(leftPos + MODAL_WIDTH - 75, bottomY, 65, 20).build();
        this.addDrawableChild(this.addFriendButton);

        refreshData();
        updateState();
    }

    @Override
    public void removed() {
        super.removed();
        FriendManager manager = FriendManager.getInstanceOrNull();
        if (manager == null) {
            return;
        }
        if (friendsListener != null) {
            manager.removeFriendsListener(friendsListener);
        }
        if (requestsListener != null) {
            manager.removePendingRequestsListener(requestsListener);
        }
        if (worldInvitesListener != null) {
            manager.removeWorldInvitesListener(worldInvitesListener);
        }
        manager.setScreenOpen(false);

    }

    private void refreshData() {
        FriendManager manager = getFriendManagerOrClose();
        if (manager == null) {
            return;
        }

        this.friends.clear();
        this.friends.addAll(manager.getCachedFriends());
        refreshStatusesForCurrentFriends();
        this.requests.clear();
        this.requests.addAll(manager.getCachedPendingRequests());
        this.worldInvites.clear();
        this.worldInvites.addAll(manager.getCachedWorldInvites());
        updateState();

        manager.getFriends().thenAccept(apiFriends -> this.client.execute(() -> {
            this.friends.clear();
            this.friends.addAll(apiFriends);
            refreshStatusesForCurrentFriends();
            updateState();
        })).exceptionally(ex -> null);

        manager.getPendingRequests().thenAccept(apiRequests -> this.client.execute(() -> {
            this.requests.clear();
            this.requests.addAll(apiRequests);
            updateState();
        })).exceptionally(ex -> null);

        manager.getWorldInvites().thenAccept(apiWorldInvites -> this.client.execute(() -> {
            this.worldInvites.clear();
            this.worldInvites.addAll(apiWorldInvites);
            updateState();
        })).exceptionally(ex -> null);
    }

    private void updateState() {
        if (this.tabFriendsButton != null && this.tabRequestsButton != null && this.tabWorldInvitesButton != null) {
            this.tabFriendsButton.active = (activeTab != Tab.FRIENDS);
            this.tabRequestsButton.active = (activeTab != Tab.REQUESTS);
            this.tabWorldInvitesButton.active = (activeTab != Tab.WORLD_INVITES);
            this.tabRequestsButton.setMessage(tr("tab.requests", requests.size()));
            this.tabWorldInvitesButton.setMessage(tr("tab.world_invites", worldInvites.size()));
        }
    }

    private void sendFriendRequest() {
        if (this.addFriendField != null) {
            String name = this.addFriendField.getText().trim();
            if (!name.isEmpty()) {
                this.addFriendButton.active = false;

                FriendManager manager = getFriendManagerOrClose();
                if (manager == null) {
                    this.addFriendButton.active = true;
                    return;
                }
                manager.sendRequestByName(name).thenAccept(success -> this.client.execute(() -> {
                    this.addFriendButton.active = true;
                    if (success) {
                        setAddFriendStatus(tr("log.request_sent", name), 0xFF55FF55);
                        this.addFriendField.setText("");
                    } else {
                        setAddFriendStatus(tr("error.request_failed", name), 0xFFFF5555);
                    }
                })).exceptionally(ex -> {
                    this.client.execute(() -> {
                        this.addFriendButton.active = true;
                        setAddFriendStatus(tr("error.request_failed", name), 0xFFFF5555);
                    });
                    return null;
                });
            }
        }
    }

    private void setAddFriendStatus(Text message, int color) {
        this.addFriendStatusMessage = message;
        this.addFriendStatusColor = color;
        this.addFriendStatusExpiresAtMillis = System.currentTimeMillis() + ADD_FRIEND_STATUS_DURATION_MS;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xB0000000);

        // Modal Background
        context.fill(leftPos - 1, topPos - 1, leftPos + MODAL_WIDTH + 1, topPos + MODAL_HEIGHT + 1, 0xFF000000);
        context.fill(leftPos, topPos, leftPos + MODAL_WIDTH, topPos + MODAL_HEIGHT, 0xFF313233);
        context.fill(leftPos, topPos, leftPos + MODAL_WIDTH, topPos + 1, 0xFF555555);
        context.fill(leftPos, topPos, leftPos + 1, topPos + MODAL_HEIGHT, 0xFF555555);

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, tr("header").getString(), leftPos + (MODAL_WIDTH / 2), topPos + 8, 0xFFFFAA00);

        super.render(context, mouseX, mouseY, delta);
        drawAddFriendPlaceholder(context);
        drawList(context, mouseX, mouseY);
        drawAddFriendStatus(context);
    }

    private void drawAddFriendPlaceholder(DrawContext context) {
        if (this.addFriendField == null || !this.addFriendField.getText().isEmpty()) {
            return;
        }
        context.drawText(
                this.textRenderer,
                tr("input.player_name").getString(),
                this.addFriendField.getX() + 4,
                this.addFriendField.getY() + 6,
                0xFF777777,
                false
        );
    }

    private void drawAddFriendStatus(DrawContext context) {
        if (addFriendStatusMessage == null) {
            return;
        }
        if (System.currentTimeMillis() >= addFriendStatusExpiresAtMillis) {
            addFriendStatusMessage = null;
            return;
        }
        String text = truncateText(addFriendStatusMessage.getString(), MODAL_WIDTH - 20);
        context.drawCenteredTextWithShadow(this.textRenderer, text, leftPos + (MODAL_WIDTH / 2), topPos + MODAL_HEIGHT - 39, addFriendStatusColor);
    }

    private void drawList(DrawContext context, int mouseX, int mouseY) {
        int listX = leftPos + 10;
        int listY = topPos + 55;
        int listWidth = MODAL_WIDTH - 20;
        int listHeight = MODAL_HEIGHT - 95;

        context.fill(listX - 1, listY - 1, listX + listWidth + 1, listY + listHeight + 1, 0xFF000000);
        context.fill(listX, listY, listX + listWidth, listY + listHeight, 0xFF1A1A1A);

        context.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int renderY = listY - scrollY;
        int totalItems = switch (activeTab) {
            case FRIENDS -> friends.size();
            case REQUESTS -> requests.size();
            case WORLD_INVITES -> worldInvites.size();
        };

        switch (activeTab) {
            case FRIENDS -> {
                for (UserDto friend : friends) {
                    refreshOnlineStatus(friend.name(), false);
                    boolean isOnline = onlineStatusByName.getOrDefault(friend.name(), false);
                    drawListItem(context, mouseX, mouseY, listX, renderY, listWidth, friend.name(), isOnline, true);
                    renderY += LIST_ITEM_HEIGHT;
                }
            }
            case REQUESTS -> {
                for (UserDto req : requests) {
                    drawListItem(context, mouseX, mouseY, listX, renderY, listWidth, req.name(), false, false);
                    renderY += LIST_ITEM_HEIGHT;
                }
            }
            case WORLD_INVITES -> {
                for (FriendManager.WorldInviteDto invite : worldInvites) {
                    drawWorldInviteItem(context, mouseX, mouseY, listX, renderY, listWidth, invite);
                    renderY += LIST_ITEM_HEIGHT;
                }
            }
        }

        context.disableScissor();
        drawScrollbar(context, listX, listY, listWidth, listHeight, totalItems);
    }

    private void drawScrollbar(DrawContext context, int x, int y, int width, int height, int totalItems) {
        int maxScroll = Math.max(0, (totalItems * LIST_ITEM_HEIGHT) - height);
        if (maxScroll > 0) {
            int scrollbarX = x + width - 6;
            int thumbHeight = Math.max(20, (int) ((height / (float) (totalItems * LIST_ITEM_HEIGHT)) * height));
            int thumbY = y + (int) ((scrollY / (float) maxScroll) * (height - thumbHeight));

            context.fill(scrollbarX, y, scrollbarX + 5, y + height, 0x80000000);
            context.fill(scrollbarX, thumbY, scrollbarX + 5, thumbY + thumbHeight, 0xFF888888);
            context.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight - 1, 0xFFCCCCCC);
        }
    }

    private String truncateText(String text, int maxWidth) {
        if (this.textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String truncated = text;
        while (this.textRenderer.getWidth(truncated + "...") > maxWidth && !truncated.isEmpty()) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + "...";
    }

    private void drawListItem(DrawContext context, int mouseX, int mouseY, int x, int y, int width, String name, boolean isOnline, boolean isFriend) {
        int listStartY = topPos + 55;
        int listEndY = topPos + MODAL_HEIGHT - 40;

        if (y + FriendsScreen.LIST_ITEM_HEIGHT < listStartY || y > listEndY) return;

        boolean hovered = mouseX >= x && mouseX <= x + width - 8 && mouseY >= Math.max(y, listStartY) && mouseY <= Math.min(y + FriendsScreen.LIST_ITEM_HEIGHT, listEndY);
        if (hovered) {
            context.fill(x, y, x + width - 8, y + FriendsScreen.LIST_ITEM_HEIGHT, 0x15FFFFFF);
        }

        // --- Avatar ---
        // NOTE: `PlayerSkin` is a newer-version class - your SkinManager helper will need its own
        // porting for 1.20.1 (e.g. returning a plain Identifier or a SkinTextures-based wrapper).
        // Adjust this block once SkinManager is ported.
        Identifier skinTexture = getFixedSkinTexture(getSkinAsyncPublic(name));

        context.fill(x + 4, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 13, x + 30, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) + 13, 0xFF000000);
        PlayerSkinDrawer.draw(context, skinTexture, x + 5, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 12, 24);

        int btnWidth = 65;
        int btnMargin = 12;
        int reservedButtonSpace = (btnWidth * 2) + btnMargin + 10;
        int maxTextWidth = width - 38 - reservedButtonSpace;

        // --- Info ---
        String displayName = truncateText(name, maxTextWidth);
        context.drawText(this.textRenderer, displayName, x + 38, y + 8, 0xFFFFFFFF, false);

        if (isFriend) {
            int statusColor = isOnline ? 0xFF55FF55 : 0xFFFF5555;
            String statusText = isOnline ? tr("status.online").getString() : tr("status.offline").getString();

            context.fill(x + 38, y + 22, x + 44, y + 28, 0xFF000000);
            context.fill(x + 39, y + 23, x + 43, y + 27, statusColor);
            context.drawText(this.textRenderer, statusText, x + 48, y + 21, 0xFFAAAAAA, false);
        } else {
            String subText = truncateText(tr("request.incoming").getString(), maxTextWidth);
            context.drawText(this.textRenderer, subText, x + 38, y + 21, 0xFFAAAAAA, false);
        }

        // --- Buttons ---
        int btnHeight = 20;
        int btnY = y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 10;

        if (isFriend) {
            if (isOnline) {
                boolean isDisabled = isInviteOnCooldown(name) || !isInSingleplayerWorld();
                drawVanillaStyleButton(context, mouseX, mouseY, x + width - (btnWidth * 2) - btnMargin - 5, btnY, btnWidth, btnHeight, tr("button.invite"), true, isDisabled);
            }
            drawVanillaStyleButton(context, mouseX, mouseY, x + width - btnWidth - btnMargin, btnY, btnWidth, btnHeight, tr("button.remove"), false, false);
        } else {
            drawVanillaStyleButton(context, mouseX, mouseY, x + width - (btnWidth * 2) - btnMargin - 5, btnY, btnWidth, btnHeight, tr("button.accept"), true, false);
            drawVanillaStyleButton(context, mouseX, mouseY, x + width - btnWidth - btnMargin, btnY, btnWidth, btnHeight, tr("button.decline"), false, false);
        }

        context.drawHorizontalLine(x + 2, x + width - 10, y + FriendsScreen.LIST_ITEM_HEIGHT - 1, 0x20FFFFFF);
    }

    private void drawWorldInviteItem(DrawContext context, int mouseX, int mouseY, int x, int y, int width, FriendManager.WorldInviteDto invite) {
        int listStartY = topPos + 55;
        int listEndY = topPos + MODAL_HEIGHT - 40;

        if (y + FriendsScreen.LIST_ITEM_HEIGHT < listStartY || y > listEndY) return;

        boolean hovered = mouseX >= x && mouseX <= x + width - 8 && mouseY >= Math.max(y, listStartY) && mouseY <= Math.min(y + FriendsScreen.LIST_ITEM_HEIGHT, listEndY);
        if (hovered) {
            context.fill(x, y, x + width - 8, y + FriendsScreen.LIST_ITEM_HEIGHT, 0x15FFFFFF);
        }

        // --- Avatar (host skin) ---
        Identifier skinTexture = getFixedSkinTexture(getSkinAsyncPublic(invite.hostName()));

        context.fill(x + 4, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 13, x + 30, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) + 13, 0xFF000000);
        PlayerSkinDrawer.draw(context, skinTexture, x + 5, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 12, 24);

        // Only one action button now (Accept) since invites can simply be ignored
        int btnWidth = 65;
        int btnMargin = 12;
        int reservedButtonSpace = btnWidth + btnMargin + 10;
        int maxTextWidth = width - 38 - reservedButtonSpace;

        // --- Info ---
        String displayName = truncateText(invite.worldName(), maxTextWidth);
        context.drawText(this.textRenderer, displayName, x + 38, y + 8, 0xFFFFFFFF, false);

        String subText = truncateText(tr("world_invite.from", invite.hostName()).getString(), maxTextWidth);
        context.drawText(this.textRenderer, subText, x + 38, y + 21, 0xFFAAAAAA, false);

        // --- Button ---
        int btnHeight = 20;
        int btnY = y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 10;
        drawVanillaStyleButton(context, mouseX, mouseY, x + width - btnWidth - btnMargin, btnY, btnWidth, btnHeight, tr("button.join"), true, false);

        context.drawHorizontalLine(x + 2, x + width - 10, y + FriendsScreen.LIST_ITEM_HEIGHT - 1, 0x20FFFFFF);
    }

    private void drawVanillaStyleButton(DrawContext context, int mouseX, int mouseY, int x, int y, int w, int h, Text text, boolean positiveAction, boolean disabled) {
        int listStartY = topPos + 55;
        int listEndY = topPos + MODAL_HEIGHT - 40;
        boolean withinListBounds = mouseY >= listStartY && mouseY <= listEndY;
        boolean hovered = !disabled && withinListBounds && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        int baseColor = positiveAction ? 0xFF2B7C2B : 0xFF9E2B2B;
        int hoverColor = positiveAction ? 0xFF3CA83C : 0xFFC63C3C;
        int disabledColor = 0xFF555555;

        int bgColor = disabled ? disabledColor : (hovered ? hoverColor : baseColor);

        context.fill(x, y, x + w, y + h, 0xFF000000);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);
        context.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40FFFFFF);
        context.fill(x + 1, y + 1, x + 2, y + h - 1, 0x40FFFFFF);
        context.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x40000000);
        context.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0x40000000);

        int textColor = disabled ? 0xFF888888 : (hovered ? 0xFFFFFFAA : 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, text.getString(), x + (w / 2), y + (h - 8) / 2 + 1, textColor);
    }

    private void refreshStatusesForCurrentFriends() {
        Set<String> validNames = ConcurrentHashMap.newKeySet();
        for (UserDto friend : friends) {
            validNames.add(friend.name());
            refreshOnlineStatus(friend.name(), true);
        }
        onlineStatusByName.keySet().removeIf(name -> !validNames.contains(name));
        statusFetchAtMillis.keySet().removeIf(name -> !validNames.contains(name));
        inFlightStatusFetches.removeIf(name -> !validNames.contains(name));
    }

    private void refreshOnlineStatus(String username, boolean force) {
        long now = System.currentTimeMillis();
        Long lastFetchedAt = statusFetchAtMillis.get(username);
        if (!force && lastFetchedAt != null && now - lastFetchedAt < STATUS_REFRESH_INTERVAL_MS) {
            return;
        }
        if (!inFlightStatusFetches.add(username)) {
            return;
        }

        statusFetchAtMillis.put(username, now);
        FriendManager manager = FriendManager.getInstanceOrNull();
        if (manager == null) {
            inFlightStatusFetches.remove(username);
            return;
        }
        manager.getIngameOnlineStatus(username)
                .thenAccept(status -> this.client.execute(() -> onlineStatusByName.put(username, status.is_ingame_online())))
                .exceptionally(ex -> null)
                .whenComplete((r, t) -> inFlightStatusFetches.remove(username));
    }

    private static MutableText tr(String key, Object... args) {
        return Text.translatable(TRANSLATION_PREFIX + key, args);
    }

    private FriendManager getFriendManagerOrClose() {
        FriendManager manager = FriendManager.getInstanceOrNull();
        if (manager == null && this.client != null) {
            this.client.setScreen(null);
        }
        return manager;
    }

    private Identifier getFixedSkinTexture(Supplier<Identifier> skinSupplier) {
        Identifier skinId = skinSupplier.get();
        String path = skinId.getPath();
        if (!path.startsWith("textures/") && !path.startsWith("skins/")) {
            return Identifier.of("minecraft", "textures/" + path + ".png");
        }
        return skinId;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = mouseX;
        double my = mouseY;

        int listX = leftPos + 10;
        int listY = topPos + 55;
        int listWidth = MODAL_WIDTH - 20;
        int listHeight = MODAL_HEIGHT - 95;

        if (mx >= listX && mx <= listX + listWidth && my >= listY && my <= listY + listHeight) {
            int clickedIndex = (int) ((my - listY + scrollY) / LIST_ITEM_HEIGHT);
            int itemY = listY - scrollY + (clickedIndex * LIST_ITEM_HEIGHT);

            if (clickedIndex >= 0) {
                int btnWidth = 65;
                int btnHeight = 20;
                int btnY = itemY + (LIST_ITEM_HEIGHT / 2) - 10;
                int btnMargin = 12;

                int btn1X = listX + listWidth - (btnWidth * 2) - btnMargin - 5;
                int btn2X = listX + listWidth - btnWidth - btnMargin;

                boolean clickedBtn1 = mx >= btn1X && mx <= btn1X + btnWidth && my >= btnY && my <= btnY + btnHeight;
                boolean clickedBtn2 = mx >= btn2X && mx <= btn2X + btnWidth && my >= btnY && my <= btnY + btnHeight;

                if (activeTab == Tab.FRIENDS && clickedIndex < friends.size()) {
                    UserDto friend = friends.get(clickedIndex);
                    boolean isOnline = onlineStatusByName.getOrDefault(friend.name(), false);
                    // Button 1: Invite (if Online)
                    if (clickedBtn1 && isOnline && !isInviteOnCooldown(friend.name()) && isInSingleplayerWorld()) {
                        handleInviteClick(friend.name());
                        return true;
                    }
                    // Button 2: Remove
                    if (clickedBtn2) {
                        FriendManager manager = FriendManager.getInstanceOrNull();
                        if (manager == null) {
                            return true;
                        }
                        manager.removeFriend(friend.id()).thenAccept(success -> {
                            if (success) {
                                this.client.execute(this::refreshData);
                            }
                        });
                        return true;
                    }
                } else if (activeTab == Tab.REQUESTS && clickedIndex < requests.size()) {
                    UserDto req = requests.get(clickedIndex);
                    // Button 1: Accept
                    if (clickedBtn1) {
                        FriendManager manager = FriendManager.getInstanceOrNull();
                        if (manager == null) {
                            return true;
                        }
                        manager.acceptRequest(req.id()).thenAccept(success -> {
                            if (success) {
                                this.client.execute(this::refreshData);
                            }
                        });
                        return true;
                    }
                    // Button 2: Decline
                    if (clickedBtn2) {
                        FriendManager manager = FriendManager.getInstanceOrNull();
                        if (manager == null) {
                            return true;
                        }
                        manager.declineRequest(req.id()).thenAccept(success -> {
                            if (success) {
                                this.client.execute(this::refreshData);
                            }
                        });
                        return true;
                    }
                } else if (activeTab == Tab.WORLD_INVITES && clickedIndex < worldInvites.size()) {
                    FriendManager.WorldInviteDto invite = worldInvites.get(clickedIndex);
                    if (clickedBtn2) {
                        this.client.setScreen(null);
                        if (this.client.world != null) {
                            this.client.world.disconnect();
                        }
                        // Always tear down tunnel state before joining an invite, including in-flight connects.
                        MCTunnelClient.getInstance().getTunnelClient().disconnect();

                        ServerAddress address = ServerAddress.parse(invite.tunnel_ip());
                        ServerInfo serverInfo = new ServerInfo(
                                invite.worldName(),
                                invite.tunnel_ip(),
                                false
                        );

                        ConnectScreen.connect(
                                new TitleScreen(),
                                this.client,
                                address,
                                serverInfo,
                                false
                        );

                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleInviteClick(String friendName) {
        if (this.client.getServer() == null) {
            return;
        }

        inviteButtonCooldowns.put(friendName, System.currentTimeMillis());
        playInviteSound();

        String worldName = this.client.getServer().getSaveProperties().getLevelName();

        // NOTE: verify `isRemote()`/`openToLan(...)` names against your genSources output -
        // the "publish to LAN" API surface has shifted somewhat between versions.
        if (!this.client.getServer().isRemote()) {
            this.client.getServer().openToLan(GameMode.SURVIVAL, false, 25565);
        }

        if (MCTunnelClient.getInstance().getTunnelClient().isConnected()) {
            executeApiInvite(friendName, worldName, MCTunnelClient.getInstance().getTunnelClient().getHostname());
        } else {
            TunnelManager.host(this.client, MCTunnelClient.getInstance().getTunnelClient(), hostname -> executeApiInvite(friendName, worldName, hostname));
        }
    }

    private void playInviteSound() {
        if (this.client != null) {
            this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.2f));
        }
    }

    private boolean isInviteOnCooldown(String friendName) {
        Long cooldownStartTime = inviteButtonCooldowns.get(friendName);
        if (cooldownStartTime == null) {
            return false;
        }
        return System.currentTimeMillis() - cooldownStartTime < INVITE_COOLDOWN_MS;
    }

    private boolean isInSingleplayerWorld() {
        return this.client.getServer() != null;
    }

    private void executeApiInvite(String friendName, String worldName, String tunnelIp) {
        FriendManager manager = FriendManager.getInstanceOrNull();
        if (manager == null) {
            return;
        }
        manager.sendWorldInvite(friendName, worldName, tunnelIp).thenAccept(success -> {
            if (success) {
                System.out.println("[CraftProxy] Sent invite to " + friendName + " for world " + worldName + " at " + tunnelIp);
            } else {
                System.err.println("[CraftProxy] Error sending  invite to " + friendName + " for world " + worldName + " at " + tunnelIp);
            }
        }).exceptionally(ex -> {
            System.err.println("[CraftProxy] Error sending invite to " + friendName + " for world " + worldName + " at " + tunnelIp);
            ex.printStackTrace();
            return null;
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int listX = leftPos + 10;
        int listY = topPos + 55;
        int listWidth = MODAL_WIDTH - 20;
        int listHeight = MODAL_HEIGHT - 95;

        if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
            int totalContentHeight = switch (activeTab) {
                case FRIENDS -> friends.size();
                case REQUESTS -> requests.size();
                case WORLD_INVITES -> worldInvites.size();
            } * LIST_ITEM_HEIGHT;
            int maxScroll = Math.max(0, totalContentHeight - listHeight);

            if (maxScroll > 0) {
                this.scrollY -= (int) (amount * 20);
                this.scrollY = MathHelper.clamp(this.scrollY, 0, maxScroll);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.addFriendField != null && this.addFriendField.isFocused()) {
                sendFriendRequest();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.client.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}