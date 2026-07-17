package net.craftproxy.mod.client.managers;

import com.mojang.blaze3d.platform.NativeImage;
import net.craftproxy.mod.client.dto.CosmeticEntryDto;
import net.craftproxy.mod.client.dto.CosmeticsBatchResponseDto;
import net.craftproxy.mod.client.utils.HttpUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CapeManager {

    private static final String BASE_URL = "https://backend.craftproxy.net";

    // uuid -> cosmeticId, or explicit "NONE" sentinel for "we checked, they have no cape"
    private static final Map<UUID, String> EQUIPPED_CAPES = new ConcurrentHashMap<>();
    private static final String NONE = "__NONE__";

    private static final Map<String, String> TEXTURE_URLS = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> LOADED_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> DOWNLOAD_IN_PROGRESS = new ConcurrentHashMap<>();

    // UUIDs we haven't looked up yet, waiting for the next batch flush
    private static final Set<UUID> PENDING_LOOKUP = ConcurrentHashMap.newKeySet();

    private static final HttpClient PUBLIC_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();

    static {
        // Flush pending UUID lookups every 2 seconds in one batch call.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "craftproxy-cape-batch");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(CapeManager::flushPendingLookups, 2, 2, TimeUnit.SECONDS);
    }

    /**
     * Called from CustomCapeLayer.submit() every frame -- must never block.
     */
    public static Identifier getCapeFor(int entityId) {
        assert Minecraft.getInstance().level != null;
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (!(entity instanceof Player player)) return null;

        UUID uuid = player.getUUID();
        String cosmeticId = EQUIPPED_CAPES.get(uuid);

        if (cosmeticId == null) {
            // Never looked up -- queue it, result comes on a later frame.
            PENDING_LOOKUP.add(uuid);
            return null;
        }
        if (NONE.equals(cosmeticId)) {
            return null; // confirmed: this player has no cape
        }

        Identifier cached = LOADED_TEXTURES.get(cosmeticId);
        if (cached != null) return cached;

        String url = TEXTURE_URLS.get(cosmeticId);
        if (url == null) return null;

        if (DOWNLOAD_IN_PROGRESS.putIfAbsent(cosmeticId, Boolean.TRUE) == null) {
            new Thread(() -> downloadAndRegister(cosmeticId, url), "craftproxy-cape-download-" + cosmeticId).start();
        }
        return null;
    }

    private static void flushPendingLookups() {
        if (PENDING_LOOKUP.isEmpty()) return;

        List<UUID> batch = new ArrayList<>(PENDING_LOOKUP);
        batch.forEach(PENDING_LOOKUP::remove);

        try {
            String uuidParam = batch.stream().map(UUID::toString).collect(Collectors.joining(","));
            HttpUtils api = new HttpUtils(BASE_URL);
            HttpUtils.Response res = api.get("/api/cosmetics/players?uuids=" + uuidParam);

            if (!res.isSuccessful()) {
                System.err.println("[CraftProxy] Cape batch lookup failed: " + res.status());
                PENDING_LOOKUP.addAll(batch);
                return;
            }

            CosmeticsBatchResponseDto data = res.as(CosmeticsBatchResponseDto.class);

            for (CosmeticEntryDto entry : data.players()) {
                EQUIPPED_CAPES.put(entry.uuid(), entry.cosmeticId());
                TEXTURE_URLS.put(entry.cosmeticId(), entry.textureUrl());
            }

            // Anyone we asked about but who wasn't in the response has no cape --
            // cache that explicitly so we don't ask again every 2 seconds forever.
            for (UUID uuid : batch) {
                EQUIPPED_CAPES.putIfAbsent(uuid, NONE);
            }
        } catch (Exception e) {
            System.err.println("[CraftProxy] Cape batch lookup error: " + e.getMessage());
            PENDING_LOOKUP.addAll(batch);
        }
    }

    private static void downloadAndRegister(String cosmeticId, String textureUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(textureUrl)).timeout(Duration.ofSeconds(10)).GET().build();

            HttpResponse<byte[]> response = PUBLIC_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("[CraftProxy] Texture download failed (" + response.statusCode() + "): " + cosmeticId);
                return;
            }

            NativeImage image = NativeImage.read(response.body());
            Minecraft.getInstance().execute(() -> {
                DynamicTexture texture = new DynamicTexture(() -> "craftproxy_cape_" + cosmeticId, image);
                Identifier id = Identifier.fromNamespaceAndPath("craftproxy", "cosmetic/" + cosmeticId);
                Minecraft.getInstance().getTextureManager().register(id, texture);
                LOADED_TEXTURES.put(cosmeticId, id);
            });
        } catch (Exception e) {
            System.err.println("[CraftProxy] Failed to load cape texture " + cosmeticId + ": " + e.getMessage());
        } finally {
            DOWNLOAD_IN_PROGRESS.remove(cosmeticId);
        }
    }
}