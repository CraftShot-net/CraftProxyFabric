package net.craftproxy.mod.client.gui;

import net.craftproxy.mod.client.compat.Gfx;
import net.craftproxy.mod.client.ui.Anim;
import net.craftproxy.mod.client.ui.Theme;
import net.craftproxy.mod.client.ui.UiRender;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.IntSupplier;

/**
 * Small square button in the CraftProxy style (dark rounded tile, accent border on hover)
 * with a custom icon texture and an optional red counter badge.
 */
public class IconButton extends Button {

    private final Identifier icon;
    private final int iconSize;
    private final Anim hover = new Anim();
    private IntSupplier badge = () -> 0;

    public IconButton(int x, int y, int size, Identifier icon, Component tooltipMessage, OnPress onPress) {
        super(x, y, size, size, tooltipMessage, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.iconSize = Math.min(16, size - 4);
    }

    /** Shows a counter badge (e.g. pending requests) in the top-right corner while it is > 0. */
    public IconButton badge(IntSupplier badge) {
        this.badge = badge;
        return this;
    }

    //? if >=26.1 {
    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        draw(new Gfx(graphics));
    }
    //?} else if >=1.21.9 {
    /*@Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(new Gfx(graphics));
    }
    *///?} else {
    /*@Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(new Gfx(graphics));
    }
    *///?}

    private void draw(Gfx graphics) {
        float t = hover.update(this.active && this.isHoveredOrFocused());
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        int bg = this.active ? Theme.mix(0xD0111318, 0xF01E2330, t) : 0x90111318;
        int border = this.active ? Theme.mix(Theme.withAlpha(Theme.BORDER_STRONG, 0xC0), Theme.ACCENT, t) : 0x50343A47;
        UiRender.card(graphics, x, y, w, h, 4, bg, border);

        int iconX = x + (w - iconSize) / 2;
        int iconY = y + (h - iconSize) / 2 - Math.round(t);
        graphics.blit(icon, iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize, iconSize, iconSize);
        if (!this.active) {
            UiRender.fill(graphics, iconX, iconY, iconX + iconSize, iconY + iconSize, 0x99111318);
        }
        if (this.active) {
            UiRender.countBadge(graphics, Minecraft.getInstance().font, badge.getAsInt(), x + w + 3, y - 3);
        }
    }
}
