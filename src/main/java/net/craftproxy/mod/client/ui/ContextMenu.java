package net.craftproxy.mod.client.ui;

import net.craftproxy.mod.client.compat.Gfx;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * Right-click menu: a small floating card with icon + label entries, separators and an
 * optional header. Opens with a short fade/slide and closes on any click outside or Escape.
 */
public class ContextMenu {
    private static final int ENTRY_HEIGHT = 16;
    private static final int SEPARATOR_HEIGHT = 5;
    private static final int HEADER_HEIGHT = 14;

    private record Entry(String label, Icon icon, boolean enabled, boolean danger, String hint, Runnable action) {
        boolean separator() {
            return label == null;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private String header;
    private int x, y, w, h;
    private boolean open;
    private final Anim appear = new Anim();
    private final Anim.Group<Integer> hover = new Anim.Group<>();

    public ContextMenu header(String header) {
        this.header = header;
        return this;
    }

    public ContextMenu add(String label, Icon icon, Runnable action) {
        entries.add(new Entry(label, icon, true, false, null, action));
        return this;
    }

    /** Entry that is greyed out; {@code hint} explains why (shown as second line). */
    public ContextMenu add(String label, Icon icon, boolean enabled, String hint, Runnable action) {
        entries.add(new Entry(label, icon, enabled, false, enabled ? null : hint, action));
        return this;
    }

    public ContextMenu danger(String label, Icon icon, Runnable action) {
        entries.add(new Entry(label, icon, true, true, null, action));
        return this;
    }

    public ContextMenu separator() {
        entries.add(new Entry(null, null, false, false, null, null));
        return this;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    private int entryHeight(Entry e) {
        if (e.separator()) return SEPARATOR_HEIGHT;
        return e.hint() != null ? ENTRY_HEIGHT + 9 : ENTRY_HEIGHT;
    }

    /** Opens the menu at the mouse position, kept inside the screen. */
    public void open(Font font, int mouseX, int mouseY, int screenW, int screenH) {
        int width = header != null ? font.width(header) + 16 : 0;
        int height = 6 + (header != null ? HEADER_HEIGHT : 0);
        for (Entry e : entries) {
            height += entryHeight(e);
            if (!e.separator()) {
                width = Math.max(width, font.width(e.label()) + 34);
                if (e.hint() != null) width = Math.max(width, font.width(e.hint()) + 34);
            }
        }
        this.w = Math.max(width, 110);
        this.h = height;
        this.x = Math.max(4, Math.min(mouseX, screenW - w - 4));
        this.y = Math.max(4, Math.min(mouseY, screenH - h - 4));
        this.open = true;
        this.appear.set(0.0F);
        this.hover.clear();
    }

    public void draw(Gfx g, Font font, int mx, int my) {
        if (!open) return;
        float t = Anim.easeOutCubic(appear.update(1.0F, 16.0F));
        float savedAlpha = UiRender.alpha;
        UiRender.alpha = savedAlpha * t;
        int dy = Math.round((1.0F - t) * -4);
        int top = y + dy;

        UiRender.shadow(g, x, top, w, h, 4, 3);
        UiRender.card(g, x, top, w, h, 4, 0xFA14171D, Theme.BORDER_STRONG);

        int cy = top + 3;
        if (header != null) {
            UiRender.text(g, font, UiRender.ellipsize(font, header, w - 14), x + 8, cy + 3, Theme.TEXT_MUTED);
            cy += HEADER_HEIGHT;
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int eh = entryHeight(e);
            if (e.separator()) {
                UiRender.divider(g, x + 6, x + w - 6, cy + 2, Theme.BORDER);
                cy += eh;
                continue;
            }
            boolean hovered = e.enabled() && UiRender.inside(mx, my, x + 3, cy, w - 6, eh);
            float ht = hover.update(i, hovered);
            int baseFg = e.danger() ? Theme.DANGER_HOVER : Theme.TEXT;
            if (ht > 0.01F) {
                int bg = e.danger() ? Theme.withAlpha(Theme.DANGER, Math.round(0x40 * ht)) : Theme.withAlpha(Theme.SURFACE_ACTIVE, Math.round(255 * ht));
                UiRender.roundRect(g, x + 3, cy, w - 6, eh, 3, bg);
            }
            int fg = e.enabled() ? baseFg : Theme.TEXT_MUTED;
            if (e.icon() != null) {
                e.icon().drawCentered(g, x + 13, cy + ENTRY_HEIGHT / 2, 1, e.enabled() ? (e.danger() ? Theme.DANGER_HOVER : Theme.mix(Theme.TEXT_SECONDARY, Theme.ACCENT_HOVER, ht)) : Theme.TEXT_MUTED);
            }
            UiRender.text(g, font, e.label(), x + 24, cy + 4, fg);
            if (e.hint() != null) {
                UiRender.text(g, font, e.hint(), x + 24, cy + 14, Theme.withAlpha(Theme.TEXT_MUTED, 0xC0));
            }
            cy += eh;
        }
        UiRender.alpha = savedAlpha;
    }

    /** Handles a click while open: runs a hovered entry and closes. Always consumes the click. */
    public boolean click(double mx, double my) {
        if (!open) return false;
        if (!UiRender.inside(mx, my, x, y, w, h)) {
            close();
            return true;
        }
        int cy = y + 3 + (header != null ? HEADER_HEIGHT : 0);
        for (Entry e : entries) {
            int eh = entryHeight(e);
            if (!e.separator() && my >= cy && my < cy + eh) {
                if (e.enabled()) {
                    UiButton.playClick();
                    close();
                    e.action().run();
                }
                return true;
            }
            cy += eh;
        }
        return true;
    }
}
