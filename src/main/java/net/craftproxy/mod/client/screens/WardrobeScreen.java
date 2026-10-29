package net.craftproxy.mod.client.screens;

import net.craftproxy.mod.CraftProxy;
import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.compat.Gfx;
import net.craftproxy.mod.client.compat.SkinData;
import net.craftproxy.mod.client.dto.MyCosmeticsResponseDto.PlayerCosmeticEntryDto;
import net.craftproxy.mod.client.gui.WardrobePreviewWidget;
import net.craftproxy.mod.client.managers.CapeManager;
import net.craftproxy.mod.client.managers.WardrobeManager;
import net.craftproxy.mod.client.ui.Anim;
import net.craftproxy.mod.client.ui.Hits;
import net.craftproxy.mod.client.ui.Icon;
import net.craftproxy.mod.client.ui.ScrollArea;
import net.craftproxy.mod.client.ui.Theme;
import net.craftproxy.mod.client.ui.UiButton;
import net.craftproxy.mod.client.ui.UiRender;
import net.craftproxy.mod.client.ui.UiScreen;
import net.craftproxy.mod.client.utils.SkinManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Wardrobe: category sidebar, large interactive 3D preview and a grid of owned cosmetics.
 * Hovering a cape previews it on the model; clicking a card equips or unequips it.
 */
public class WardrobeScreen extends UiScreen {
    private static final String TRANSLATION_PREFIX = "screen.craftproxy.wardrobe.";
    private static final Identifier WARDROBE_ICON = CraftProxy.id("textures/gui/icon_wardrobe.png");
    private static final String ALL = "all";

    private static WardrobeScreen INSTANCE;

    public static WardrobeScreen getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new WardrobeScreen();
        }
        return INSTANCE;
    }

    private static final int CATEGORY_H = 22;
    private static final int CARD_H = 94;
    private static final int CARD_GAP = 6;
    private static final int MIN_CARD_W = 72;

    // --- Layout ---
    private int wx, wy, ww, wh;
    private int headerBottom;
    private int catW, previewX, previewY, previewW, previewH;
    private int gridX, gridY, gridW, gridH;

    // --- State ---
    private String activeCategory = ALL;
    private final List<PlayerCosmeticEntryDto> cosmetics = new CopyOnWriteArrayList<>();
    private Consumer<List<PlayerCosmeticEntryDto>> cosmeticsListener;
    private boolean loading;
    private String pendingCosmeticId;
    private PlayerCosmeticEntryDto hoveredCosmetic;

    // --- Widgets ---
    private WardrobePreviewWidget previewWidget;
    private Supplier<SkinData> cachedSkinSupplier;
    private UiButton closeButton;
    private final ScrollArea gridScroll = new ScrollArea();
    private final Hits hits = new Hits();
    private final Anim.Group<String> hover = new Anim.Group<>();
    private final Anim categoryIndicator = new Anim();

    private WardrobeScreen() {
        super(tr("title"));
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(TRANSLATION_PREFIX + key, args);
    }

    private static String trs(String key, Object... args) {
        return tr(key, args).getString();
    }

    // =====================================================================================
    // Lifecycle
    // =====================================================================================

    @Override
    protected void init() {
        super.init();
        this.ww = Math.min(600, this.width - 16);
        this.wh = Math.min(340, this.height - 16);
        this.wx = (this.width - ww) / 2;
        this.wy = (this.height - wh) / 2;
        this.headerBottom = wy + Theme.HEADER_HEIGHT;

        boolean compact = ww < 440;
        this.catW = compact ? 84 : 112;
        this.previewW = compact ? 112 : Math.min(160, Math.max(130, ww * 26 / 100));
        this.previewX = wx + catW + 10;
        this.previewY = headerBottom + 10;
        this.previewH = wy + wh - 10 - previewY;
        this.gridX = previewX + previewW + 10;
        this.gridY = previewY;
        this.gridW = wx + ww - 10 - gridX;
        this.gridH = previewH;

        closeButton = UiButton.icon(Icon.CLOSE, UiButton.Style.GHOST, null, () -> ClientCompat.setScreen(null))
                .bounds(wx + ww - 24, wy + 6, 18, 18);

        if (this.previewWidget != null) {
            this.removeWidget(this.previewWidget);
        }
        this.cachedSkinSupplier = SkinManager.getSkinAsyncPublic(this.minecraft.getUser().getName());
        this.previewWidget = new WardrobePreviewWidget(
                previewW - 16,
                previewH - 58,
                this.minecraft.getEntityModels(),
                this::resolveCurrentPlayerSkin,
                this::resolvePreviewCape
        );
        this.previewWidget.setX(previewX + 8);
        this.previewWidget.setY(previewY + 8);
        this.addRenderableWidget(this.previewWidget);

        if (WardrobeManager.isInitialized()) {
            if (cosmeticsListener != null) WardrobeManager.getInstance().removeCosmeticsListener(cosmeticsListener);
            cosmeticsListener = fresh -> this.minecraft.execute(() -> setCosmetics(fresh));
            WardrobeManager.getInstance().addCosmeticsListener(cosmeticsListener);
            refreshData();
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (cosmeticsListener != null && WardrobeManager.isInitialized()) {
            WardrobeManager.getInstance().removeCosmeticsListener(cosmeticsListener);
        }
        hoveredCosmetic = null;
    }

    private void refreshData() {
        if (!WardrobeManager.isInitialized()) return;
        setCosmetics(WardrobeManager.getInstance().getCachedCosmetics());
        loading = cosmetics.isEmpty();
        WardrobeManager.getInstance().fetchMyCosmetics()
                .thenAccept(fresh -> this.minecraft.execute(() -> {
                    loading = false;
                    setCosmetics(fresh);
                }))
                .exceptionally(ex -> {
                    this.minecraft.execute(() -> {
                        loading = false;
                        showNotice(tr("error.fetch_failed"), Icon.CLOSE, Theme.DANGER);
                    });
                    return null;
                });
    }

    private void setCosmetics(List<PlayerCosmeticEntryDto> fresh) {
        cosmetics.clear();
        cosmetics.addAll(fresh);
        // Preload cape textures into Minecraft's TextureManager
        for (PlayerCosmeticEntryDto item : fresh) {
            if (item.textureUrl() != null) {
                CapeManager.preloadTexture(item.cosmeticId(), item.textureUrl());
            }
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

    /** The hovered cape is tried on; otherwise the equipped one is shown. */
    private Identifier resolvePreviewCape() {
        PlayerCosmeticEntryDto hovered = this.hoveredCosmetic;
        if (hovered != null && isCape(hovered)) {
            Identifier texture = CapeManager.getLoadedTexture(hovered.cosmeticId());
            if (texture != null) return texture;
        }
        return CapeManager.getLocalPlayerEquippedCape();
    }

    private static boolean isCape(PlayerCosmeticEntryDto cosmetic) {
        return "cape".equalsIgnoreCase(cosmetic.type());
    }

    // =====================================================================================
    // Data helpers
    // =====================================================================================

    /** Category id to item count; always contains all, capes and hats, followed by unknown types. */
    private Map<String, Integer> categories() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put(ALL, cosmetics.size());
        result.put("cape", 0);
        result.put("hat", 0);
        for (PlayerCosmeticEntryDto c : cosmetics) {
            String type = c.type() == null ? "other" : c.type().toLowerCase(Locale.ROOT);
            result.merge(type, 1, Integer::sum);
        }
        return result;
    }

    private List<PlayerCosmeticEntryDto> filtered() {
        if (ALL.equals(activeCategory)) return cosmetics;
        List<PlayerCosmeticEntryDto> result = new ArrayList<>();
        for (PlayerCosmeticEntryDto c : cosmetics) {
            if (c.type() != null && c.type().equalsIgnoreCase(activeCategory)) result.add(c);
        }
        return result;
    }

    private String categoryLabel(String category) {
        String key = TRANSLATION_PREFIX + "category." + category;
        String localized = Component.translatable(key).getString();
        return localized.equals(key) ? titleCase(category) : localized;
    }

    private static Icon categoryIcon(String category) {
        return switch (category) {
            case ALL -> Icon.GRID;
            case "cape" -> Icon.CAPE;
            case "hat" -> Icon.HAT;
            default -> Icon.SPARKLE;
        };
    }

    private String typeLabel(String type) {
        String key = TRANSLATION_PREFIX + "type." + type;
        String localized = Component.translatable(key).getString();
        return localized.equals(key) ? titleCase(type) : localized;
    }

    private String formatCosmeticName(String cosmeticId) {
        String trKey = TRANSLATION_PREFIX + "item." + cosmeticId;
        String localized = Component.translatable(trKey).getString();
        if (!localized.equals(trKey)) {
            return localized;
        }
        // Fallback title formatting: "first_100_users_cape" -> "First 100 Users Cape"
        return titleCase(cosmeticId);
    }

    private static String titleCase(String id) {
        String[] words = id.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    // =====================================================================================
    // Actions
    // =====================================================================================

    private void toggleEquip(PlayerCosmeticEntryDto entry) {
        if (pendingCosmeticId != null || !WardrobeManager.isInitialized()) return;
        pendingCosmeticId = entry.cosmeticId();
        String name = formatCosmeticName(entry.cosmeticId());
        WardrobeManager.getInstance().toggleEquip(entry.cosmeticId())
                .thenAccept(res -> this.minecraft.execute(() -> {
                    pendingCosmeticId = null;
                    if (res.equipped()) showNotice(tr("status.equipped", name), Icon.CHECK, Theme.SUCCESS);
                    else showNotice(tr("status.unequipped", name), Icon.CLOSE, Theme.TEXT_SECONDARY);
                    setCosmetics(WardrobeManager.getInstance().getCachedCosmetics());
                }))
                .exceptionally(ex -> {
                    this.minecraft.execute(() -> {
                        pendingCosmeticId = null;
                        showNotice(tr("error.toggle_failed"), Icon.CLOSE, Theme.DANGER);
                    });
                    return null;
                });
    }

    // =====================================================================================
    // Rendering
    // =====================================================================================

    @Override
    protected int noticeY() {
        int below = wy + wh + 16;
        return below + 9 < this.height ? below : wy + wh - 24;
    }

    @Override
    protected int noticeCenterX() {
        // Inside the window the pill sits under the item grid instead of across the preview card.
        return noticeY() < wy + wh ? (gridX + wx + ww) / 2 : super.noticeCenterX();
    }

    @Override
    protected int noticeMaxWidth() {
        // Long messages may grow left over the preview card, but never across the category sidebar.
        return noticeY() < wy + wh ? wx + ww - previewX - 16 : super.noticeMaxWidth();
    }

    @Override
    protected int noticeMaxRight() {
        return noticeY() < wy + wh ? wx + ww - 8 : super.noticeMaxRight();
    }

    @Override
    protected void drawBackdrop(Gfx g, int mx, int my) {
        hits.clear();
        hoveredCosmetic = null;
        drawWindow(g, wx, wy, ww, wh);
        drawHeader(g, mx, my);
        drawCategories(g, mx, my);
        drawPreviewPanel(g);
        drawGrid(g, mx, my);
    }

    @Override
    protected void drawForeground(Gfx g, int mx, int my) {
        drawPreviewCaption(g);
    }

    private void drawHeader(Gfx g, int mx, int my) {
        g.blit(WARDROBE_ICON, wx + 10, wy + 7, 0.0F, 0.0F, 16, 16, 16, 16, 16, 16);
        Component title = tr("header").withStyle(s -> s.withBold(true));
        UiRender.text(g, this.font, title, wx + 32, wy + 11, Theme.TEXT);
        int equipped = 0;
        for (PlayerCosmeticEntryDto c : cosmetics) if (c.equipped()) equipped++;
        String summary = trs(cosmetics.size() == 1 ? "summary.one" : "summary", cosmetics.size(), equipped);
        int sx = wx + 32 + this.font.width(title) + 8;
        UiRender.text(g, this.font, UiRender.ellipsize(this.font, summary, closeButton.x - sx - 8), sx, wy + 11, Theme.TEXT_MUTED);
        drawButton(g, closeButton, mx, my);
        UiRender.divider(g, wx + 1, wx + ww - 1, headerBottom - 1, Theme.BORDER);
    }

    private void drawCategories(Gfx g, int mx, int my) {
        int top = headerBottom;
        int bottom = wy + wh - 1;
        UiRender.roundRect(g, wx + 1, top, catW - 1, bottom - top, 5, Theme.SIDEBAR);
        UiRender.fill(g, wx + 1, top, wx + catW, top + 6, Theme.SIDEBAR);
        UiRender.fill(g, wx + catW - 6, top, wx + catW, bottom, Theme.SIDEBAR);
        UiRender.fill(g, wx + catW, top, wx + catW + 1, bottom, Theme.BORDER);

        UiRender.text(g, this.font, trs("categories").toUpperCase(Locale.ROOT), wx + 10, top + 10, Theme.TEXT_MUTED);

        int x = wx + 6;
        int w = catW - 12;
        int y = top + 24;
        int index = 0;
        int activeIndex = 0;
        for (Map.Entry<String, Integer> entry : categories().entrySet()) {
            String category = entry.getKey();
            boolean active = category.equals(activeCategory);
            if (active) activeIndex = index;
            boolean hovered = UiRender.inside(mx, my, x, y, w, CATEGORY_H - 2);
            float t = hover.update("cat:" + category, hovered && !active);
            if (!active && t > 0.01F) UiRender.roundRect(g, x, y, w, CATEGORY_H - 2, 4, Theme.mix(Theme.SIDEBAR, Theme.SURFACE_HOVER, t));
            y += CATEGORY_H;
            index++;
        }

        // Selection highlight slides between categories.
        float indicator = categoryIndicator.update(activeIndex, 18.0F);
        int iy = top + 24 + Math.round(indicator * CATEGORY_H);
        UiRender.roundRect(g, x, iy, w, CATEGORY_H - 2, 4, Theme.SURFACE_ACTIVE);
        UiRender.roundRect(g, x, iy + 5, 2, CATEGORY_H - 12, 1, Theme.ACCENT);

        y = top + 24;
        for (Map.Entry<String, Integer> entry : categories().entrySet()) {
            String category = entry.getKey();
            boolean active = category.equals(activeCategory);
            int fg = active ? Theme.TEXT : Theme.TEXT_SECONDARY;
            categoryIcon(category).drawCentered(g, x + 11, y + (CATEGORY_H - 2) / 2, 1, active ? Theme.ACCENT_HOVER : Theme.TEXT_MUTED);
            String count = Integer.toString(entry.getValue());
            int countW = this.font.width(count);
            UiRender.text(g, this.font, UiRender.ellipsize(this.font, categoryLabel(category), w - 30 - countW - 6), x + 21, y + 6, fg);
            UiRender.textRight(g, this.font, count, x + w - 6, y + 6, Theme.TEXT_MUTED);
            hits.add(x, y, w, CATEGORY_H - 2, () -> {
                if (!category.equals(activeCategory)) {
                    UiButton.playClick();
                    activeCategory = category;
                    gridScroll.scrollToTop();
                }
            });
            y += CATEGORY_H;
        }
    }

    private void drawPreviewPanel(Gfx g) {
        UiRender.card(g, previewX, previewY, previewW, previewH, 5, Theme.SURFACE, Theme.BORDER);
        // Soft light behind the model and a floor shadow under its feet.
        int glowTop = previewY + 8;
        int glowBottom = previewY + previewH - 40;
        g.fillGradient(previewX + 1, glowTop, previewX + previewW - 1, glowBottom, Theme.withAlpha(Theme.ACCENT, 0x1C), Theme.withAlpha(Theme.ACCENT, 0x00));
        int feetY = previewWidget.getY() + previewWidget.getHeight() - 6;
        int cx = previewX + previewW / 2;
        UiRender.roundRect(g, cx - 26, feetY, 52, 6, 3, 0x40000000);
        UiRender.roundRect(g, cx - 18, feetY + 1, 36, 4, 2, 0x50000000);
        UiRender.divider(g, previewX + 1, previewX + previewW - 1, previewY + previewH - 46, Theme.BORDER);
    }

    private void drawPreviewCaption(Gfx g) {
        int y = previewY + previewH - 41;
        int x = previewX + 8;
        int w = previewW - 16;
        PlayerCosmeticEntryDto hovered = hoveredCosmetic;
        String label;
        String value;
        int valueColor;
        if (hovered != null && isCape(hovered)) {
            label = trs("preview.trying_on");
            value = formatCosmeticName(hovered.cosmeticId());
            valueColor = Theme.ACCENT_HOVER;
        } else {
            PlayerCosmeticEntryDto equippedCape = null;
            for (PlayerCosmeticEntryDto c : cosmetics) {
                if (c.equipped() && isCape(c)) equippedCape = c;
            }
            label = trs("preview.equipped_cape");
            value = equippedCape != null ? formatCosmeticName(equippedCape.cosmeticId()) : trs("preview.none");
            valueColor = equippedCape != null ? Theme.TEXT : Theme.TEXT_MUTED;
        }
        UiRender.text(g, this.font, UiRender.ellipsize(this.font, label.toUpperCase(Locale.ROOT), w), x, y, Theme.TEXT_MUTED);
        UiRender.text(g, this.font, UiRender.ellipsize(this.font, value, w), x, y + 11, valueColor);
        UiRender.text(g, this.font, UiRender.ellipsize(this.font, trs("preview.hint"), w), x, y + 24, Theme.withAlpha(Theme.TEXT_MUTED, 0xA0));
    }

    private void drawGrid(Gfx g, int mx, int my) {
        List<PlayerCosmeticEntryDto> items = filtered();
        Component title = Component.literal(categoryLabel(activeCategory)).withStyle(s -> s.withBold(true));
        UiRender.text(g, this.font, title, gridX + 2, gridY + 2, Theme.TEXT);
        UiRender.text(g, this.font, trs(items.size() == 1 ? "count.one" : "count", items.size()), gridX + 2 + this.font.width(title) + 6, gridY + 2, Theme.TEXT_MUTED);

        int top = gridY + 16;
        gridScroll.setBounds(gridX, top, gridW, gridY + gridH - top);

        if (!WardrobeManager.isInitialized()) {
            drawGridEmpty(g, Icon.INFO, trs("empty.not_connected"), null);
            return;
        }
        if (items.isEmpty()) {
            if (loading) {
                UiRender.spinner(g, gridX + gridW / 2, top + (gridScroll.h) / 2, Theme.TEXT_SECONDARY);
            } else if (cosmetics.isEmpty()) {
                drawGridEmpty(g, Icon.SPARKLE, trs("empty"), trs("empty.subtitle"));
            } else {
                drawGridEmpty(g, categoryIcon(activeCategory), trs("empty.category", categoryLabel(activeCategory)), null);
            }
            return;
        }

        int usable = gridW - 6;
        int columns = Math.max(1, (usable + CARD_GAP) / (MIN_CARD_W + CARD_GAP));
        int cardW = (usable - CARD_GAP * (columns - 1)) / columns;
        int rows = (items.size() + columns - 1) / columns;
        gridScroll.setContentHeight(rows * (CARD_H + CARD_GAP) - CARD_GAP + 2);
        gridScroll.update();

        gridScroll.begin(g);
        hits.clip(gridScroll.x, gridScroll.y, gridScroll.w, gridScroll.h);
        boolean inGrid = gridScroll.contains(mx, my);
        for (int i = 0; i < items.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int cx = gridX + col * (cardW + CARD_GAP);
            int cy = top + 1 + row * (CARD_H + CARD_GAP) - gridScroll.offset();
            if (cy + CARD_H < top || cy > top + gridScroll.h) continue;
            drawCard(g, items.get(i), cx, cy, cardW, inGrid && UiRender.inside(mx, my, cx, cy, cardW, CARD_H));
        }
        hits.clearClip();
        gridScroll.end(g, mx, my);
    }

    private void drawGridEmpty(Gfx g, Icon icon, String title, String subtitle) {
        int cx = gridX + gridW / 2;
        int cy = gridScroll.y + gridScroll.h / 2 - 10;
        UiRender.roundRect(g, cx - 18, cy - 38, 36, 36, 8, Theme.SURFACE);
        icon.drawCentered(g, cx, cy - 20, 3, Theme.TEXT_MUTED);
        UiRender.textCentered(g, this.font, UiRender.ellipsize(this.font, title, gridW - 10), cx, cy + 6, Theme.TEXT_SECONDARY);
        if (subtitle != null) {
            UiRender.textCentered(g, this.font, UiRender.ellipsize(this.font, subtitle, gridW - 10), cx, cy + 18, Theme.TEXT_MUTED);
        }
    }

    private void drawCard(Gfx g, PlayerCosmeticEntryDto cosmetic, int x, int y, int w, boolean hovered) {
        boolean equipped = cosmetic.equipped();
        boolean pending = cosmetic.cosmeticId().equals(pendingCosmeticId);
        float t = hover.update("card:" + cosmetic.cosmeticId(), hovered);
        if (hovered) hoveredCosmetic = cosmetic;

        int lift = Math.round(t * 2);
        int cy = y - lift + 2;
        int bg = Theme.mix(Theme.SURFACE, Theme.SURFACE_HOVER, t);
        int border = equipped ? Theme.mix(Theme.ACCENT, Theme.ACCENT_HOVER, t) : Theme.mix(Theme.BORDER, Theme.BORDER_STRONG, t);
        if (t > 0.01F) UiRender.roundRect(g, x + 1, cy + 3, w - 2, CARD_H - 2, 5, Theme.withAlpha(0x000000, Math.round(0x50 * t)));
        UiRender.card(g, x, cy, w, CARD_H - 2, 5, bg, border);

        // Thumbnail
        int thumbX = x + 4;
        int thumbY = cy + 4;
        int thumbW = w - 8;
        int thumbH = 56;
        UiRender.roundRect(g, thumbX, thumbY, thumbW, thumbH, 3, Theme.mix(0xFF141821, 0xFF181D28, t));
        g.fillGradient(thumbX, thumbY + thumbH / 2, thumbX + thumbW, thumbY + thumbH - 2, 0x00000000, 0x30000000);
        Identifier texture = isCape(cosmetic) ? CapeManager.getLoadedTexture(cosmetic.cosmeticId()) : null;
        if (texture != null) {
            // Front of the cape is at u 1..11, v 1..17 of the 64x32 cape texture.
            int capeW = 22;
            int capeH = 35;
            int capeX = thumbX + (thumbW - capeW) / 2;
            int capeY = thumbY + (thumbH - capeH) / 2;
            UiRender.fill(g, capeX + 2, capeY + 2, capeX + capeW + 2, capeY + capeH + 2, 0x50000000);
            g.blit(texture, capeX, capeY, 1.0F, 1.0F, capeW, capeH, 10, 16, 64, 32);
        } else {
            categoryIcon(cosmetic.type() == null ? "" : cosmetic.type().toLowerCase(Locale.ROOT))
                    .drawCentered(g, thumbX + thumbW / 2, thumbY + thumbH / 2, 3, Theme.withAlpha(Theme.TEXT_MUTED, 0xB0));
        }

        if (equipped) {
            // Check badge in the top-right corner of the thumbnail.
            UiRender.roundRect(g, thumbX + thumbW - 13, thumbY + 3, 10, 10, 3, Theme.ACCENT);
            Icon.CHECK.drawCentered(g, thumbX + thumbW - 8, thumbY + 8, 1, Theme.TEXT_ON_ACCENT);
        }

        // Texts
        String name = UiRender.ellipsize(this.font, formatCosmeticName(cosmetic.cosmeticId()), w - 10);
        UiRender.text(g, this.font, name, x + 5, cy + 64, Theme.TEXT);

        String status;
        int statusColor;
        if (pending) {
            UiRender.spinner(g, x + 12, cy + 80, Theme.ACCENT_HOVER);
            status = null;
            statusColor = 0;
        } else if (t > 0.5F) {
            status = trs(equipped ? "button.unequip" : "button.equip");
            statusColor = equipped ? Theme.DANGER_HOVER : Theme.ACCENT_HOVER;
        } else if (equipped) {
            status = trs("badge.equipped");
            statusColor = Theme.ACCENT_HOVER;
        } else {
            status = typeLabel(cosmetic.type() == null ? "other" : cosmetic.type());
            statusColor = Theme.TEXT_MUTED;
        }
        if (status != null) {
            UiRender.text(g, this.font, UiRender.ellipsize(this.font, status, w - 10), x + 5, cy + 76, statusColor);
        }

        hits.add(x, y, w, CARD_H, () -> {
            if (pendingCosmeticId == null) {
                UiButton.playClick();
                toggleEquip(cosmetic);
            }
        });
    }

    // =====================================================================================
    // Input
    // =====================================================================================

    @Override
    protected boolean onMouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (gridScroll.mouseClicked(mx, my)) return true;
        if (closeButton.click(mx, my)) return true;
        return hits.click(mx, my, button);
    }

    @Override
    protected boolean onMouseDragged(double mx, double my, int button) {
        return gridScroll.mouseDragged(my);
    }

    @Override
    protected void onMouseReleased(double mx, double my, int button) {
        gridScroll.mouseReleased();
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        return gridScroll.scroll(mx, my, amount);
    }
}
