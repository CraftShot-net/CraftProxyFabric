package net.craftproxy.mod.client.screens;

import com.mojang.blaze3d.platform.InputConstants;
import net.craftproxy.mod.client.MCTunnelClient;
import net.craftproxy.mod.client.managers.FriendManager;
import net.craftproxy.mod.client.managers.FriendManager.UserDto;
import net.craftproxy.mod.client.managers.TunnelManager;
import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.compat.Gfx;
import net.craftproxy.mod.client.compat.SkinData;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;

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

    private EditBox addFriendField;
    private Button addFriendButton;
    private Button tabFriendsButton;
    private Button tabRequestsButton;
    private Button tabWorldInvitesButton;

    private final List<UserDto> friends = new CopyOnWriteArrayList<>();
    private final List<UserDto> requests = new CopyOnWriteArrayList<>();
    private final List<FriendManager.WorldInviteDto> worldInvites = new CopyOnWriteArrayList<>();
    private final Map<String, Boolean> onlineStatusByName = new ConcurrentHashMap<>();
    private final Map<String, Long> statusFetchAtMillis = new ConcurrentHashMap<>();
    private final Set<String> inFlightStatusFetches = ConcurrentHashMap.newKeySet();
    private static final long STATUS_REFRESH_INTERVAL_MS = 5000L;
    
    private final Map<String, Long> inviteButtonCooldowns = new ConcurrentHashMap<>();
    private static final long INVITE_COOLDOWN_MS = 2000L;

    private Component addFriendStatusMessage;
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

        FriendManager.getInstance().setScreenOpen(true);

        friendsListener = fresh -> this.minecraft.execute(() -> {
            this.friends.clear();
            this.friends.addAll(fresh);
            refreshStatusesForCurrentFriends();
            updateState();
        });
        requestsListener = fresh -> this.minecraft.execute(() -> {
            this.requests.clear();
            this.requests.addAll(fresh);
            updateState();
        });
        worldInvitesListener = fresh -> this.minecraft.execute(() -> {
            this.worldInvites.clear();
            this.worldInvites.addAll(fresh);
            updateState();
        });

        FriendManager.getInstance().addFriendsListener(friendsListener);
        FriendManager.getInstance().addPendingRequestsListener(requestsListener);
        FriendManager.getInstance().addWorldInvitesListener(worldInvitesListener);

        // Tabs
        this.tabFriendsButton = Button.builder(tr("tab.friends"), btn -> {
            activeTab = Tab.FRIENDS;
            scrollY = 0;
            updateState();
        }).bounds(leftPos + padding, topPos + 25, buttonWidth, 20).build();
        this.addRenderableWidget(this.tabFriendsButton);

        this.tabRequestsButton = Button.builder(tr("tab.requests", requests.size()), btn -> {
            activeTab = Tab.REQUESTS;
            scrollY = 0;
            updateState();
        }).bounds(leftPos + (padding * 2) + buttonWidth, topPos + 25, buttonWidth, 20).build();
        this.addRenderableWidget(this.tabRequestsButton);

        this.tabWorldInvitesButton = Button.builder(tr("tab.world_invites", worldInvites.size()), btn -> {
            activeTab = Tab.WORLD_INVITES;
            scrollY = 0;
            updateState();
        }).bounds(leftPos + (padding * 3) + (buttonWidth * 2), topPos + 25, buttonWidth, 20).build();
        this.addRenderableWidget(this.tabWorldInvitesButton);

        // Add Friend Area
        int bottomY = topPos + MODAL_HEIGHT - 30;
        this.addFriendField = new EditBox(this.font, leftPos + padding, bottomY, MODAL_WIDTH - 90, 20, tr("input.player_name"));
        this.addFriendField.setMaxLength(16);
        this.addFriendField.setHint(tr("input.player_name"));
        this.addRenderableWidget(this.addFriendField);

        this.addFriendButton = Button.builder(tr("button.add"), btn -> sendFriendRequest()).bounds(leftPos + MODAL_WIDTH - 75, bottomY, 65, 20).build();
        this.addRenderableWidget(this.addFriendButton);

        refreshData();
        updateState();
    }

    @Override
    public void removed() {
        super.removed();
        if (friendsListener != null) {
            FriendManager.getInstance().removeFriendsListener(friendsListener);
        }
        if (requestsListener != null) {
            FriendManager.getInstance().removePendingRequestsListener(requestsListener);
        }
        if (worldInvitesListener != null) {
            FriendManager.getInstance().removeWorldInvitesListener(worldInvitesListener);
        }
        FriendManager.getInstance().setScreenOpen(false);

    }

    private void refreshData() {
        FriendManager manager = FriendManager.getInstance();

        this.friends.clear();
        this.friends.addAll(manager.getCachedFriends());
        refreshStatusesForCurrentFriends();
        this.requests.clear();
        this.requests.addAll(manager.getCachedPendingRequests());
        this.worldInvites.clear();
        this.worldInvites.addAll(manager.getCachedWorldInvites());
        updateState();

        manager.getFriends().thenAccept(apiFriends -> this.minecraft.execute(() -> {
            this.friends.clear();
            this.friends.addAll(apiFriends);
            refreshStatusesForCurrentFriends();
            updateState();
        })).exceptionally(ex -> null);

        manager.getPendingRequests().thenAccept(apiRequests -> this.minecraft.execute(() -> {
            this.requests.clear();
            this.requests.addAll(apiRequests);
            updateState();
        })).exceptionally(ex -> null);

        manager.getWorldInvites().thenAccept(apiWorldInvites -> this.minecraft.execute(() -> {
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
            String name = this.addFriendField.getValue().trim();
            if (!name.isEmpty()) {
                this.addFriendButton.active = false;

                FriendManager.getInstance().sendRequestByName(name).thenAccept(success -> this.minecraft.execute(() -> {
                    this.addFriendButton.active = true;
                    if (success) {
                        setAddFriendStatus(tr("log.request_sent", name), 0xFF55FF55);
                        this.addFriendField.setValue("");
                    } else {
                        setAddFriendStatus(tr("error.request_failed", name), 0xFFFF5555);
                    }
                })).exceptionally(ex -> {
                    this.minecraft.execute(() -> {
                        this.addFriendButton.active = true;
                        setAddFriendStatus(tr("error.request_failed", name), 0xFFFF5555);
                    });
                    return null;
                });
            }
        }
    }

    private void setAddFriendStatus(Component message, int color) {
        this.addFriendStatusMessage = message;
        this.addFriendStatusColor = color;
        this.addFriendStatusExpiresAtMillis = System.currentTimeMillis() + ADD_FRIEND_STATUS_DURATION_MS;
    }

    // Rendering order: backdrop (modal frame), then the widgets, then the list on top.
    //? if >=26.1 {
    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        Gfx gfx = new Gfx(graphics);
        drawBackdrop(gfx);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawForeground(gfx, mouseX, mouseY);
    }
    //?} else if >=1.21.6 {
    /*@Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float delta) {
        Gfx gfx = new Gfx(graphics);
        drawBackdrop(gfx);
        super.render(graphics, mouseX, mouseY, delta);
        drawForeground(gfx, mouseX, mouseY);
    }
    *///?} else if >=1.20.2 {
    /*// Screen.render() draws the background itself before the widgets, so the backdrop replaces it.
    @Override
    public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float delta) {
        drawBackdrop(new Gfx(graphics));
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        drawForeground(new Gfx(graphics), mouseX, mouseY);
    }
    *///?} else {
    /*@Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float delta) {
        Gfx gfx = new Gfx(graphics);
        drawBackdrop(gfx);
        super.render(graphics, mouseX, mouseY, delta);
        drawForeground(gfx, mouseX, mouseY);
    }
    *///?}

    private void drawBackdrop(Gfx graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);

        // Modal Background
        graphics.fill(leftPos - 1, topPos - 1, leftPos + MODAL_WIDTH + 1, topPos + MODAL_HEIGHT + 1, 0xFF000000);
        graphics.fill(leftPos, topPos, leftPos + MODAL_WIDTH, topPos + MODAL_HEIGHT, 0xFF313233);
        graphics.fill(leftPos, topPos, leftPos + MODAL_WIDTH, topPos + 1, 0xFF555555);
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + MODAL_HEIGHT, 0xFF555555);

        // Title
        graphics.centeredText(this.font, tr("header").getString(), leftPos + (MODAL_WIDTH / 2), topPos + 8, 0xFFFFAA00);
    }

    private void drawForeground(Gfx graphics, int mouseX, int mouseY) {
        drawList(graphics, mouseX, mouseY);
        drawAddFriendStatus(graphics);
    }

    private void drawAddFriendStatus(Gfx graphics) {
        if (addFriendStatusMessage == null) {
            return;
        }
        if (System.currentTimeMillis() >= addFriendStatusExpiresAtMillis) {
            addFriendStatusMessage = null;
            return;
        }
        String text = truncateText(addFriendStatusMessage.getString(), MODAL_WIDTH - 20);
        graphics.centeredText(this.font, text, leftPos + (MODAL_WIDTH / 2), topPos + MODAL_HEIGHT - 39, addFriendStatusColor);
    }

    private void drawList(Gfx graphics, int mouseX, int mouseY) {
        int listX = leftPos + 10;
        int listY = topPos + 55;
        int listWidth = MODAL_WIDTH - 20;
        int listHeight = MODAL_HEIGHT - 95;

        graphics.fill(listX - 1, listY - 1, listX + listWidth + 1, listY + listHeight + 1, 0xFF000000);
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xFF1A1A1A);

        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

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
                    drawListItem(graphics, mouseX, mouseY, listX, renderY, listWidth, friend.name(), isOnline, true);
                    renderY += LIST_ITEM_HEIGHT;
                }
            }
            case REQUESTS -> {
                for (UserDto req : requests) {
                    drawListItem(graphics, mouseX, mouseY, listX, renderY, listWidth, req.name(), false, false);
                    renderY += LIST_ITEM_HEIGHT;
                }
            }
            case WORLD_INVITES -> {
                for (FriendManager.WorldInviteDto invite : worldInvites) {
                    drawWorldInviteItem(graphics, mouseX, mouseY, listX, renderY, listWidth, invite);
                    renderY += LIST_ITEM_HEIGHT;
                }
            }
        }

        graphics.disableScissor();
        drawScrollbar(graphics, listX, listY, listWidth, listHeight, totalItems);
    }

    private void drawScrollbar(Gfx graphics, int x, int y, int width, int height, int totalItems) {
        int maxScroll = Math.max(0, (totalItems * LIST_ITEM_HEIGHT) - height);
        if (maxScroll > 0) {
            int scrollbarX = x + width - 6;
            int thumbHeight = Math.max(20, (int) ((height / (float) (totalItems * LIST_ITEM_HEIGHT)) * height));
            int thumbY = y + (int) ((scrollY / (float) maxScroll) * (height - thumbHeight));

            graphics.fill(scrollbarX, y, scrollbarX + 5, y + height, 0x80000000);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 5, thumbY + thumbHeight, 0xFF888888);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight - 1, 0xFFCCCCCC);
        }
    }

    private String truncateText(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String truncated = text;
        while (this.font.width(truncated + "...") > maxWidth && !truncated.isEmpty()) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + "...";
    }

    private void drawListItem(Gfx graphics, int mouseX, int mouseY, int x, int y, int width, String name, boolean isOnline, boolean isFriend) {
        int listStartY = topPos + 55;
        int listEndY = topPos + MODAL_HEIGHT - 40;

        if (y + FriendsScreen.LIST_ITEM_HEIGHT < listStartY || y > listEndY) return;

        boolean hovered = mouseX >= x && mouseX <= x + width - 8 && mouseY >= Math.max(y, listStartY) && mouseY <= Math.min(y + FriendsScreen.LIST_ITEM_HEIGHT, listEndY);
        if (hovered) {
            graphics.fill(x, y, x + width - 8, y + FriendsScreen.LIST_ITEM_HEIGHT, 0x15FFFFFF);
        }

        // --- Avatar ---
        Supplier<SkinData> skinSupplier = getSkinAsyncPublic(name);
        Identifier skinTexture = skinSupplier.get().texture();

        graphics.fill(x + 4, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 13, x + 30, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) + 13, 0xFF000000);
        graphics.blit(skinTexture, x + 5, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 12, 8.0F, 8.0F, 24, 24, 8, 8, 64, 64);
        graphics.blit(skinTexture, x + 5, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 12, 40.0F, 8.0F, 24, 24, 8, 8, 64, 64);

        int btnWidth = 65;
        int btnMargin = 12;
        int reservedButtonSpace = (btnWidth * 2) + btnMargin + 10;
        int maxTextWidth = width - 38 - reservedButtonSpace;

        // --- Info ---
        String displayName = truncateText(name, maxTextWidth);
        graphics.text(this.font, displayName, x + 38, y + 8, 0xFFFFFFFF);

        if (isFriend) {
            int statusColor = isOnline ? 0xFF55FF55 : 0xFFFF5555;
            String statusText = isOnline ? tr("status.online").getString() : tr("status.offline").getString();

            graphics.fill(x + 38, y + 22, x + 44, y + 28, 0xFF000000);
            graphics.fill(x + 39, y + 23, x + 43, y + 27, statusColor);
            graphics.text(this.font, statusText, x + 48, y + 21, 0xFFAAAAAA);
        } else {
            String subText = truncateText(tr("request.incoming").getString(), maxTextWidth);
            graphics.text(this.font, subText, x + 38, y + 21, 0xFFAAAAAA);
        }

        // --- Buttons ---
        int btnHeight = 20;
        int btnY = y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 10;

        if (isFriend) {
            if (isOnline) {
                boolean isDisabled = isInviteOnCooldown(name) || !isInSingleplayerWorld();
                drawVanillaStyleButton(graphics, mouseX, mouseY, x + width - (btnWidth * 2) - btnMargin - 5, btnY, btnWidth, btnHeight, tr("button.invite"), true, isDisabled);
            }
            drawVanillaStyleButton(graphics, mouseX, mouseY, x + width - btnWidth - btnMargin, btnY, btnWidth, btnHeight, tr("button.remove"), false, false);
        } else {
            drawVanillaStyleButton(graphics, mouseX, mouseY, x + width - (btnWidth * 2) - btnMargin - 5, btnY, btnWidth, btnHeight, tr("button.accept"), true, false);
            drawVanillaStyleButton(graphics, mouseX, mouseY, x + width - btnWidth - btnMargin, btnY, btnWidth, btnHeight, tr("button.decline"), false, false);
        }

        graphics.horizontalLine(x + 2, x + width - 10, y + FriendsScreen.LIST_ITEM_HEIGHT - 1, 0x20FFFFFF);
    }

    private void drawWorldInviteItem(Gfx graphics, int mouseX, int mouseY, int x, int y, int width, FriendManager.WorldInviteDto invite) {
        int listStartY = topPos + 55;
        int listEndY = topPos + MODAL_HEIGHT - 40;

        if (y + FriendsScreen.LIST_ITEM_HEIGHT < listStartY || y > listEndY) return;

        boolean hovered = mouseX >= x && mouseX <= x + width - 8 && mouseY >= Math.max(y, listStartY) && mouseY <= Math.min(y + FriendsScreen.LIST_ITEM_HEIGHT, listEndY);
        if (hovered) {
            graphics.fill(x, y, x + width - 8, y + FriendsScreen.LIST_ITEM_HEIGHT, 0x15FFFFFF);
        }

        // --- Avatar (host skin) ---
        Supplier<SkinData> skinSupplier = getSkinAsyncPublic(invite.hostName());
        Identifier skinTexture = skinSupplier.get().texture();

        graphics.fill(x + 4, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 13, x + 30, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) + 13, 0xFF000000);
        graphics.blit(skinTexture, x + 5, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 12, 8.0F, 8.0F, 24, 24, 8, 8, 64, 64);
        graphics.blit(skinTexture, x + 5, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 12, 40.0F, 8.0F, 24, 24, 8, 8, 64, 64);

        // Only one action button now (Accept) since invites can simply be ignored
        int btnWidth = 65;
        int btnMargin = 12;
        int reservedButtonSpace = btnWidth + btnMargin + 10;
        int maxTextWidth = width - 38 - reservedButtonSpace;

        // --- Info ---
        String displayName = truncateText(invite.worldName(), maxTextWidth);
        graphics.text(this.font, displayName, x + 38, y + 8, 0xFFFFFFFF);

        String subText = truncateText(tr("world_invite.from", invite.hostName()).getString(), maxTextWidth);
        graphics.text(this.font, subText, x + 38, y + 21, 0xFFAAAAAA);

        // --- Button ---
        int btnHeight = 20;
        int btnY = y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 10;
        drawVanillaStyleButton(graphics, mouseX, mouseY, x + width - btnWidth - btnMargin, btnY, btnWidth, btnHeight, tr("button.join"), true, false);

        graphics.horizontalLine(x + 2, x + width - 10, y + FriendsScreen.LIST_ITEM_HEIGHT - 1, 0x20FFFFFF);
    }

    private void drawVanillaStyleButton(Gfx graphics, int mouseX, int mouseY, int x, int y, int w, int h, Component text, boolean positiveAction, boolean disabled) {
        int listStartY = topPos + 55;
        int listEndY = topPos + MODAL_HEIGHT - 40;
        boolean withinListBounds = mouseY >= listStartY && mouseY <= listEndY;
        boolean hovered = !disabled && withinListBounds && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        int baseColor = positiveAction ? 0xFF2B7C2B : 0xFF9E2B2B;
        int hoverColor = positiveAction ? 0xFF3CA83C : 0xFFC63C3C;
        int disabledColor = 0xFF555555;
        
        int bgColor = disabled ? disabledColor : (hovered ? hoverColor : baseColor);

        graphics.fill(x, y, x + w, y + h, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40FFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + h - 1, 0x40FFFFFF);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x40000000);
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0x40000000);

        int textColor = disabled ? 0xFF888888 : (hovered ? 0xFFFFFFAA : 0xFFFFFFFF);
        graphics.centeredText(this.font, text.getString(), x + (w / 2), y + (h - 8) / 2 + 1, textColor);
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
        FriendManager.getInstance().getIngameOnlineStatus(username).thenAccept(status -> this.minecraft.execute(() -> onlineStatusByName.put(username, status.is_ingame_online()))).exceptionally(ex -> null).whenComplete((result, error) -> inFlightStatusFetches.remove(username));
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(TRANSLATION_PREFIX + key, args);
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        return handleListClick(event.x(), event.y()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        return handleKey(event.key()) || super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        return handleListClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return handleKey(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }
    *///?}

    //? if >=1.20.2 {
    @Override
    public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
        return handleScroll(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(final double x, final double y, final double scrollY) {
        return handleScroll(x, y, scrollY) || super.mouseScrolled(x, y, scrollY);
    }
    *///?}

    private boolean handleListClick(double mx, double my) {
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
                        FriendManager.getInstance().removeFriend(friend.id()).thenAccept(success -> {
                            if (success) {
                                this.minecraft.execute(this::refreshData); // Liste neu laden
                            }
                        });
                        return true;
                    }
                } else if (activeTab == Tab.REQUESTS && clickedIndex < requests.size()) {
                    UserDto req = requests.get(clickedIndex);
                    // Button 1: Accept
                    if (clickedBtn1) {
                        FriendManager.getInstance().acceptRequest(req.id()).thenAccept(success -> {
                            if (success) {
                                this.minecraft.execute(this::refreshData); // Listen updaten
                            }
                        });
                        return true;
                    }
                    // Button 2: Decline
                    if (clickedBtn2) {
                        FriendManager.getInstance().declineRequest(req.id()).thenAccept(success -> {
                            if (success) {
                                this.minecraft.execute(this::refreshData); // update list
                            }
                        });
                        return true;
                    }
                } else if (activeTab == Tab.WORLD_INVITES && clickedIndex < worldInvites.size()) {
                    FriendManager.WorldInviteDto invite = worldInvites.get(clickedIndex);
                    if (clickedBtn2) {
                        ClientCompat.setScreen(null);
                        ClientCompat.disconnectLevel(this.minecraft);
                        if (MCTunnelClient.getInstance().getTunnelClient().isConnected()) {
                            MCTunnelClient.getInstance().getTunnelClient().disconnect();
                        }

                        ServerAddress address = ServerAddress.parseString(invite.tunnel_ip());
                        //? if >=1.20.2 {
                        ServerData serverData = new ServerData(
                                invite.worldName(),
                                invite.tunnel_ip(),
                                ServerData.Type.OTHER
                        );
                        //?} else {
                        /*ServerData serverData = new ServerData(invite.worldName(), invite.tunnel_ip(), false);
                        *///?}

                        ConnectScreen.startConnecting(
                                new TitleScreen(),
                                this.minecraft,
                                address,
                                serverData,
                                //? if >=1.20.5 {
                                false,
                                null
                                //?} else {
                                /*false
                                *///?}
                        );

                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void handleInviteClick(String friendName) {
        if (this.minecraft.getSingleplayerServer() == null) {
            return;
        }

        inviteButtonCooldowns.put(friendName, System.currentTimeMillis());
        playInviteSound();

        String worldName = this.minecraft.getSingleplayerServer().getWorldData().getLevelName();

        if (!this.minecraft.getSingleplayerServer().isPublished()) {
            ClientCompat.publishLan(this.minecraft.getSingleplayerServer());
        }


        if (MCTunnelClient.getInstance().getTunnelClient().isConnected()) {
            executeApiInvite(friendName, worldName, MCTunnelClient.getInstance().getTunnelClient().getHostname());
        } else {
            TunnelManager.host(this.minecraft, MCTunnelClient.getInstance().getTunnelClient(), hostname -> executeApiInvite(friendName, worldName, hostname));
        }
    }

    private void playInviteSound() {
        if (this.minecraft.player != null) {
            this.minecraft.player.playSound(
                    SoundEvents.UI_BUTTON_CLICK.value(),
                    0.5f,
                    1.2f
            );
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
        return this.minecraft.getSingleplayerServer() != null;
    }

    private void executeApiInvite(String friendName, String worldName, String tunnelIp) {
        FriendManager.getInstance().sendWorldInvite(friendName, worldName, tunnelIp).thenAccept(success -> {
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


    private boolean handleScroll(final double x, final double y, final double scrollY) {
        int listX = leftPos + 10;
        int listY = topPos + 55;
        int listWidth = MODAL_WIDTH - 20;
        int listHeight = MODAL_HEIGHT - 95;

        if (x >= listX && x <= listX + listWidth && y >= listY && y <= listY + listHeight) {
            int totalContentHeight = switch (activeTab) {
                case FRIENDS -> friends.size();
                case REQUESTS -> requests.size();
                case WORLD_INVITES -> worldInvites.size();
            } * LIST_ITEM_HEIGHT;
            int maxScroll = Math.max(0, totalContentHeight - listHeight);

            if (maxScroll > 0) {
                this.scrollY -= (int) (scrollY * 20);
                this.scrollY = Mth.clamp(this.scrollY, 0, maxScroll);
            }
            return true;
        }
        return false;
    }

    private boolean handleKey(int key) {
        if (key == InputConstants.KEY_RETURN) {
            if (this.addFriendField != null && this.addFriendField.isFocused()) {
                sendFriendRequest();
                return true;
            }
        }
        if (key == InputConstants.KEY_ESCAPE) {
            ClientCompat.setScreen(null);
            return true;
        }
        return false;
    }
}