package net.craftproxy.mod.client.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Runtime textures from downloaded or generated images. Render thread only.
 */
public final class Textures {
    private Textures() {
    }

    /** Uploads {@code image} and registers it under {@code id}; the texture owns the image afterwards. */
    public static void register(Identifier id, NativeImage image, String debugName) {
        //? if >=1.21.5 {
        DynamicTexture texture = new DynamicTexture(() -> debugName, image);
        //?} else {
        /*DynamicTexture texture = new DynamicTexture(image);
        *///?}
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }

    /** Frees the GPU texture and its pixels. */
    public static void release(Identifier id) {
        Minecraft.getInstance().getTextureManager().release(id);
    }
}
