package net.craftproxy.mod.client.ui;

import net.craftproxy.mod.client.compat.Gfx;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Drawing primitives of the CraftProxy UI: pixel-rounded rectangles, soft shadows, flat text,
 * player heads, status dots and badges. Everything goes through {@link Gfx}, so it works on
 * every supported version.
 * <p>
 * {@link #alpha} is a global opacity multiplier used for fade-in animations of whole screens.
 */
public final class UiRender {
    private UiRender() {
    }

    /** Global opacity applied to fills and text drawn through this class. */
    public static float alpha = 1.0F;

    // Horizontal inset of each corner row, indexed by radius.
    private static final int[][] CORNERS = {
            {},
            {1},
            {2, 1},
            {3, 1, 1},
            {4, 2, 1, 1},
            {5, 3, 2, 1, 1},
            {6, 4, 3, 2, 1, 1}
    };

    private static int a(int color) {
        return alpha >= 1.0F ? color : Theme.fade(color, alpha);
    }

    public static void fill(Gfx g, int x0, int y0, int x1, int y1, int color) {
        if (x1 <= x0 || y1 <= y0 || (color >>> 24) == 0) return;
        g.fill(x0, y0, x1, y1, a(color));
    }

    private static int clampRadius(int w, int h, int r) {
        return Math.max(0, Math.min(Math.min(r, CORNERS.length - 1), Math.min(w, h) / 2));
    }

    private static int inset(int[] corner, int row) {
        return row < corner.length ? corner[row] : 0;
    }

    /** Filled rectangle with pixel-rounded corners. */
    public static void roundRect(Gfx g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = clampRadius(w, h, r);
        int[] corner = CORNERS[r];
        fill(g, x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int in = corner[i];
            fill(g, x + in, y + i, x + w - in, y + i + 1, color);
            fill(g, x + in, y + h - 1 - i, x + w - in, y + h - i, color);
        }
    }

    /** One pixel wide outline following the same rounded shape as {@link #roundRect}. */
    public static void roundBorder(Gfx g, int x, int y, int w, int h, int r, int color) {
        if (w <= 1 || h <= 1) return;
        r = clampRadius(w, h, r);
        int[] corner = CORNERS[r];
        for (int i = 0; i <= r && i < h / 2 + 1; i++) {
            int cur = inset(corner, i);
            int top = y + i;
            int bottom = y + h - 1 - i;
            if (i == 0) {
                fill(g, x + cur, top, x + w - cur, top + 1, color);
                fill(g, x + cur, bottom, x + w - cur, bottom + 1, color);
                continue;
            }
            int prev = inset(corner, i - 1);
            int segEnd = Math.max(cur + 1, prev);
            fill(g, x + cur, top, x + segEnd, top + 1, color);
            fill(g, x + w - segEnd, top, x + w - cur, top + 1, color);
            if (bottom != top) {
                fill(g, x + cur, bottom, x + segEnd, bottom + 1, color);
                fill(g, x + w - segEnd, bottom, x + w - cur, bottom + 1, color);
            }
        }
        fill(g, x, y + r + 1, x + 1, y + h - r - 1, color);
        fill(g, x + w - 1, y + r + 1, x + w, y + h - r - 1, color);
    }

    /** Rounded card with fill and 1px border. */
    public static void card(Gfx g, int x, int y, int w, int h, int r, int fill, int border) {
        roundRect(g, x, y, w, h, r, fill);
        if ((border >>> 24) != 0) roundBorder(g, x, y, w, h, r, border);
    }

    /** Soft drop shadow made of a few stacked translucent rounded rectangles. */
    public static void shadow(Gfx g, int x, int y, int w, int h, int r, int layers) {
        for (int i = layers; i >= 1; i--) {
            roundRect(g, x - i, y - i + 2, w + i * 2, h + i * 2, r + i, 0x14000000);
        }
    }

    // --- Text ---

    public static void text(Gfx g, Font font, String text, int x, int y, int color) {
        int c = a(color);
        if ((c >>> 24) < 8) return;
        g.text(font, text, x, y, c, false);
    }

    public static void text(Gfx g, Font font, Component text, int x, int y, int color) {
        int c = a(color);
        if ((c >>> 24) < 8) return;
        g.text(font, text, x, y, c, false);
    }

    public static void text(Gfx g, Font font, FormattedCharSequence text, int x, int y, int color) {
        int c = a(color);
        if ((c >>> 24) < 8) return;
        g.text(font, text, x, y, c, false);
    }

    public static void textCentered(Gfx g, Font font, String text, int cx, int y, int color) {
        text(g, font, text, cx - font.width(text) / 2, y, color);
    }

    public static void textRight(Gfx g, Font font, String text, int rightX, int y, int color) {
        text(g, font, text, rightX - font.width(text), y, color);
    }

    /** Shortens {@code text} with an ellipsis so it fits into {@code maxWidth}. */
    public static String ellipsize(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String dots = "…";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + dots) > maxWidth) end--;
        return text.substring(0, end).trim() + dots;
    }

    // --- Decorations ---

    /**
     * Draws a player head (face + hat layer). When {@code bg} is opaque, the corners are
     * painted with it so the head looks rounded.
     */
    public static void head(Gfx g, Identifier skin, int x, int y, int size, int bg) {
        if (skin != null) {
            g.blit(skin, x, y, 8.0F, 8.0F, size, size, 8, 8, 64, 64);
            g.blit(skin, x, y, 40.0F, 8.0F, size, size, 8, 8, 64, 64);
        } else {
            fill(g, x, y, x + size, y + size, Theme.SURFACE_ACTIVE);
        }
        if ((bg >>> 24) == 0xFF) {
            int r = size >= 24 ? 3 : size >= 14 ? 2 : 1;
            int[] corner = CORNERS[r];
            for (int i = 0; i < r; i++) {
                int in = corner[i];
                fill(g, x, y + i, x + in, y + i + 1, bg);
                fill(g, x + size - in, y + i, x + size, y + i + 1, bg);
                fill(g, x, y + size - 1 - i, x + in, y + size - i, bg);
                fill(g, x + size - in, y + size - 1 - i, x + size, y + size - i, bg);
            }
        }
    }

    /** Paints the corners of a rectangle with {@code bg}, so a texture drawn there looks rounded. */
    public static void roundCorners(Gfx g, int x, int y, int w, int h, int r, int bg) {
        r = clampRadius(w, h, r);
        int[] corner = CORNERS[r];
        for (int i = 0; i < r; i++) {
            int in = corner[i];
            fill(g, x, y + i, x + in, y + i + 1, bg);
            fill(g, x + w - in, y + i, x + w, y + i + 1, bg);
            fill(g, x, y + h - 1 - i, x + in, y + h - i, bg);
            fill(g, x + w - in, y + h - 1 - i, x + w, y + h - i, bg);
        }
    }

    /**
     * Draws a texture of {@code texW} x {@code texH} pixels into the box, cropped to the box's
     * aspect ratio around the center (like CSS {@code object-fit: cover}).
     */
    public static void imageCover(Gfx g, Identifier texture, int texW, int texH, int x, int y, int w, int h) {
        int regionW = texW;
        int regionH = texH;
        if ((long) texW * h > (long) texH * w) regionW = Math.max(1, Math.round(texH * (float) w / h));
        else regionH = Math.max(1, Math.round(texW * (float) h / w));
        g.blit(texture, x, y, (texW - regionW) / 2.0F, (texH - regionH) / 2.0F, w, h, regionW, regionH, texW, texH);
    }

    /** Status dot with a ring in the background color, e.g. on the corner of an avatar. */
    public static void statusDot(Gfx g, int cx, int cy, int size, int color, int ring) {
        int half = size / 2;
        roundRect(g, cx - half - 1, cy - half - 1, size + 2, size + 2, 2, ring);
        roundRect(g, cx - half, cy - half, size, size, size >= 5 ? 2 : 1, color);
    }

    /** Small rounded pill with text; returns its width. */
    public static int pill(Gfx g, Font font, String text, int x, int y, int bg, int fg) {
        int w = font.width(text) + 8;
        roundRect(g, x, y, w, 11, 3, bg);
        text(g, font, text, x + 4, y + 2, fg);
        return w;
    }

    /** Red counter badge whose right edge is at {@code rightX}; returns its width (0 for count 0). */
    public static int countBadge(Gfx g, Font font, int count, int rightX, int y) {
        if (count <= 0) return 0;
        String s = count > 99 ? "99+" : Integer.toString(count);
        int w = Math.max(9, font.width(s) + 5);
        roundRect(g, rightX - w, y, w, 9, 3, Theme.DANGER);
        text(g, font, s, rightX - w + (w - font.width(s)) / 2 + 1, y + 1, Theme.TEXT_ON_ACCENT);
        return w;
    }

    /** Three dots pulsing one after another. */
    public static void spinner(Gfx g, int cx, int cy, int color) {
        long t = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            float phase = ((t / 180.0F) - i) % 3.0F;
            float k = phase < 1.0F ? 1.0F - phase : 0.25F;
            fill(g, cx - 5 + i * 4, cy - 1, cx - 3 + i * 4, cy + 1, Theme.fade(color, 0.35F + 0.65F * Math.max(0.25F, k)));
        }
    }

    /** Dark rounded tooltip box near the mouse, kept inside the screen. */
    public static void tooltip(Gfx g, Font font, List<String> lines, int mouseX, int mouseY, int screenW, int screenH) {
        if (lines.isEmpty()) return;
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        w += 10;
        int h = lines.size() * 10 + 6;
        int x = mouseX + 10;
        int y = mouseY - h - 4;
        if (x + w > screenW - 4) x = mouseX - w - 6;
        if (y < 4) y = mouseY + 14;
        x = Math.max(4, x);
        y = Math.min(y, screenH - h - 4);
        shadow(g, x, y, w, h, 3, 2);
        card(g, x, y, w, h, 3, 0xF20B0D11, Theme.BORDER_STRONG);
        for (int i = 0; i < lines.size(); i++) {
            text(g, font, lines.get(i), x + 5, y + 4 + i * 10, i == 0 ? Theme.TEXT : Theme.TEXT_SECONDARY);
        }
    }

    /** Horizontal 1px divider. */
    public static void divider(Gfx g, int x0, int x1, int y, int color) {
        fill(g, x0, y, x1, y + 1, color);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
