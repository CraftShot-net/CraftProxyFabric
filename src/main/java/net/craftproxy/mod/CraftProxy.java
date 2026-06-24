package net.craftproxy.mod;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

public class CraftProxy implements ModInitializer {
	public static final String MOD_ID = "craftproxy";

	@Override
	public void onInitialize() {

	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
