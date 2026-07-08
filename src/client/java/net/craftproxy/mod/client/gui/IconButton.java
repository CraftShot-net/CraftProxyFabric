package net.craftproxy.mod.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

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

    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractDefaultSprite(graphics);

        int iconX = getX() + (getWidth() - iconSize) / 2;
        int iconY = getY() + (getHeight() - iconSize) / 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize, iconSize, iconSize);
    }
}
