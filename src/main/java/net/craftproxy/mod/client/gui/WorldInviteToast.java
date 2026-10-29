package net.craftproxy.mod.client.gui;

import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.compat.Gfx;
import net.craftproxy.mod.client.compat.SkinData;
import net.craftproxy.mod.client.ui.Icon;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class WorldInviteToast extends CraftToast {

    private final String hostName;
    private final String body;
    private final Supplier<SkinData> skinSupplier;

    private WorldInviteToast(String hostName, String worldName, Supplier<SkinData> skinSupplier) {
        this.hostName = hostName;
        this.body = Component.translatable("screen.craftproxy.social.toast.world_invite.body", worldName).getString();
        this.skinSupplier = skinSupplier;
    }

    public static void show(String hostName, String worldName, Supplier<SkinData> skinSupplier) {
        ClientCompat.addToast(new WorldInviteToast(hostName, worldName, skinSupplier));
    }

    @Override
    protected void draw(Gfx graphics, Font font, long elapsedMs) {
        drawCard(graphics, font, skinSupplier.get().texture(), Icon.PLAY, hostName, body, 0, elapsedMs);
    }
}
