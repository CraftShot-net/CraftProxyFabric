package net.craftproxy.mod.client.ui;

import net.craftproxy.mod.client.compat.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Flat, rounded button with an optional icon, eased hover state and an optional tooltip.
 * Not a vanilla widget: the owning screen draws it and forwards clicks.
 */
public class UiButton {
    public enum Style {PRIMARY, SECONDARY, GHOST, SUCCESS, DANGER}

    public int x, y, w, h;
    public boolean visible = true;
    public boolean loading = false;
    private Component label;
    private final Icon icon;
    private final Style style;
    private final Runnable onClick;
    private BooleanSupplier enabled = () -> true;
    private Supplier<String> tooltip;
    private final Anim hover = new Anim();

    public UiButton(Component label, Icon icon, Style style, Runnable onClick) {
        this.label = label;
        this.icon = icon;
        this.style = style;
        this.onClick = onClick;
    }

    public static UiButton icon(Icon icon, Style style, String tooltip, Runnable onClick) {
        UiButton button = new UiButton(null, icon, style, onClick);
        button.tooltip = () -> tooltip;
        return button;
    }

    public UiButton bounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public UiButton enabledWhen(BooleanSupplier enabled) {
        this.enabled = enabled;
        return this;
    }

    public UiButton tooltip(Supplier<String> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public void setLabel(Component label) {
        this.label = label;
    }

    public boolean isEnabled() {
        return !loading && enabled.getAsBoolean();
    }

    public boolean isHovered(double mx, double my) {
        return visible && UiRender.inside(mx, my, x, y, w, h);
    }

    /** Preferred width for a label button with the given font. */
    public static int widthFor(Font font, Component label, Icon icon) {
        int w = 16;
        if (label != null) w += font.width(label);
        if (icon != null) w += icon.width + (label != null ? 5 : 0);
        return w;
    }

    public void draw(Gfx g, Font font, int mx, int my) {
        if (!visible) return;
        boolean enabled = isEnabled();
        boolean hovered = isHovered(mx, my);
        float t = hover.update(enabled && hovered);

        int bg;
        int fg;
        int border = 0;
        switch (style) {
            case PRIMARY -> {
                bg = Theme.mix(Theme.ACCENT, Theme.ACCENT_HOVER, t);
                fg = Theme.TEXT_ON_ACCENT;
            }
            case SUCCESS -> {
                bg = Theme.mix(Theme.withAlpha(Theme.SUCCESS, 0x38), Theme.SUCCESS, t);
                fg = Theme.mix(Theme.SUCCESS_HOVER, Theme.TEXT_ON_ACCENT, t);
            }
            case DANGER -> {
                bg = Theme.mix(Theme.withAlpha(Theme.DANGER, 0x30), Theme.DANGER, t);
                fg = Theme.mix(Theme.DANGER_HOVER, Theme.TEXT_ON_ACCENT, t);
            }
            case GHOST -> {
                bg = Theme.withAlpha(Theme.SURFACE_HOVER, Math.round(255 * t));
                fg = Theme.mix(Theme.TEXT_SECONDARY, Theme.TEXT, t);
            }
            default -> {
                bg = Theme.mix(Theme.SURFACE, Theme.SURFACE_HOVER, t);
                fg = Theme.mix(Theme.TEXT_SECONDARY, Theme.TEXT, t);
                border = Theme.mix(Theme.BORDER, Theme.BORDER_STRONG, t);
            }
        }
        if (!enabled) {
            bg = style == Style.GHOST ? 0 : Theme.SURFACE;
            fg = Theme.TEXT_MUTED;
            border = style == Style.SECONDARY ? Theme.BORDER : 0;
        }

        UiRender.card(g, x, y, w, h, h >= 16 ? 3 : 2, bg, border);

        if (loading) {
            UiRender.spinner(g, x + w / 2, y + h / 2, fg);
            return;
        }

        int contentW = (label != null ? font.width(label) : 0) + (icon != null ? icon.width + (label != null ? 5 : 0) : 0);
        int cx = x + (w - contentW) / 2;
        if (icon != null) {
            icon.draw(g, cx, y + (h - icon.height + 1) / 2, 1, fg);
            cx += icon.width + 5;
        }
        if (label != null) {
            UiRender.text(g, font, label, cx, y + (h - 8) / 2 + 1, fg);
        }
    }

    /** Tooltip text while hovered, or {@code null}. */
    public String hoveredTooltip(double mx, double my) {
        return tooltip != null && isHovered(mx, my) ? tooltip.get() : null;
    }

    /** Runs the action when clicked; also swallows clicks on a disabled button. */
    public boolean click(double mx, double my) {
        if (!isHovered(mx, my)) return false;
        if (isEnabled()) {
            playClick();
            onClick.run();
        }
        return true;
    }

    public static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
