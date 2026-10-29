package net.craftproxy.mod.client.ui;

import net.craftproxy.mod.client.compat.Gfx;

/**
 * Tiny pixel-art glyphs drawn with fills, so they can be tinted freely and need no textures.
 * {@code #} marks a filled pixel.
 */
public enum Icon {
    SEARCH(
            ".###...",
            "#...#..",
            "#...#..",
            "#...#..",
            ".###...",
            "....##.",
            ".....##"),
    PLUS(
            "..#..",
            "..#..",
            "#####",
            "..#..",
            "..#.."),
    CLOSE(
            "#...#",
            ".#.#.",
            "..#..",
            ".#.#.",
            "#...#"),
    CHECK(
            "......#",
            ".....#.",
            "#...#..",
            ".#.#...",
            "..#...."),
    SEND(
            "#......",
            "###....",
            "#####..",
            "#######",
            "#####..",
            "###....",
            "#......"),
    PLAY(
            "#....",
            "###..",
            "#####",
            "###..",
            "#...."),
    USER_ADD(
            "..##.....",
            ".####..#.",
            ".####.###",
            "..##...#.",
            ".........",
            "######...",
            "######..."),
    USER_REMOVE(
            "..##.....",
            ".####....",
            ".####.###",
            "..##.....",
            ".........",
            "######...",
            "######..."),
    USER(
            "..##..",
            ".####.",
            ".####.",
            "..##..",
            "......",
            "######",
            "######"),
    WORLD(
            "..###..",
            ".#.#.#.",
            "#######",
            "#..#..#",
            "#######",
            ".#.#.#.",
            "..###.."),
    CHAT(
            "#######",
            "#.....#",
            "#.###.#",
            "#.....#",
            "#######",
            ".##....",
            ".#....."),
    MORE(
            "#",
            ".",
            "#",
            ".",
            "#"),
    COPY(
            "####...",
            "#..#...",
            "#.####.",
            "###..#.",
            "..#..#.",
            "..####."),
    CAPE(
            "#######",
            "#######",
            ".#####.",
            ".#####.",
            ".#####.",
            ".##.##.",
            ".#...#."),
    HAT(
            "..###..",
            ".#####.",
            ".#####.",
            "#######"),
    GRID(
            "##.##",
            "##.##",
            ".....",
            "##.##",
            "##.##"),
    CHEVRON_DOWN(
            "#...#",
            ".#.#.",
            "..#.."),
    CHEVRON_RIGHT(
            "#..",
            ".#.",
            "..#",
            ".#.",
            "#.."),
    INFO(
            "#",
            ".",
            "#",
            "#",
            "#"),
    SPARKLE(
            "...#...",
            "...#...",
            "..###..",
            "#######",
            "..###..",
            "...#...",
            "...#...");

    private final String[] rows;
    public final int width;
    public final int height;

    Icon(String... rows) {
        this.rows = rows;
        this.height = rows.length;
        int w = 0;
        for (String row : rows) w = Math.max(w, row.length());
        this.width = w;
    }

    /** Draws the glyph with its top-left corner at ({@code x}, {@code y}); every pixel becomes a {@code scale}² block. */
    public void draw(Gfx gfx, int x, int y, int scale, int color) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) != '#') {
                    c++;
                    continue;
                }
                int start = c;
                while (c < row.length() && row.charAt(c) == '#') c++;
                UiRender.fill(gfx, x + start * scale, y + r * scale, x + c * scale, y + (r + 1) * scale, color);
            }
        }
    }

    /** Draws the glyph centered on ({@code cx}, {@code cy}). */
    public void drawCentered(Gfx gfx, int cx, int cy, int scale, int color) {
        draw(gfx, cx - (width * scale) / 2, cy - (height * scale) / 2, scale, color);
    }
}
