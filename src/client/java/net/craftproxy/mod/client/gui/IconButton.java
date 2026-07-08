package net.craftproxy.mod.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A small square button that renders the vanilla button background (with proper
 * hover/active states) and blits a custom icon texture centered on top of it.
 */
public class IconButton extends ButtonWidget {

    private final Identifier icon;
    private final int iconSize;

    public IconButton(int x, int y, int size, Identifier icon, Text tooltipMessage, PressAction onPress) {
        // Pass Text.empty() as the message so it doesn't render text over your icon
        super(x, y, size, size, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.icon = icon;
        this.iconSize = size - 4;

        // Add the tooltip message
        this.setTooltip(Tooltip.of(tooltipMessage));
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        // Calling super.renderButton handles the vanilla background and hover states
        super.renderButton(context, mouseX, mouseY, delta);

        int iconX = this.getX() + (this.getWidth() - this.iconSize) / 2;
        int iconY = this.getY() + (this.getHeight() - this.iconSize) / 2;

        // Blit the custom icon centered on the button
        context.drawTexture(this.icon, iconX, iconY, 0.0F, 0.0F, this.iconSize, this.iconSize, this.iconSize, this.iconSize);
    }
}