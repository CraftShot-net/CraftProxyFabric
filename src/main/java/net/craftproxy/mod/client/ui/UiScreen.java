package net.craftproxy.mod.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.compat.Gfx;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Base class of the CraftProxy screens. It hides the version specific render and input
 * overrides and adds the shared pieces of the UI toolkit: open fade, context menu,
 * confirmation dialog, inline notices and tooltips.
 * <p>
 * Draw order per frame: scrim, {@link #drawBackdrop}, vanilla widgets (text fields, 3D preview),
 * {@link #drawForeground}, then overlays.
 */
public abstract class UiScreen extends Screen {
    private static final long OPEN_FADE_MS = 160L;
    private static final long NOTICE_MS = 3200L;

    private long openedAtMillis = System.currentTimeMillis();
    private ContextMenu contextMenu;
    private UiDialog dialog;
    private final List<String> tooltip = new ArrayList<>();
    private int hoverX;
    private int hoverY;

    private Component noticeText;
    private Icon noticeIcon;
    private int noticeColor;
    private long noticeAtMillis;

    protected UiScreen(Component title) {
        super(title);
    }

    @Override
    public void added() {
        super.added();
        openedAtMillis = System.currentTimeMillis();
        contextMenu = null;
        dialog = null;
    }

    // --- Hooks for subclasses ---

    /** Everything below the vanilla widgets. Mouse coordinates are off-screen while an overlay is open. */
    protected abstract void drawBackdrop(Gfx g, int mouseX, int mouseY);

    /** Everything above the vanilla widgets but below overlays. */
    protected void drawForeground(Gfx g, int mouseX, int mouseY) {
    }

    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean onMouseDragged(double mouseX, double mouseY, int button) {
        return false;
    }

    protected void onMouseReleased(double mouseX, double mouseY, int button) {
    }

    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        return false;
    }

    protected boolean onKey(int key) {
        return false;
    }

    /** Ctrl+V (Cmd+V on macOS) without an overlay; return true to stop the focused field from pasting text. */
    protected boolean onPaste() {
        return false;
    }

    /** Escape without dialog or context menu; return true when a screen specific overlay was closed instead of the screen. */
    protected boolean onEscape() {
        return false;
    }

    /** Key press while any overlay is open (the key is consumed either way). */
    protected void onOverlayKey(int key) {
    }

    /** Screen specific modal overlay above everything but notices, menus and dialogs, with the real mouse position. */
    protected void drawOverlay(Gfx g, int mouseX, int mouseY) {
    }

    /** Y coordinate the inline notice pill is centered on. */
    protected int noticeY() {
        return this.height - 26;
    }

    /** X coordinate the inline notice pill is centered on. */
    protected int noticeCenterX() {
        return this.width / 2;
    }

    /** Widest the notice pill may get. */
    protected int noticeMaxWidth() {
        return this.width - 40;
    }

    /** The notice pill is shifted left so it never ends right of this x. */
    protected int noticeMaxRight() {
        return this.width - 8;
    }

    // --- Toolkit helpers ---

    protected void openContextMenu(ContextMenu menu, int mouseX, int mouseY) {
        menu.open(this.font, mouseX, mouseY, this.width, this.height);
        this.contextMenu = menu;
    }

    protected void openDialog(UiDialog dialog) {
        this.dialog = dialog;
    }

    protected boolean hasOverlay() {
        return (contextMenu != null && contextMenu.isOpen()) || (dialog != null && !dialog.isClosed());
    }

    /** Shows a tooltip for the current frame. */
    protected void showTooltip(String... lines) {
        tooltip.clear();
        for (String line : lines) {
            if (line != null && !line.isEmpty()) tooltip.add(line);
        }
    }

    /** Draws a toolkit button and registers its tooltip while hovered. */
    protected void drawButton(Gfx g, UiButton button, int mouseX, int mouseY) {
        button.draw(g, this.font, mouseX, mouseY);
        String tip = button.hoveredTooltip(mouseX, mouseY);
        if (tip != null) showTooltip(tip);
    }

    /** Short status message shown as a pill near the bottom of the screen. */
    protected void showNotice(Component text, Icon icon, int color) {
        this.noticeText = text;
        this.noticeIcon = icon;
        this.noticeColor = color;
        this.noticeAtMillis = System.currentTimeMillis();
    }

    /** Focuses the field when its frame (outside the edit box itself) is clicked. */
    protected boolean clickField(UiTextField field, double mouseX, double mouseY) {
        if (!field.contains(mouseX, mouseY)) return false;
        if (!field.box.isMouseOver(mouseX, mouseY)) {
            this.setFocused(field.box);
            return true;
        }
        return false;
    }

    /** Window frame with soft shadow. */
    protected void drawWindow(Gfx g, int x, int y, int w, int h) {
        UiRender.shadow(g, x, y, w, h, 6, 5);
        UiRender.card(g, x, y, w, h, 6, Theme.WINDOW, Theme.BORDER);
    }

    // --- Rendering ---

    private void beginFrame(Gfx g, int mouseX, int mouseY) {
        float open = Anim.easeOutCubic((System.currentTimeMillis() - openedAtMillis) / (float) OPEN_FADE_MS);
        UiRender.alpha = open;
        tooltip.clear();
        boolean blocked = hasOverlay();
        hoverX = blocked ? -10000 : mouseX;
        hoverY = blocked ? -10000 : mouseY;
        UiRender.fill(g, 0, 0, this.width, this.height, Theme.SCRIM);
        drawBackdrop(g, hoverX, hoverY);
    }

    private void endFrame(Gfx g, int mouseX, int mouseY) {
        drawForeground(g, hoverX, hoverY);
        drawOverlay(g, mouseX, mouseY);
        drawNotice(g);
        if (contextMenu != null && contextMenu.isOpen()) {
            contextMenu.draw(g, this.font, mouseX, mouseY);
        }
        if (dialog != null) {
            if (dialog.isClosed()) dialog = null;
            else dialog.draw(g, this.font, mouseX, mouseY, this.width, this.height);
        }
        if (!tooltip.isEmpty() && !hasOverlay()) {
            UiRender.tooltip(g, this.font, tooltip, mouseX, mouseY, this.width, this.height);
        }
        UiRender.alpha = 1.0F;
    }

    private void drawNotice(Gfx g) {
        if (noticeText == null) return;
        long age = System.currentTimeMillis() - noticeAtMillis;
        if (age > NOTICE_MS) {
            noticeText = null;
            return;
        }
        float in = Anim.easeOutCubic(age / 160.0F);
        float out = Theme.clamp01((NOTICE_MS - age) / 300.0F);
        float t = Math.min(in, out);
        float savedAlpha = UiRender.alpha;
        UiRender.alpha = savedAlpha * t;

        int iconW = noticeIcon != null ? noticeIcon.width + 6 : 0;
        String text = UiRender.ellipsize(this.font, noticeText.getString(), noticeMaxWidth() - iconW - 18);
        int w = this.font.width(text) + iconW + 18;
        int h = 18;
        int x = Math.min(noticeCenterX() - w / 2, noticeMaxRight() - w);
        int y = noticeY() - h / 2 + Math.round((1.0F - in) * 6);
        UiRender.shadow(g, x, y, w, h, 5, 3);
        UiRender.card(g, x, y, w, h, 5, 0xF5181B22, Theme.withAlpha(noticeColor, 0x90));
        if (noticeIcon != null) {
            noticeIcon.draw(g, x + 9, y + (h - noticeIcon.height) / 2, 1, noticeColor);
        }
        UiRender.text(g, this.font, text, x + 9 + iconW, y + 5, Theme.TEXT);
        UiRender.alpha = savedAlpha;
    }

    //? if >=26.1 {
    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        Gfx gfx = new Gfx(graphics);
        beginFrame(gfx, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        endFrame(gfx, mouseX, mouseY);
    }
    //?} else if >=1.21.6 {
    /*@Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float delta) {
        Gfx gfx = new Gfx(graphics);
        beginFrame(gfx, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, delta);
        endFrame(gfx, mouseX, mouseY);
    }
    *///?} else if >=1.20.2 {
    /*// Screen.render() draws the background itself before the widgets, so the backdrop replaces it.
    @Override
    public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float delta) {
        beginFrame(new Gfx(graphics), mouseX, mouseY);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        endFrame(new Gfx(graphics), mouseX, mouseY);
    }
    *///?} else {
    /*@Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float delta) {
        Gfx gfx = new Gfx(graphics);
        this.renderBackground(graphics);
        beginFrame(gfx, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, delta);
        endFrame(gfx, mouseX, mouseY);
    }
    *///?}

    // --- Input ---

    /** Subclasses always see 0 = left, 1 = right, 2 = middle; 26.3 numbers the buttons from 1 (left, middle, right). */
    private static int normalizeButton(int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) return 0;
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) return 1;
        if (button == InputConstants.MOUSE_BUTTON_MIDDLE) return 2;
        return button;
    }

    private boolean handleClick(double mx, double my, int button) {
        if (dialog != null && !dialog.isClosed()) {
            if (button == 0) dialog.click(mx, my);
            return true;
        }
        if (contextMenu != null && contextMenu.isOpen()) {
            if (button == 0) contextMenu.click(mx, my);
            else contextMenu.close();
            return true;
        }
        if (this.getFocused() instanceof EditBox box && !box.isMouseOver(mx, my)) {
            this.setFocused(null);
        }
        return onMouseClicked(mx, my, button);
    }

    private boolean handleScroll(double mx, double my, double amount) {
        if (hasOverlay()) return true;
        return onScroll(mx, my, amount);
    }

    private boolean handleKey(int key, boolean paste) {
        if (key == InputConstants.KEY_ESCAPE) {
            if (dialog != null && !dialog.isClosed()) {
                dialog.close();
            } else if (contextMenu != null && contextMenu.isOpen()) {
                contextMenu.close();
            } else if (!onEscape()) {
                ClientCompat.setScreen(null);
            }
            return true;
        }
        if (hasOverlay()) {
            onOverlayKey(key);
            return true;
        }
        if (paste && onPaste()) return true;
        return onKey(key);
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        return handleClick(event.x(), event.y(), normalizeButton(event.button())) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(final MouseButtonEvent event) {
        onMouseReleased(event.x(), event.y(), normalizeButton(event.button()));
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(final MouseButtonEvent event, final double dx, final double dy) {
        return onMouseDragged(event.x(), event.y(), normalizeButton(event.button())) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        return handleKey(event.key(), event.isPaste()) || super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        return handleClick(mouseX, mouseY, normalizeButton(button)) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        onMouseReleased(mouseX, mouseY, normalizeButton(button));
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dx, final double dy) {
        return onMouseDragged(mouseX, mouseY, normalizeButton(button)) || super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return handleKey(keyCode, Screen.isPaste(keyCode)) || super.keyPressed(keyCode, scanCode, modifiers);
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
}
