package net.craftproxy.mod.client.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.craftproxy.mod.client.compat.SkinData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
//? if >=1.21.9 {
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
//?} else if >=1.20.2 {
/*import net.minecraft.client.resources.PlayerSkin;
*///?} else {
/*import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.resources.Identifier;
import java.util.Map;
*///?}
//? if >=26.3 {
import com.mojang.authlib.services.ProfileResult;
//?} else if >=1.20.2 {
/*import com.mojang.authlib.yggdrasil.ProfileResult;
*///?}

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class SkinManager {

    private static java.util.Map<String, Supplier<SkinData>> SKIN_CACHE;

    public static Supplier<SkinData> getSkinAsyncPublic(String username) {
        return getSkinAsync(username);
    }

    private static Supplier<SkinData> getSkinAsync(String username) {
        if (SKIN_CACHE == null) SKIN_CACHE = new HashMap<>();

        return SKIN_CACHE.computeIfAbsent(username, name -> {
            class AsyncSkinSupplier implements Supplier<SkinData> {
                private Supplier<SkinData> currentSupplier;

                public AsyncSkinSupplier() {
                    UUID offlineUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
                    this.currentSupplier = lookup(new GameProfile(offlineUUID, name));

                    CompletableFuture.runAsync(() -> {
                        try {
                            UUID realUUID = fetchUuid(name).get();
                            if (realUUID == null) return;
                            GameProfile profile = fetchProfile(realUUID);
                            if (profile != null) {
                                Minecraft.getInstance().execute(() -> this.currentSupplier = lookup(profile));
                            }
                        } catch (Exception ignored) {
                        }
                    });
                }

                @Override
                public SkinData get() {
                    return currentSupplier.get();
                }
            }
            return new AsyncSkinSupplier();
        });
    }

    /** Skin of the logged-in player, also when not in a world. */
    public static SkinData ofLocalPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            //? if >=1.20.2 {
            return of(mc.player.getSkin());
            //?} else {
            /*return new SkinData(mc.player.getSkinTextureLocation(), "slim".equals(mc.player.getModelName()));
            *///?}
        }
        return defaultFor(mc.getUser().getProfileId());
    }

    public static SkinData defaultFor(UUID uuid) {
        //? if >=1.20.2 {
        return of(DefaultPlayerSkin.get(uuid));
        //?} else {
        /*return new SkinData(DefaultPlayerSkin.getDefaultSkin(uuid), "slim".equals(DefaultPlayerSkin.getSkinModelName(uuid)));
        *///?}
    }

    //? if >=1.21.9 {
    public static SkinData of(PlayerSkin skin) {
        return new SkinData(skin.body().texturePath(), skin.model() == PlayerModelType.SLIM);
    }
    //?} else if >=1.20.2 {
    /*public static SkinData of(PlayerSkin skin) {
        return new SkinData(skin.texture(), skin.model() == PlayerSkin.Model.SLIM);
    }
    *///?}

    private static Supplier<SkinData> lookup(GameProfile profile) {
        //? if >=1.21.9 {
        Supplier<PlayerSkin> skin = Minecraft.getInstance().getSkinManager().createLookup(profile, false);
        return () -> of(skin.get());
        //?} else if >=1.20.2 {
        /*Supplier<PlayerSkin> skin = Minecraft.getInstance().getSkinManager().lookupInsecure(profile);
        return () -> of(skin.get());
        *///?} else {
        /*return () -> {
            net.minecraft.client.resources.SkinManager skins = Minecraft.getInstance().getSkinManager();
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = skins.getInsecureSkinInformation(profile);
            MinecraftProfileTexture skin = textures.get(MinecraftProfileTexture.Type.SKIN);
            if (skin == null) return defaultFor(profile.getId());
            Identifier texture = skins.registerTexture(skin, MinecraftProfileTexture.Type.SKIN);
            return new SkinData(texture, "slim".equals(skin.getMetadata("model")));
        };
        *///?}
    }

    private static GameProfile fetchProfile(UUID uuid) {
        //? if >=1.21.9 {
        ProfileResult result = Minecraft.getInstance().services().sessionService().fetchProfile(uuid, true);
        return result != null ? result.profile() : null;
        //?} else if >=1.20.2 {
        /*ProfileResult result = Minecraft.getInstance().getMinecraftSessionService().fetchProfile(uuid, true);
        return result != null ? result.profile() : null;
        *///?} else {
        /*return Minecraft.getInstance().getMinecraftSessionService().fillProfileProperties(new GameProfile(uuid, null), true);
        *///?}
    }

    public static CompletableFuture<UUID> fetchUuid(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username)).GET().build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
                    String id = jsonObject.get("id").getAsString();

                    String formattedUuid = id.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");

                    return UUID.fromString(formattedUuid);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        });
    }

}
