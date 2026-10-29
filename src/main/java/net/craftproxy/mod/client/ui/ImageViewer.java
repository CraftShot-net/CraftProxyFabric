package net.craftproxy.mod.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.craftproxy.mod.client.compat.Gfx;
import net.craftproxy.mod.client.images.ChatImages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Full-screen lightbox for chat images: the image fitted to the screen, arrows for messages
 * with several images, and buttons to copy the link or open it in the browser.
 * Clicking next to the image or pressing Escape closes it.
 */
public class ImageViewer {
    private static final int TOP = 30;
    private static final int BOTTOM = 34;
    private static final int SIDE = 34;

    private final List<String> urls;
    private int index;
    private boolean closed;
    private final Anim appear = new Anim();
    private final UiButton close;
    private final UiButton previous;
    private final UiButton next;
    private final UiButton copy;
    private final UiButton open;
    private int ix, iy, iw, ih;

    public ImageViewer(List<String> urls, int index, Consumer<String> onCopy, Consumer<String> onOpen) {
        this.urls = List.copyOf(urls);
        this.index = Math.max(0, Math.min(index, urls.size() - 1));
        this.close = UiButton.icon(Icon.CLOSE, UiButton.Style.GHOST, null, () -> closed = true);
        this.previous = UiButton.icon(Icon.CHEVRON_LEFT, UiButton.Style.SECONDARY, null, () -> step(-1));
        this.next = UiButton.icon(Icon.CHEVRON_RIGHT, UiButton.Style.SECONDARY, null, () -> step(1));
        this.copy = new UiButton(Component.translatable("screen.craftproxy.social.image.copy_link"), Icon.COPY,
                UiButton.Style.SECONDARY, () -> onCopy.accept(current()));
        this.open = new UiButton(Component.translatable("screen.craftproxy.social.image.open"), Icon.WORLD,
                UiButton.Style.SECONDARY, () -> onOpen.accept(current()));
    }

    public boolean isClosed() {
        return closed;
    }

    public void close() {
        closed = true;
    }

    private String current() {
        return urls.get(index);
    }

    private void step(int delta) {
        index = Math.floorMod(index + delta, urls.size());
    }

    public void draw(Gfx g, Font font, int mx, int my, int screenW, int screenH) {
        float t = Anim.easeOutCubic(appear.update(1.0F, 14.0F));
        float savedAlpha = UiRender.alpha;
        UiRender.alpha = savedAlpha * t;
        UiRender.fill(g, 0, 0, screenW, screenH, 0xE6050608);

        boolean multiple = urls.size() > 1;
        int areaX = multiple ? SIDE : 12;
        int areaW = screenW - areaX * 2;
        int areaH = screenH - TOP - BOTTOM;
        int scale = (int) Math.ceil(Minecraft.getInstance().getWindow().getGuiScale());
        ChatImages.Handle image = ChatImages.remote(current(), Math.min(ChatImages.MAX_SIDE, Math.max(areaW, areaH) * scale));

        if (image.ready()) {
            // Fit into the area, but show small images at most twice their real pixel size.
            float fit = Math.min(Math.min(areaW / (float) image.sourceWidth(), areaH / (float) image.sourceHeight()), 2.0F / scale);
            iw = Math.max(1, Math.round(image.sourceWidth() * fit));
            ih = Math.max(1, Math.round(image.sourceHeight() * fit));
            ix = (screenW - iw) / 2;
            iy = TOP + (areaH - ih) / 2;
            UiRender.shadow(g, ix, iy, iw, ih, 3, 4);
            g.blit(image.texture(), ix, iy, 0.0F, 0.0F, iw, ih, image.width(), image.height(), image.width(), image.height());
        } else {
            iw = Math.min(areaW, 160);
            ih = Math.min(areaH, 100);
            ix = (screenW - iw) / 2;
            iy = TOP + (areaH - ih) / 2;
            UiRender.card(g, ix, iy, iw, ih, 5, Theme.SURFACE, Theme.BORDER);
            if (image.status() == ChatImages.Status.FAILED) {
                Icon.IMAGE.drawCentered(g, ix + iw / 2, iy + ih / 2 - 6, 2, Theme.TEXT_MUTED);
                UiRender.textCentered(g, font, Component.translatable("screen.craftproxy.social.image.failed").getString(),
                        ix + iw / 2, iy + ih / 2 + 8, Theme.TEXT_MUTED);
            } else {
                UiRender.spinner(g, ix + iw / 2, iy + ih / 2, Theme.TEXT_SECONDARY);
            }
        }

        if (multiple) {
            String counter = (index + 1) + " / " + urls.size();
            UiRender.textCentered(g, font, counter, screenW / 2, 11, Theme.TEXT_SECONDARY);
            previous.bounds(8, TOP + areaH / 2 - 10, 20, 20).draw(g, font, mx, my);
            next.bounds(screenW - 28, TOP + areaH / 2 - 10, 20, 20).draw(g, font, mx, my);
        }
        close.bounds(screenW - 26, 6, 20, 20).draw(g, font, mx, my);

        int copyW = UiButton.widthFor(font, Component.translatable("screen.craftproxy.social.image.copy_link"), Icon.COPY);
        int openW = UiButton.widthFor(font, Component.translatable("screen.craftproxy.social.image.open"), Icon.WORLD);
        int bx = (screenW - copyW - 6 - openW) / 2;
        int by = screenH - BOTTOM + 8;
        copy.bounds(bx, by, copyW, 18).draw(g, font, mx, my);
        open.bounds(bx + copyW + 6, by, openW, 18).draw(g, font, mx, my);
        UiRender.alpha = savedAlpha;
    }

    /** Always consumes the click. */
    public void click(double mx, double my) {
        if (close.click(mx, my) || copy.click(mx, my) || open.click(mx, my)) return;
        if (urls.size() > 1 && (previous.click(mx, my) || next.click(mx, my))) return;
        if (!UiRender.inside(mx, my, ix, iy, iw, ih)) closed = true;
    }

    /** Arrow keys switch images; returns true when the key was used. */
    public boolean key(int key) {
        if (urls.size() < 2) return false;
        if (key == InputConstants.KEY_LEFT) {
            step(-1);
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            step(1);
            return true;
        }
        return false;
    }
}
