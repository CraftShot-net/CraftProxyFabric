package net.craftproxy.mod.client.ui;

import net.craftproxy.mod.client.compat.Gfx;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * Rounded input field: the screen draws the frame through {@link #draw}, while a borderless
 * vanilla {@link EditBox} (added as a widget by the screen) handles caret, selection and clipboard.
 */
public class UiTextField {
    public final EditBox box;
    private final Icon icon;
    public int x, y, w, h;
    private final Anim focus = new Anim();

    public UiTextField(Font font, int maxLength, Component hint, Icon icon) {
        this.icon = icon;
        this.box = new EditBox(font, 0, 0, 10, 10, hint);
        this.box.setMaxLength(maxLength);
        this.box.setBordered(false);
        this.box.setHint(Component.literal(hint.getString()).withStyle(style -> style.withColor(TextColor.fromRgb(Theme.TEXT_MUTED & 0xFFFFFF))));
        this.box.setTextColor(Theme.TEXT);
        //? if >=1.21.11 {
        this.box.setTextShadow(false);
        //?}
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int textX = x + 6 + (icon != null ? icon.width + 5 : 0);
        this.box.setX(textX);
        this.box.setY(y + (h - 8) / 2);
        this.box.setWidth(Math.max(10, x + w - 6 - textX));
    }

    public String getValue() {
        return box.getValue();
    }

    public void setValue(String value) {
        box.setValue(value);
    }

    public boolean isFocused() {
        return box.isFocused();
    }

    public boolean contains(double mx, double my) {
        return box.visible && UiRender.inside(mx, my, x, y, w, h);
    }

    public void setVisible(boolean visible) {
        box.setVisible(visible);
        if (!visible) box.setFocused(false);
    }

    public void draw(Gfx g, int mx, int my) {
        if (!box.visible) return;
        float f = focus.update(box.isFocused());
        boolean hovered = contains(mx, my);
        int border = Theme.mix(hovered ? Theme.BORDER_STRONG : Theme.BORDER, Theme.ACCENT, f);
        UiRender.card(g, x, y, w, h, 3, Theme.INPUT, border);
        if (icon != null) {
            icon.draw(g, x + 6, y + (h - icon.height) / 2, 1, Theme.mix(Theme.TEXT_MUTED, Theme.ACCENT_HOVER, f));
        }
    }
}
