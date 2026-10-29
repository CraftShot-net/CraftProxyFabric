package net.craftproxy.mod.client.gui;

import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.compat.Gfx;
import net.craftproxy.mod.client.compat.SkinData;
import net.craftproxy.mod.client.ui.Icon;
import net.craftproxy.mod.client.ui.Theme;
import net.craftproxy.mod.client.ui.UiRender;
import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=1.21.2 {
import net.minecraft.client.gui.components.toasts.ToastManager;
//?} else {
/*import net.minecraft.client.gui.components.toasts.ToastComponent;
*///?}
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

public class WorldInviteToast implements Toast {

    private static final long DISPLAY_TIME_MS = 5000L;

    private final String hostName;
    private final String worldName;
    private final Supplier<SkinData> skinSupplier;
    private boolean hide = false;

    private WorldInviteToast(String hostName, String worldName, Supplier<SkinData> skinSupplier) {
        this.hostName = hostName;
        this.worldName = worldName;
        this.skinSupplier = skinSupplier;
    }

    public static void show(String hostName, String worldName, Supplier<SkinData> skinSupplier) {
        ClientCompat.addToast(new WorldInviteToast(hostName, worldName, skinSupplier));
    }

    //? if >=1.21.2 {
    @Override
    public Visibility getWantedVisibility() {
        return hide ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        if (fullyVisibleForMs >= DISPLAY_TIME_MS) {
            hide = true;
        }
    }
    //?}

    //? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        draw(new Gfx(graphics), font, fullyVisibleForMs);
    }
    //?} else if >=1.21.2 {
    /*@Override
    public void render(GuiGraphics graphics, Font font, long fullyVisibleForMs) {
        draw(new Gfx(graphics), font, fullyVisibleForMs);
    }
    *///?} else {
    /*@Override
    public Visibility render(GuiGraphics graphics, ToastComponent toasts, long fullyVisibleForMs) {
        draw(new Gfx(graphics), toasts.getMinecraft().font, fullyVisibleForMs);
        return fullyVisibleForMs >= DISPLAY_TIME_MS ? Visibility.HIDE : Visibility.SHOW;
    }
    *///?}

    private void draw(Gfx graphics, Font font, long visibleMs) {
        int w = width();
        int h = height();

        UiRender.card(graphics, 0, 0, w, h, 4, Theme.WINDOW, Theme.BORDER_STRONG);
        UiRender.roundRect(graphics, 1, 6, 2, h - 12, 1, Theme.ACCENT);

        // Host avatar with a small "play" badge
        Identifier skinId = skinSupplier.get().texture();
        UiRender.head(graphics, skinId, 9, 6, 20, Theme.WINDOW);
        UiRender.roundRect(graphics, 23, 20, 10, 10, 3, Theme.WINDOW);
        UiRender.roundRect(graphics, 24, 21, 8, 8, 2, Theme.ACCENT);
        Icon.PLAY.draw(graphics, 26, 22, 1, Theme.TEXT_ON_ACCENT);

        int textX = 38;
        int maxW = w - textX - 8;
        String title = Component.translatable("screen.craftproxy.social.toast.world_invite.title").getString();
        UiRender.text(graphics, font, Component.literal(UiRender.ellipsize(font, title, maxW - 8)).withStyle(s -> s.withBold(true)), textX, 6, Theme.ACCENT_HOVER);
        String message = Component.translatable("screen.craftproxy.social.toast.world_invite.message", hostName, worldName).getString();
        UiRender.text(graphics, font, UiRender.ellipsize(font, message, maxW), textX, 17, Theme.TEXT_SECONDARY);

        // Remaining display time
        float remaining = 1.0F - Math.min(1.0F, visibleMs / (float) DISPLAY_TIME_MS);
        int barW = Math.round((w - 10) * remaining);
        UiRender.fill(graphics, 5, h - 3, 5 + barW, h - 2, Theme.withAlpha(Theme.ACCENT, 0xA0));
    }

    @Override
    public int width() {
        return 210;
    }

    @Override
    public int height() {
        return 32;
    }
}
