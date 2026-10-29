package net.craftproxy.mod.client.gui;

import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.compat.Gfx;
import net.craftproxy.mod.client.compat.SkinData;
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
        draw(new Gfx(graphics), font);
    }
    //?} else if >=1.21.2 {
    /*@Override
    public void render(GuiGraphics graphics, Font font, long fullyVisibleForMs) {
        draw(new Gfx(graphics), font);
    }
    *///?} else {
    /*@Override
    public Visibility render(GuiGraphics graphics, ToastComponent toasts, long fullyVisibleForMs) {
        draw(new Gfx(graphics), toasts.getMinecraft().font);
        return fullyVisibleForMs >= DISPLAY_TIME_MS ? Visibility.HIDE : Visibility.SHOW;
    }
    *///?}

    private void draw(Gfx graphics, Font font) {
        int w = width();
        int h = height();

        graphics.fill(0, 0, w, h, 0xEE0D0D0D);

        graphics.fill(0, 0, w, 2, 0xFF55AAFF);

        graphics.fill(0, 0, 3, h, 0xFF55AAFF);

        int skinX = 8;
        int skinY = (h - 16) / 2;
        Identifier skinId = skinSupplier.get().texture();
        graphics.blit(skinId, skinX, skinY, 8.0F, 8.0F, 16, 16, 8, 8, 64, 64);
        graphics.blit(skinId, skinX, skinY, 40.0F, 8.0F, 16, 16, 8, 8, 64, 64);

        int textX = skinX + 16 + 5;
        int textY1 = 5;
        int textY2 = 5 + font.lineHeight + 2;

        String title = Component.translatable("screen.craftproxy.friends.toast.world_invite.title").getString();
        graphics.text(font, title, textX, textY1, 0xFF55AAFF);

        String excerpt = Component.translatable("screen.craftproxy.friends.toast.world_invite.message", hostName, worldName).getString();
        int maxW = w - textX - 6;
        if (font.width(excerpt) > maxW) {
            while (!excerpt.isEmpty() && font.width(excerpt + "…") > maxW) {
                excerpt = excerpt.substring(0, excerpt.length() - 1);
            }
            excerpt += "…";
        }
        graphics.text(font, excerpt, textX, textY2, 0xFFCCCCCC);
    }

    @Override
    public int width() {
        return 200;
    }

    @Override
    public int height() {
        return 32;
    }
}
