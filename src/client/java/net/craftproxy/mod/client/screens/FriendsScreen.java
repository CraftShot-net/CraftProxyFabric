package net.craftproxy.mod.client.screens;

import net.craftproxy.mod.client.managers.FriendManager;
import net.craftproxy.mod.client.managers.FriendManager.UserDto;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.NonNull;
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
    private enum Tab {FRIENDS, REQUESTS}

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

    private final List<UserDto> friends = new CopyOnWriteArrayList<>();
    private final List<UserDto> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Boolean> onlineStatusByName = new ConcurrentHashMap<>();
    private final Map<String, Long> statusFetchAtMillis = new ConcurrentHashMap<>();
    private final Set<String> inFlightStatusFetches = ConcurrentHashMap.newKeySet();
    private static final long STATUS_REFRESH_INTERVAL_MS = 5000L;

    private FriendsScreen() {
        super(tr("title"));
    }

    private Consumer<List<UserDto>> friendsListener;
    private Consumer<List<UserDto>> requestsListener;

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - MODAL_WIDTH) / 2;
        this.topPos = (this.height - MODAL_HEIGHT) / 2;

        int padding = 10;
        int buttonWidth = (MODAL_WIDTH - (padding * 3)) / 2;

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

        FriendManager.getInstance().addFriendsListener(friendsListener);
        FriendManager.getInstance().addPendingRequestsListener(requestsListener);

        // Tabs
        this.tabFriendsButton = Button.builder(tr("tab.friends"), _ -> {
            activeTab = Tab.FRIENDS;
            scrollY = 0;
            updateState();
        }).bounds(leftPos + padding, topPos + 25, buttonWidth, 20).build();
        this.addRenderableWidget(this.tabFriendsButton);

        this.tabRequestsButton = Button.builder(tr("tab.requests", requests.size()), _ -> {
            activeTab = Tab.REQUESTS;
            scrollY = 0;
            updateState();
        }).bounds(leftPos + (padding * 2) + buttonWidth, topPos + 25, buttonWidth, 20).build();
        this.addRenderableWidget(this.tabRequestsButton);

        // Add Friend Area
        int bottomY = topPos + MODAL_HEIGHT - 30;
        this.addFriendField = new EditBox(this.font, leftPos + padding, bottomY, MODAL_WIDTH - 90, 20, tr("input.player_name"));
        this.addFriendField.setMaxLength(16);
        this.addRenderableWidget(this.addFriendField);

        this.addFriendButton = Button.builder(tr("button.add"), _ -> sendFriendRequest()).bounds(leftPos + MODAL_WIDTH - 75, bottomY, 65, 20).build();
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
        FriendManager.getInstance().setScreenOpen(false);

    }

    private void refreshData() {
        FriendManager manager = FriendManager.getInstance();

        this.friends.clear();
        this.friends.addAll(manager.getCachedFriends());
        refreshStatusesForCurrentFriends();
        this.requests.clear();
        this.requests.addAll(manager.getCachedPendingRequests());
        updateState();

        manager.getFriends().thenAccept(apiFriends -> this.minecraft.execute(() -> {
            this.friends.clear();
            this.friends.addAll(apiFriends);
            refreshStatusesForCurrentFriends();
            updateState();
        })).exceptionally(_ -> null);

        manager.getPendingRequests().thenAccept(apiRequests -> this.minecraft.execute(() -> {
            this.requests.clear();
            this.requests.addAll(apiRequests);
            updateState();
        })).exceptionally(_ -> null);
    }

    private void updateState() {
        if (this.tabFriendsButton != null && this.tabRequestsButton != null) {
            this.tabFriendsButton.active = (activeTab != Tab.FRIENDS);
            this.tabRequestsButton.active = (activeTab != Tab.REQUESTS);
            this.tabRequestsButton.setMessage(tr("tab.requests", requests.size()));
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
                        System.out.println(tr("log.request_sent", name).getString());
                        this.addFriendField.setValue("");
                    } else {
                        System.out.println("API Error: Failed to send friend request to " + name);
                    }
                })).exceptionally(_ -> {
                    this.minecraft.execute(() -> this.addFriendButton.active = true);
                    return null;
                });
            }
        }
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);

        // Modal Background
        graphics.fill(leftPos - 1, topPos - 1, leftPos + MODAL_WIDTH + 1, topPos + MODAL_HEIGHT + 1, 0xFF000000);
        graphics.fill(leftPos, topPos, leftPos + MODAL_WIDTH, topPos + MODAL_HEIGHT, 0xFF313233);
        graphics.fill(leftPos, topPos, leftPos + MODAL_WIDTH, topPos + 1, 0xFF555555);
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + MODAL_HEIGHT, 0xFF555555);

        // Title
        graphics.centeredText(this.font, tr("header").getString(), leftPos + (MODAL_WIDTH / 2), topPos + 8, 0xFFFFAA00);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawList(graphics, mouseX, mouseY);
    }

    private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int listX = leftPos + 10;
        int listY = topPos + 55;
        int listWidth = MODAL_WIDTH - 20;
        int listHeight = MODAL_HEIGHT - 95;

        graphics.fill(listX - 1, listY - 1, listX + listWidth + 1, listY + listHeight + 1, 0xFF000000);
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xFF1A1A1A);

        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int renderY = listY - scrollY;
        int totalItems = activeTab == Tab.FRIENDS ? friends.size() : requests.size();

        if (activeTab == Tab.FRIENDS) {
            for (UserDto friend : friends) {
                refreshOnlineStatus(friend.name(), false);
                boolean isOnline = onlineStatusByName.getOrDefault(friend.name(), false);
                drawListItem(graphics, mouseX, mouseY, listX, renderY, listWidth, friend.name(), isOnline, true);
                renderY += LIST_ITEM_HEIGHT;
            }
        } else {
            for (UserDto req : requests) {
                drawListItem(graphics, mouseX, mouseY, listX, renderY, listWidth, req.name(), false, false);
                renderY += LIST_ITEM_HEIGHT;
            }
        }

        graphics.disableScissor();
        drawScrollbar(graphics, listX, listY, listWidth, listHeight, totalItems);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int totalItems) {
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

    private void drawListItem(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int width, String name, boolean isOnline, boolean isFriend) {
        int listStartY = topPos + 55;
        int listEndY = topPos + MODAL_HEIGHT - 40;

        if (y + FriendsScreen.LIST_ITEM_HEIGHT < listStartY || y > listEndY) return;

        boolean hovered = mouseX >= x && mouseX <= x + width - 8 && mouseY >= Math.max(y, listStartY) && mouseY <= Math.min(y + FriendsScreen.LIST_ITEM_HEIGHT, listEndY);
        if (hovered) {
            graphics.fill(x, y, x + width - 8, y + FriendsScreen.LIST_ITEM_HEIGHT, 0x15FFFFFF);
        }

        // --- Avatar ---
        Supplier<PlayerSkin> skinSupplier = getSkinAsyncPublic(name);
        Identifier skinTexture = getFixedSkinTexture(skinSupplier);

        graphics.fill(x + 4, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 13, x + 30, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) + 13, 0xFF000000);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture, x + 5, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 12, 8.0F, 8.0F, 24, 24, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture, x + 5, y + (FriendsScreen.LIST_ITEM_HEIGHT / 2) - 12, 40.0F, 8.0F, 24, 24, 8, 8, 64, 64);

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
                drawVanillaStyleButton(graphics, mouseX, mouseY, x + width - (btnWidth * 2) - btnMargin - 5, btnY, btnWidth, btnHeight, tr("button.invite"), true);
            }
            drawVanillaStyleButton(graphics, mouseX, mouseY, x + width - btnWidth - btnMargin, btnY, btnWidth, btnHeight, tr("button.remove"), false);
        } else {
            drawVanillaStyleButton(graphics, mouseX, mouseY, x + width - (btnWidth * 2) - btnMargin - 5, btnY, btnWidth, btnHeight, tr("button.accept"), true);
            drawVanillaStyleButton(graphics, mouseX, mouseY, x + width - btnWidth - btnMargin, btnY, btnWidth, btnHeight, tr("button.decline"), false);
        }

        graphics.horizontalLine(x + 2, x + width - 10, y + FriendsScreen.LIST_ITEM_HEIGHT - 1, 0x20FFFFFF);
    }

    private void drawVanillaStyleButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, Component text, boolean positiveAction) {
        int listStartY = topPos + 55;
        int listEndY = topPos + MODAL_HEIGHT - 40;
        boolean withinListBounds = mouseY >= listStartY && mouseY <= listEndY;
        boolean hovered = withinListBounds && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        int baseColor = positiveAction ? 0xFF2B7C2B : 0xFF9E2B2B;
        int hoverColor = positiveAction ? 0xFF3CA83C : 0xFFC63C3C;
        int bgColor = hovered ? hoverColor : baseColor;

        graphics.fill(x, y, x + w, y + h, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40FFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + h - 1, 0x40FFFFFF);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x40000000);
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0x40000000);

        int textColor = hovered ? 0xFFFFFFAA : 0xFFFFFFFF;
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
        FriendManager.getInstance().getIngameOnlineStatus(username)
                .thenAccept(status -> this.minecraft.execute(() -> onlineStatusByName.put(username, status.is_ingame_online())))
                .exceptionally(_ -> null)
                .whenComplete((_, _) -> inFlightStatusFetches.remove(username));
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(TRANSLATION_PREFIX + key, args);
    }

    private Identifier getFixedSkinTexture(Supplier<PlayerSkin> skinSupplier) {
        Identifier skinId = skinSupplier.get().body().id();
        String path = skinId.getPath();
        if (!path.startsWith("textures/") && !path.startsWith("skins/")) {
            return Identifier.withDefaultNamespace("textures/" + path + ".png");
        }
        return skinId;
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

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
                    if (clickedBtn1 && isOnline) {
                        System.out.println(tr("log.host_invite_sent", friend.name()).getString());
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
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
        int listX = leftPos + 10;
        int listY = topPos + 55;
        int listWidth = MODAL_WIDTH - 20;
        int listHeight = MODAL_HEIGHT - 95;

        if (x >= listX && x <= listX + listWidth && y >= listY && y <= listY + listHeight) {
            int totalContentHeight = (activeTab == Tab.FRIENDS ? friends.size() : requests.size()) * LIST_ITEM_HEIGHT;
            int maxScroll = Math.max(0, totalContentHeight - listHeight);

            if (maxScroll > 0) {
                this.scrollY -= (int) (scrollY * 20);
                this.scrollY = Math.clamp(this.scrollY, 0, maxScroll);
            }
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.addFriendField != null && this.addFriendField.isFocused()) {
                sendFriendRequest();
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.gui.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }
}