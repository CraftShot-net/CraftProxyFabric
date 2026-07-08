package net.craftproxy.mod.client.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SkinManager {

    private static final Map<String, Supplier<Identifier>> SKIN_CACHE = new ConcurrentHashMap<>();

    public static Supplier<Identifier> getSkinAsyncPublic(String username) {
        return getSkinAsync(username);
    }

    private static Supplier<Identifier> getSkinAsync(String username) {
        return SKIN_CACHE.computeIfAbsent(username, name -> {
            class AsyncSkinSupplier implements Supplier<Identifier> {
                private volatile Identifier currentTexture;

                public AsyncSkinSupplier() {
                    MinecraftClient client = MinecraftClient.getInstance();
                    UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
                    this.currentTexture = client.getSkinProvider().loadSkin(new GameProfile(offlineUuid, name));

                    fetchUuid(name)
                            .thenAccept(realUuid -> {
                                if (realUuid == null) {
                                    return;
                                }

                                client.execute(() -> client.getSkinProvider().loadSkin(
                                        new GameProfile(realUuid, name),
                                        (type, id, texture) -> {
                                            if (type == MinecraftProfileTexture.Type.SKIN) {
                                                this.currentTexture = id;
                                            }
                                        },
                                        true
                                ));
                            })
                            .exceptionally(ex -> {
                                ex.printStackTrace();
                                return null;
                            });
                }

                @Override
                public Identifier get() {
                    return currentTexture;
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