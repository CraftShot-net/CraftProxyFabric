package net.craftproxy.mod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public class CraftProxy implements ModInitializer {
	public static final String MOD_ID = "craftproxy";

	@Override
	public void onInitialize() {

	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}


}
