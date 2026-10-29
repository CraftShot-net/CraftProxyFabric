package net.craftproxy.mod.client.compat;

import net.minecraft.resources.Identifier;

/**
 * Version-independent view of a player skin. Minecraft's own skin type changed
 * several times (a bare texture location in 1.20.1, PlayerSkin since 1.20.2,
 * ClientAsset based PlayerSkin since 1.21.9), so the mod only passes this record around.
 *
 * @param texture full texture location, usable for blitting and model rendering
 * @param slim    whether the skin uses the slim (Alex) arm model
 */
public record SkinData(Identifier texture, boolean slim) {
}
