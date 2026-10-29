package net.craftproxy.mod.client.gui;

import net.craftproxy.mod.client.compat.Gfx;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if <1.21.9 {
/*import net.minecraft.client.gui.Font;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A small square button that renders the vanilla button background (with proper
 * hover/active states) and blits a custom icon texture centered on top of it.
 */
public class IconButton extends Button {

    private final Identifier icon;
    private final int iconSize;

    public IconButton(int x, int y, int size, Identifier icon, Component tooltipMessage, OnPress onPress) {
        super(x, y, size, size, tooltipMessage, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.iconSize = size - 4;
    }

    //? if >=26.1 {
    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractDefaultSprite(graphics);
        drawIcon(new Gfx(graphics));
    }
    //?} else if >=1.21.9 {
    /*@Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDefaultSprite(graphics);
        drawIcon(new Gfx(graphics));
    }
    *///?} else {
    /*@Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        drawIcon(new Gfx(graphics));
    }

    // The message is only used as tooltip/narration, the icon replaces the label.
    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
    }
    *///?}

    private void drawIcon(Gfx graphics) {
        int iconX = getX() + (getWidth() - iconSize) / 2;
        int iconY = getY() + (getHeight() - iconSize) / 2;

        graphics.blit(icon, iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize, iconSize, iconSize);
    }
}
