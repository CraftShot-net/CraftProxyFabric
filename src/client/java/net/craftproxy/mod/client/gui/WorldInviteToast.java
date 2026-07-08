package net.craftproxy.mod.client.gui;


import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

public class WorldInviteToast implements Toast {

    private static final long DISPLAY_TIME_MS = 5000L;

    private final String hostName;
    private final String worldName;
    private final Supplier<Identifier> skinSupplier;

    private WorldInviteToast(String hostName, String worldName, Supplier<Identifier> skinSupplier) {
        this.hostName = hostName;
        this.worldName = worldName;
        this.skinSupplier = skinSupplier;
    }

    public static void show(String hostName, String worldName, Supplier<Identifier> skinSupplier) {
        MinecraftClient.getInstance().getToastManager().add(
                new WorldInviteToast(hostName, worldName, skinSupplier)
        );
    }

    @Override
    public Visibility draw(DrawContext context, ToastManager manager, long startTime) {
        int w = this.getWidth();
        int h = this.getHeight();
        var font = MinecraftClient.getInstance().textRenderer;

        context.fill(0, 0, w, h, 0xEE0D0D0D);
        context.fill(0, 0, w, 2, 0xFF55AAFF);
        context.fill(0, 0, 3, h, 0xFF55AAFF);

        int skinX = 8;
        int skinY = (h - 16) / 2;
        Identifier skinId = getFixedSkinTexture(skinSupplier);
        PlayerSkinDrawer.draw(context, skinId, skinX, skinY, 16);

        int textX = skinX + 16 + 5;
        int textY1 = 5;
        int textY2 = 5 + font.fontHeight + 2;

        String title = Text.translatable("screen.craftproxy.friends.toast.world_invite.title").getString();
        context.drawText(font, title, textX, textY1, 0xFF55AAFF, false);

        String excerpt = Text.translatable("screen.craftproxy.friends.toast.world_invite.message", hostName, worldName).getString();
        int maxW = w - textX - 6;
        if (font.getWidth(excerpt) > maxW) {
            while (!excerpt.isEmpty() && font.getWidth(excerpt + "…") > maxW) {
                excerpt = excerpt.substring(0, excerpt.length() - 1);
            }
            excerpt += "…";
        }
        context.drawText(font, excerpt, textX, textY2, 0xFFCCCCCC, false);

        return startTime >= DISPLAY_TIME_MS ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public int getWidth() {
        return 200;
    }

    @Override
    public int getHeight() {
        return 32;
    }

    private Identifier getFixedSkinTexture(Supplier<Identifier> skinSupplier) {
        Identifier skinId = skinSupplier.get();
        String path = skinId.getPath();
        if (!path.startsWith("textures/") && !path.startsWith("skins/")) {
            return Identifier.of("minecraft", "textures/" + path + ".png");
        }
        return skinId;
    }
}
