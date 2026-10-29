package net.craftproxy.mod.client.screens;

import com.mojang.blaze3d.platform.InputConstants;
import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.compat.Gfx;
import net.craftproxy.mod.client.compat.SkinData;
import net.craftproxy.mod.client.dto.MyCosmeticsResponseDto.PlayerCosmeticEntryDto;
import net.craftproxy.mod.client.gui.WardrobePreviewWidget;
import net.craftproxy.mod.client.managers.CapeManager;
import net.craftproxy.mod.client.managers.WardrobeManager;
import net.craftproxy.mod.client.utils.SkinManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class WardrobeScreen extends Screen {
    private static final String TRANSLATION_PREFIX = "screen.craftproxy.wardrobe.";

    private static WardrobeScreen INSTANCE;

    public static WardrobeScreen getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new WardrobeScreen();
        }
        return INSTANCE;
    }

    // Modal layout
    private static final int MODAL_WIDTH = 410;
    private static final int MODAL_HEIGHT = 245;
    private static final int PREVIEW_PANEL_WIDTH = 135;
    private static final int LIST_ITEM_HEIGHT = 48;

    private int leftPos;
    private int topPos;
    private int scrollY = 0;

    // Filter tabs
    private enum Tab { ALL, CAPES }
    private Tab activeTab = Tab.ALL;
    private Button tabAllButton;
    private Button tabCapesButton;
    private Button closeButton;

    // 3D Player Preview Widget
    private WardrobePreviewWidget previewWidget;
    private Supplier<SkinData> cachedSkinSupplier;

    private final List<PlayerCosmeticEntryDto> cosmetics = new CopyOnWriteArrayList<>();
    private Consumer<List<PlayerCosmeticEntryDto>> cosmeticsListener;

    private boolean isActionPending = false;
    private Component statusMessage;
    private int statusColor;
    private long statusExpiresAtMillis;
    private static final long STATUS_DURATION_MS = 3000L;

    private WardrobeScreen() {
        super(tr("title"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - MODAL_WIDTH) / 2;
        this.topPos = (this.height - MODAL_HEIGHT) / 2;

        // Close 'X' Button in header
        this.closeButton = Button.builder(Component.literal("✕"), btn -> ClientCompat.setScreen(null))
                .bounds(leftPos + MODAL_WIDTH - 20, topPos + 4, 16, 16)
                .build();
        this.addRenderableWidget(this.closeButton);

        int listX = leftPos + PREVIEW_PANEL_WIDTH + 14;
        int tabY = topPos + 26;
        int tabWidth = 55;

        this.tabAllButton = Button.builder(tr("tab.all"), btn -> {
            this.activeTab = Tab.ALL;
            this.scrollY = 0;
            updateTabButtons();
        }).bounds(listX, tabY, tabWidth, 18).build();
        this.addRenderableWidget(this.tabAllButton);

        this.tabCapesButton = Button.builder(tr("tab.capes"), btn -> {
            this.activeTab = Tab.CAPES;
            this.scrollY = 0;
            updateTabButtons();
        }).bounds(listX + tabWidth + 4, tabY, tabWidth, 18).build();
        this.addRenderableWidget(this.tabCapesButton);
        updateTabButtons();

        // 3D Player Preview Widget (Interactive drag rotation + player skin + cape)
        if (this.previewWidget != null) {
            this.removeWidget(this.previewWidget);
        }
        this.cachedSkinSupplier = SkinManager.getSkinAsyncPublic(this.minecraft.getUser().getName());
        this.previewWidget = new WardrobePreviewWidget(
                PREVIEW_PANEL_WIDTH - 16,
                MODAL_HEIGHT - 56,
                this.minecraft.getEntityModels(),
                this::resolveCurrentPlayerSkin,
                CapeManager::getLocalPlayerEquippedCape
        );
        this.previewWidget.setX(leftPos + 16);
        this.previewWidget.setY(topPos + 34);
        this.addRenderableWidget(this.previewWidget);

        cosmeticsListener = fresh -> this.minecraft.execute(() -> {
            this.cosmetics.clear();
            this.cosmetics.addAll(fresh);
            // Preload cape textures into Minecraft TextureManager
            for (PlayerCosmeticEntryDto item : fresh) {
                if (item.textureUrl() != null) {
                    CapeManager.preloadTexture(item.cosmeticId(), item.textureUrl());
                }
            }
        });

        if (WardrobeManager.isInitialized()) {
            WardrobeManager.getInstance().addCosmeticsListener(cosmeticsListener);
            refreshData();
        }
    }

    private void updateTabButtons() {
        if (this.tabAllButton != null) {
            this.tabAllButton.active = (this.activeTab != Tab.ALL);
        }
        if (this.tabCapesButton != null) {
            this.tabCapesButton.active = (this.activeTab != Tab.CAPES);
        }
    }

    private SkinData resolveCurrentPlayerSkin() {
        if (this.minecraft.player != null) {
            return SkinManager.ofLocalPlayer();
        }
        if (this.cachedSkinSupplier != null) {
            SkinData skin = this.cachedSkinSupplier.get();
            if (skin != null) return skin;
        }
        return SkinManager.defaultFor(this.minecraft.getUser().getProfileId());
    }

    @Override
    public void removed() {
        super.removed();
        if (cosmeticsListener != null && WardrobeManager.isInitialized()) {
            WardrobeManager.getInstance().removeCosmeticsListener(cosmeticsListener);
        }
    }

    private void refreshData() {
        if (!WardrobeManager.isInitialized()) return;
        this.cosmetics.clear();
        this.cosmetics.addAll(WardrobeManager.getInstance().getCachedCosmetics());

        WardrobeManager.getInstance().fetchMyCosmetics()
                .thenAccept(fresh -> this.minecraft.execute(() -> {
                    this.cosmetics.clear();
                    this.cosmetics.addAll(fresh);
                    for (PlayerCosmeticEntryDto item : fresh) {
                        if (item.textureUrl() != null) {
                            CapeManager.preloadTexture(item.cosmeticId(), item.textureUrl());
                        }
                    }
                }))
                .exceptionally(ex -> {
                    this.minecraft.execute(() -> setStatus(tr("error.fetch_failed"), 0xFFFF5555));
                    return null;
                });
    }

    private void setStatus(Component msg, int color) {
        this.statusMessage = msg;
        this.statusColor = color;
        this.statusExpiresAtMillis = System.currentTimeMillis() + STATUS_DURATION_MS;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(TRANSLATION_PREFIX + key, args);
    }

    private List<PlayerCosmeticEntryDto> getFilteredCosmetics() {
        if (activeTab == Tab.CAPES) {
            List<PlayerCosmeticEntryDto> capes = new ArrayList<>();
            for (PlayerCosmeticEntryDto c : cosmetics) {
                if ("cape".equalsIgnoreCase(c.type())) {
                    capes.add(c);
                }
            }
            return capes;
        }
        return cosmetics;
    }

    // Rendering order: backdrop (modal frame), then the widgets (tabs and preview), then the list on top.
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
        // Dim screen background
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);

        // Modal Frame & Background (matching FriendsScreen aesthetic)
        graphics.fill(leftPos - 1, topPos - 1, leftPos + MODAL_WIDTH + 1, topPos + MODAL_HEIGHT + 1, 0xFF000000);
        graphics.fill(leftPos, topPos, leftPos + MODAL_WIDTH, topPos + MODAL_HEIGHT, 0xFF2A2B2D);
        graphics.fill(leftPos, topPos, leftPos + MODAL_WIDTH, topPos + 1, 0xFF4A4B4E);
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + MODAL_HEIGHT, 0xFF4A4B4E);
        graphics.fill(leftPos, topPos + MODAL_HEIGHT - 1, leftPos + MODAL_WIDTH, topPos + MODAL_HEIGHT, 0xFF1C1D1E);
        graphics.fill(leftPos + MODAL_WIDTH - 1, topPos, leftPos + MODAL_WIDTH, topPos + MODAL_HEIGHT, 0xFF1C1D1E);

        // Header Title
        graphics.centeredText(this.font, tr("header").getString(), leftPos + (MODAL_WIDTH / 2), topPos + 8, 0xFFFFAA00);

        // Draw Player Preview container backdrop & border
        drawPreviewContainer(graphics);
    }

    private void drawForeground(Gfx graphics, int mouseX, int mouseY) {
        // Draw Cosmetics List container & items
        drawCosmeticsList(graphics, mouseX, mouseY);

        // Status Toast Message
        drawStatusMessage(graphics);
    }

    private void drawPreviewContainer(Gfx graphics) {
        int previewX = leftPos + 10;
        int previewY = topPos + 26;
        int previewW = PREVIEW_PANEL_WIDTH;
        int previewH = MODAL_HEIGHT - 36;

        // Container card frame
        graphics.fill(previewX - 1, previewY - 1, previewX + previewW + 1, previewY + previewH + 1, 0xFF181819);
        graphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF1E1F21);
        graphics.fill(previewX, previewY, previewX + previewW, previewY + 1, 0xFF353639);
        graphics.fill(previewX, previewY, previewX + 1, previewY + previewH, 0xFF353639);

        // Rotation Hint Banner at bottom of preview container
        graphics.fill(previewX + 4, previewY + previewH - 18, previewX + previewW - 4, previewY + previewH - 4, 0xFF151618);
        graphics.centeredText(this.font, tr("preview.hint").getString(), previewX + (previewW / 2), previewY + previewH - 15, 0xFF8E9094);
    }

    private void drawCosmeticsList(Gfx graphics, int mouseX, int mouseY) {
        int listX = leftPos + PREVIEW_PANEL_WIDTH + 14;
        int listY = topPos + 48;
        int listWidth = MODAL_WIDTH - PREVIEW_PANEL_WIDTH - 24;
        int listHeight = MODAL_HEIGHT - 58;

        // Container frame
        graphics.fill(listX - 1, listY - 1, listX + listWidth + 1, listY + listHeight + 1, 0xFF181819);
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xFF1E1F21);
        graphics.fill(listX, listY, listX + listWidth, listY + 1, 0xFF353639);
        graphics.fill(listX, listY, listX + 1, listY + listHeight, 0xFF353639);

        List<PlayerCosmeticEntryDto> items = getFilteredCosmetics();

        if (items.isEmpty()) {
            graphics.centeredText(this.font, tr("empty").getString(), listX + (listWidth / 2), listY + (listHeight / 2) - 4, 0xFF888888);
            return;
        }

        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int renderY = listY - scrollY;
        for (PlayerCosmeticEntryDto cosmetic : items) {
            drawCosmeticCard(graphics, mouseX, mouseY, listX, renderY, listWidth, cosmetic);
            renderY += LIST_ITEM_HEIGHT;
        }

        graphics.disableScissor();
        drawScrollbar(graphics, listX, listY, listWidth, listHeight, items.size());
    }

    private void drawCosmeticCard(Gfx graphics, int mouseX, int mouseY, int x, int y, int width, PlayerCosmeticEntryDto cosmetic) {
        int listStartY = topPos + 48;
        int listEndY = topPos + MODAL_HEIGHT - 10;

        if (y + LIST_ITEM_HEIGHT < listStartY || y > listEndY) return;

        int cardPad = 4;
        int cardX = x + cardPad;
        int cardY = y + 2;
        int cardW = width - (cardPad * 2) - 6;
        int cardH = LIST_ITEM_HEIGHT - 4;

        boolean hovered = mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= Math.max(cardY, listStartY) && mouseY <= Math.min(cardY + cardH, listEndY);

        // Card Background: dark sleek container with highlighted border if equipped or hovered
        boolean isEquipped = cosmetic.equipped();
        int cardBg = isEquipped ? 0xFF272C28 : (hovered ? 0xFF2B2C30 : 0xFF242528);
        int borderCol = isEquipped ? 0xFF3E7548 : (hovered ? 0xFF4C4D52 : 0xFF323337);

        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, cardBg);
        graphics.fill(cardX, cardY, cardX + cardW, cardY + 1, borderCol);
        graphics.fill(cardX, cardY, cardX + 1, cardY + cardH, borderCol);
        graphics.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, borderCol);
        graphics.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, borderCol);

        // Thumbnail Icon Container (Crisp miniature cape preview)
        int iconBoxX = cardX + 5;
        int iconBoxY = cardY + 5;
        int iconBoxW = 24;
        int iconBoxH = 30;

        graphics.fill(iconBoxX, iconBoxY, iconBoxX + iconBoxW, iconBoxY + iconBoxH, 0xFF141416);
        graphics.fill(iconBoxX, iconBoxY, iconBoxX + iconBoxW, iconBoxY + 1, 0xFF3C3D42);
        graphics.fill(iconBoxX, iconBoxY, iconBoxX + 1, iconBoxY + iconBoxH, 0xFF3C3D42);
        graphics.fill(iconBoxX, iconBoxY + iconBoxH - 1, iconBoxX + iconBoxW, iconBoxY + iconBoxH, 0xFF222326);
        graphics.fill(iconBoxX + iconBoxW - 1, iconBoxY, iconBoxX + iconBoxW, iconBoxY + iconBoxH, 0xFF222326);

        Identifier capeTexture = CapeManager.getLoadedTexture(cosmetic.cosmeticId());
        if (capeTexture != null) {
            // Emblem design is at u: 1..10 (width 10), v: 1..16 (height 16)
            int capePadX = 3;
            int capePadY = 3;
            int capeW = iconBoxW - (capePadX * 2);
            int capeH = iconBoxH - (capePadY * 2);
            graphics.blit(
                    capeTexture,
                    iconBoxX + capePadX,
                    iconBoxY + capePadY,
                    1.0F,
                    1.0F,
                    capeW,
                    capeH,
                    10,
                    16,
                    64,
                    32
            );
        } else {
            // Placeholder emblem
            graphics.fill(iconBoxX + 5, iconBoxY + 5, iconBoxX + iconBoxW - 5, iconBoxY + iconBoxH - 5, 0xFF2A2B2F);
        }

        // Action Button (Equip / Unequip)
        int btnWidth = 56;
        int btnHeight = 20;
        int btnX = cardX + cardW - btnWidth - 8;
        int btnY = cardY + (cardH - btnHeight) / 2;

        Component buttonText = isEquipped ? tr("button.unequip") : tr("button.equip");
        boolean isPositive = !isEquipped;
        drawVanillaStyleButton(graphics, mouseX, mouseY, btnX, btnY, btnWidth, btnHeight, buttonText, isPositive, isActionPending);

        // Text & Badges Info
        int textX = iconBoxX + iconBoxW + 8;
        int maxTextWidth = btnX - textX - 8;

        String displayName = truncateText(formatCosmeticName(cosmetic.cosmeticId()), maxTextWidth);
        graphics.text(this.font, displayName, textX, cardY + 7, 0xFFEDEDED);

        int subTextY = cardY + 22;
        if (isEquipped) {
            // Subtle green badge [EQUIPPED]
            String badge = "[" + tr("badge.equipped").getString() + "]";
            graphics.text(this.font, badge, textX, subTextY, 0xFF55FF55);
        } else {
            String typeLabel = tr("type." + cosmetic.type()).getString();
            graphics.text(this.font, typeLabel, textX, subTextY, 0xFF8E9094);
        }
    }

    private String formatCosmeticName(String cosmeticId) {
        String trKey = TRANSLATION_PREFIX + "item." + cosmeticId;
        String localized = Component.translatable(trKey).getString();
        if (!localized.equals(trKey)) {
            return localized;
        }
        // Fallback title formatting: "first_100_users_cape" -> "First 100 Users Cape"
        String[] words = cosmeticId.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void drawVanillaStyleButton(Gfx graphics, int mouseX, int mouseY, int x, int y, int w, int h, Component text, boolean positiveAction, boolean disabled) {
        int listStartY = topPos + 48;
        int listEndY = topPos + MODAL_HEIGHT - 10;
        boolean withinListBounds = mouseY >= listStartY && mouseY <= listEndY;
        boolean hovered = !disabled && withinListBounds && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        int baseColor = positiveAction ? 0xFF2B7C2B : 0xFF8E2B2B;
        int hoverColor = positiveAction ? 0xFF3CA83C : 0xFFB33636;
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

    private void drawScrollbar(Gfx graphics, int x, int y, int width, int height, int totalItems) {
        int maxScroll = Math.max(0, (totalItems * LIST_ITEM_HEIGHT) - height);
        if (maxScroll > 0) {
            int scrollbarX = x + width - 5;
            int thumbHeight = Math.max(20, (int) ((height / (float) (totalItems * LIST_ITEM_HEIGHT)) * height));
            int thumbY = y + (int) ((scrollY / (float) maxScroll) * (height - thumbHeight));

            graphics.fill(scrollbarX, y, scrollbarX + 4, y + height, 0x60000000);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFF6E7075);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbHeight - 1, 0xFF9EA0A6);
        }
    }

    private void drawStatusMessage(Gfx graphics) {
        if (statusMessage == null) return;
        if (System.currentTimeMillis() >= statusExpiresAtMillis) {
            statusMessage = null;
            return;
        }
        graphics.centeredText(this.font, statusMessage.getString(), leftPos + (MODAL_WIDTH / 2), topPos + MODAL_HEIGHT - 10, statusColor);
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
        int listX = leftPos + PREVIEW_PANEL_WIDTH + 14;
        int listY = topPos + 48;
        int listWidth = MODAL_WIDTH - PREVIEW_PANEL_WIDTH - 24;
        int listHeight = MODAL_HEIGHT - 58;

        if (mx >= listX && mx <= listX + listWidth && my >= listY && my <= listY + listHeight) {
            int clickedIndex = (int) ((my - listY + scrollY) / LIST_ITEM_HEIGHT);
            int itemY = listY - scrollY + (clickedIndex * LIST_ITEM_HEIGHT);

            List<PlayerCosmeticEntryDto> items = getFilteredCosmetics();
            if (clickedIndex >= 0 && clickedIndex < items.size()) {
                int cardPad = 4;
                int cardX = listX + cardPad;
                int cardW = listWidth - (cardPad * 2) - 6;
                int btnWidth = 56;
                int btnHeight = 20;
                int btnX = cardX + cardW - btnWidth - 8;
                int cardH = LIST_ITEM_HEIGHT - 4;
                int cardY = itemY + 2;
                int btnY = cardY + (cardH - btnHeight) / 2;

                boolean clickedBtn = mx >= btnX && mx <= btnX + btnWidth && my >= btnY && my <= btnY + btnHeight;
                if (clickedBtn && !isActionPending) {
                    PlayerCosmeticEntryDto entry = items.get(clickedIndex);
                    isActionPending = true;
                    WardrobeManager.getInstance().toggleEquip(entry.cosmeticId())
                            .thenAccept(res -> this.minecraft.execute(() -> {
                                isActionPending = false;
                                setStatus(res.equipped() ? tr("status.equipped") : tr("status.unequipped"), 0xFF55FF55);
                                this.cosmetics.clear();
                                this.cosmetics.addAll(WardrobeManager.getInstance().getCachedCosmetics());
                            }))
                            .exceptionally(ex -> {
                                this.minecraft.execute(() -> {
                                    isActionPending = false;
                                    setStatus(tr("error.toggle_failed"), 0xFFFF5555);
                                });
                                return null;
                            });
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleScroll(double x, double y, double scrollY) {
        int listX = leftPos + PREVIEW_PANEL_WIDTH + 14;
        int listY = topPos + 48;
        int listWidth = MODAL_WIDTH - PREVIEW_PANEL_WIDTH - 24;
        int listHeight = MODAL_HEIGHT - 58;

        if (x >= listX && x <= listX + listWidth && y >= listY && y <= listY + listHeight) {
            List<PlayerCosmeticEntryDto> items = getFilteredCosmetics();
            int totalHeight = items.size() * LIST_ITEM_HEIGHT;
            int maxScroll = Math.max(0, totalHeight - listHeight);

            if (maxScroll > 0) {
                this.scrollY -= (int) (scrollY * 20);
                this.scrollY = Mth.clamp(this.scrollY, 0, maxScroll);
            }
            return true;
        }
        return false;
    }

    private boolean handleKey(int key) {
        if (key == InputConstants.KEY_ESCAPE) {
            ClientCompat.setScreen(null);
            return true;
        }
        return false;
    }
}
