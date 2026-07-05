package net.craftproxy.mod.client.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.player.PlayerSkin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class SkinManager {

    private static Map<String, Supplier<PlayerSkin>> SKIN_CACHE;

    public static Supplier<PlayerSkin> getSkinAsyncPublic(String username) {
        return getSkinAsync(username);
    }

    private static Supplier<PlayerSkin> getSkinAsync(String username) {
        if (SKIN_CACHE == null) SKIN_CACHE = new HashMap<>();

        return SKIN_CACHE.computeIfAbsent(username, name -> {
            class AsyncSkinSupplier implements Supplier<PlayerSkin> {
                private Supplier<PlayerSkin> currentSupplier;

                public AsyncSkinSupplier() {
                    UUID offlineUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
                    this.currentSupplier = Minecraft.getInstance().getSkinManager().createLookup(new GameProfile(offlineUUID, name), false);

                    CompletableFuture.runAsync(() -> {
                        try {
                            UUID realUUID = fetchUuid(name).get();
                            if (realUUID == null) return;
                            ProfileResult result = Minecraft.getInstance().services().sessionService().fetchProfile(realUUID, true);
                            if (result != null) {
                                Minecraft.getInstance().execute(() -> this.currentSupplier = Minecraft.getInstance().getSkinManager().createLookup(result.profile(), false));
                            }
                        } catch (Exception ignored) {
                        }
                    });
                }

                @Override
                public PlayerSkin get() {
                    return currentSupplier.get();
                }
            }
            return new AsyncSkinSupplier();
        });
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
