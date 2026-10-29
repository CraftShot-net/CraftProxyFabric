package net.craftproxy.mod.client.ui;

/**
 * Colors, spacing and small color math shared by every CraftProxy screen.
 * All colors are ARGB.
 */
public final class Theme {
    private Theme() {
    }

    // --- Surfaces (darkest to lightest) ---
    public static final int SCRIM = 0xB8050608;
    public static final int WINDOW = 0xFF111318;
    public static final int SIDEBAR = 0xFF161920;
    public static final int SURFACE = 0xFF1C2028;
    public static final int SURFACE_HOVER = 0xFF252A34;
    public static final int SURFACE_ACTIVE = 0xFF2C3240;
    public static final int INPUT = 0xFF0C0E12;
    public static final int BORDER = 0xFF262B35;
    public static final int BORDER_STRONG = 0xFF343A47;
    public static final int DIVIDER = 0xFF1F232B;

    // --- Text ---
    public static final int TEXT = 0xFFECEEF2;
    public static final int TEXT_SECONDARY = 0xFFA4ABB8;
    public static final int TEXT_MUTED = 0xFF6C7483;
    public static final int TEXT_ON_ACCENT = 0xFFFFFFFF;

    // --- Accent & states ---
    public static final int ACCENT = 0xFF5B7CFA;
    public static final int ACCENT_HOVER = 0xFF7390FF;
    public static final int ACCENT_DIM = 0xFF2A3566;
    public static final int SUCCESS = 0xFF34C47C;
    public static final int SUCCESS_HOVER = 0xFF4BD88F;
    public static final int DANGER = 0xFFEF4F5A;
    public static final int DANGER_HOVER = 0xFFFF6872;
    public static final int WARNING = 0xFFF5A524;
    public static final int ONLINE = 0xFF3DD68C;
    public static final int OFFLINE = 0xFF5B6270;

    // --- Chat ---
    public static final int BUBBLE_OUT = 0xFF4A66E0;
    public static final int BUBBLE_IN = 0xFF232833;

    // --- Spacing & radii ---
    public static final int PAD = 8;
    public static final int GAP = 4;
    public static final int RADIUS = 4;
    public static final int RADIUS_SMALL = 2;
    public static final int HEADER_HEIGHT = 30;

    /** Replaces the alpha channel. */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (clamp(alpha) << 24);
    }

    /** Multiplies the alpha channel by {@code factor} (0..1). */
    public static int fade(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        return withAlpha(color, Math.round(a * clamp01(factor)));
    }

    /** Linear blend between two ARGB colors. */
    public static int mix(int from, int to, float t) {
        t = clamp01(t);
        int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Brightens ({@code amount} > 0) or darkens a color, keeping alpha. */
    public static int shade(int color, float amount) {
        return amount >= 0
                ? mix(color, withAlpha(0xFFFFFF, (color >>> 24) & 0xFF), amount)
                : mix(color, withAlpha(0x000000, (color >>> 24) & 0xFF), -amount);
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }
}
