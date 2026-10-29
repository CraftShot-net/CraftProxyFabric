package net.craftproxy.mod.client.compat;

import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=1.21.6 {
import net.minecraft.client.renderer.RenderPipelines;
//?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
*///?}
import net.minecraft.resources.Identifier;

/**
 * Wraps the GUI drawing context so screens can use one drawing API on every version
 * (GuiGraphics before 26.1, GuiGraphicsExtractor since).
 */
public final class Gfx {
    //? if >=26.1 {
    public final GuiGraphicsExtractor raw;

    public Gfx(GuiGraphicsExtractor raw) {
        this.raw = raw;
    }
    //?} else {
    /*public final GuiGraphics raw;

    public Gfx(GuiGraphics raw) {
        this.raw = raw;
    }
    *///?}

    public void fill(int x0, int y0, int x1, int y1, int color) {
        raw.fill(x0, y0, x1, y1, color);
    }

    public void horizontalLine(int x0, int x1, int y, int color) {
        //? if >=26.1 {
        raw.horizontalLine(x0, x1, y, color);
        //?} else {
        /*raw.hLine(x0, x1, y, color);
        *///?}
    }

    public void text(Font font, String text, int x, int y, int color) {
        //? if >=26.1 {
        raw.text(font, text, x, y, color);
        //?} else {
        /*raw.drawString(font, text, x, y, color);
        *///?}
    }

    public void centeredText(Font font, String text, int x, int y, int color) {
        //? if >=26.1 {
        raw.centeredText(font, text, x, y, color);
        //?} else {
        /*raw.drawCenteredString(font, text, x, y, color);
        *///?}
    }

    public void enableScissor(int x0, int y0, int x1, int y1) {
        raw.enableScissor(x0, y0, x1, y1);
    }

    public void disableScissor() {
        raw.disableScissor();
    }

    /**
     * Draws the region ({@code u}, {@code v}, {@code regionW} x {@code regionH}) of a
     * {@code texW} x {@code texH} texture scaled to {@code w} x {@code h} at ({@code x}, {@code y}).
     */
    public void blit(Identifier texture, int x, int y, float u, float v, int w, int h,
                     int regionW, int regionH, int texW, int texH) {
        //? if >=1.21.6 {
        raw.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, regionW, regionH, texW, texH);
        //?} else {
        /*RenderSystem.enableBlend();
        raw.blit(texture, x, y, w, h, u, v, regionW, regionH, texW, texH);
        *///?}
    }
}
