package net.craftproxy.mod.client.ui;

import net.craftproxy.mod.client.compat.Gfx;

/**
 * Vertically scrollable region with smooth (eased) scrolling, scissor clipping and a slim
 * scrollbar that fades in while hovered or scrolling and can be dragged.
 */
public class ScrollArea {
    private static final float WHEEL_STEP = 28.0F;
    private static final int BAR_WIDTH = 3;

    public int x, y, w, h;
    private int contentHeight;
    private float target;
    private float current;
    private final Anim smooth = new Anim();
    private boolean dragging;
    private double dragStartMouseY;
    private float dragStartScroll;
    private long lastScrollMillis;
    private final Anim barVisibility = new Anim();

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        clamp();
    }

    public void setContentHeight(int contentHeight) {
        this.contentHeight = contentHeight;
        clamp();
    }

    public int maxScroll() {
        return Math.max(0, contentHeight - h);
    }

    /** Current (animated) scroll offset in pixels. */
    public int offset() {
        return Math.round(current);
    }

    public boolean canScroll() {
        return maxScroll() > 0;
    }

    public boolean isAtBottom() {
        return target >= maxScroll() - 2;
    }

    public void scrollToTop() {
        target = 0;
        current = 0;
    }

    public void scrollToBottom(boolean instant) {
        target = maxScroll();
        if (instant) current = target;
    }

    /** Scrolls so the range [{@code top}, {@code bottom}) of the content is visible. */
    public void ensureVisible(int top, int bottom) {
        if (top < target) target = top;
        else if (bottom > target + h) target = bottom - h;
        clamp();
    }

    private void clamp() {
        target = Math.max(0, Math.min(target, maxScroll()));
        current = Math.max(0, Math.min(current, Math.max(maxScroll(), 0)));
    }

    public boolean contains(double mx, double my) {
        return UiRender.inside(mx, my, x, y, w, h);
    }

    /** Eases the offset; call once per frame before drawing the content. */
    public void update() {
        clamp();
        smooth.set(current);
        current = smooth.update(target, 20.0F);
    }

    public boolean scroll(double mx, double my, double amount) {
        if (!contains(mx, my)) return false;
        if (canScroll()) {
            target -= (float) amount * WHEEL_STEP;
            clamp();
            lastScrollMillis = System.currentTimeMillis();
        }
        return true;
    }

    public void begin(Gfx g) {
        g.enableScissor(x, y, x + w, y + h);
    }

    /** Ends clipping and draws the scrollbar. */
    public void end(Gfx g, int mx, int my) {
        g.disableScissor();
        drawBar(g, mx, my);
    }

    private int thumbHeight() {
        return Math.max(16, (int) (h * (h / (float) Math.max(1, contentHeight))));
    }

    private int thumbY() {
        int max = maxScroll();
        return y + (max == 0 ? 0 : (int) ((current / max) * (h - thumbHeight())));
    }

    private int barX() {
        return x + w - BAR_WIDTH - 1;
    }

    private void drawBar(Gfx g, int mx, int my) {
        if (!canScroll()) return;
        boolean active = dragging || contains(mx, my) || System.currentTimeMillis() - lastScrollMillis < 700;
        float vis = barVisibility.update(active ? 1.0F : 0.35F, 10.0F);
        boolean overBar = dragging || UiRender.inside(mx, my, barX() - 2, y, BAR_WIDTH + 3, h);
        int color = Theme.fade(overBar ? Theme.TEXT_SECONDARY : Theme.TEXT_MUTED, vis);
        UiRender.roundRect(g, barX(), thumbY(), BAR_WIDTH, thumbHeight(), 1, color);
    }

    public boolean mouseClicked(double mx, double my) {
        if (!canScroll() || !UiRender.inside(mx, my, barX() - 2, y, BAR_WIDTH + 4, h)) return false;
        int ty = thumbY();
        if (my < ty || my >= ty + thumbHeight()) {
            // Jump so the thumb centers on the click, then keep dragging from there.
            float ratio = (float) ((my - y - thumbHeight() / 2.0) / Math.max(1, h - thumbHeight()));
            target = ratio * maxScroll();
            clamp();
            current = target;
        }
        dragging = true;
        dragStartMouseY = my;
        dragStartScroll = target;
        return true;
    }

    public boolean mouseDragged(double my) {
        if (!dragging) return false;
        float perPixel = maxScroll() / (float) Math.max(1, h - thumbHeight());
        target = dragStartScroll + (float) (my - dragStartMouseY) * perPixel;
        clamp();
        current = target;
        return true;
    }

    public boolean mouseReleased() {
        boolean was = dragging;
        dragging = false;
        return was;
    }
}
