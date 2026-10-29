package net.craftproxy.mod.client.managers;

import net.craftproxy.mod.client.dto.MyCosmeticsResponseDto;
import net.craftproxy.mod.client.dto.MyCosmeticsResponseDto.PlayerCosmeticEntryDto;
import net.craftproxy.mod.client.dto.ToggleEquipResponseDto;
import net.craftproxy.mod.client.utils.HttpUtils;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class WardrobeManager {

    private static WardrobeManager instance;
    private final HttpUtils api;

    private final CopyOnWriteArrayList<PlayerCosmeticEntryDto> cachedCosmetics = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<List<PlayerCosmeticEntryDto>>> cosmeticsListeners = new CopyOnWriteArrayList<>();

    public WardrobeManager(String baseUrl, String token) {
        this.api = new HttpUtils(baseUrl).authToken(token);
    }

    public static void init(String baseUrl, String token) {
        instance = new WardrobeManager(baseUrl, token);
    }

    public static WardrobeManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("WardrobeManager not initialized. Call WardrobeManager.init(baseUrl, token) first.");
        }
        return instance;
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.cachedCosmetics.clear();
            instance.cosmeticsListeners.clear();
            instance = null;
        }
    }

    public List<PlayerCosmeticEntryDto> getCachedCosmetics() {
        return Collections.unmodifiableList(cachedCosmetics);
    }

    public void addCosmeticsListener(Consumer<List<PlayerCosmeticEntryDto>> listener) {
        cosmeticsListeners.add(listener);
    }

    public void removeCosmeticsListener(Consumer<List<PlayerCosmeticEntryDto>> listener) {
        cosmeticsListeners.remove(listener);
    }

    private void notifyListeners() {
        List<PlayerCosmeticEntryDto> snapshot = List.copyOf(cachedCosmetics);
        for (Consumer<List<PlayerCosmeticEntryDto>> listener : cosmeticsListeners) {
            listener.accept(snapshot);
        }
    }

    public CompletableFuture<List<PlayerCosmeticEntryDto>> fetchMyCosmetics() {
        return CompletableFuture.supplyAsync(() -> {
            HttpUtils.Response res = api.get("/api/my-cosmetics");
            if (res.isSuccessful()) {
                MyCosmeticsResponseDto responseDto = res.as(MyCosmeticsResponseDto.class);
                cachedCosmetics.clear();
                if (responseDto.cosmetics() != null) {
                    cachedCosmetics.addAll(responseDto.cosmetics());
                    for (PlayerCosmeticEntryDto item : responseDto.cosmetics()) {
                        CapeManager.preloadTexture(item.cosmeticId(), item.textureUrl());
                    }
                }
                syncLocalCape();
                notifyListeners();
                return getCachedCosmetics();
            }
            throw new RuntimeException("Failed to fetch player cosmetics: " + res.status());
        });
    }

    public CompletableFuture<ToggleEquipResponseDto> toggleEquip(String cosmeticId) {
        return CompletableFuture.supplyAsync(() -> {
            HttpUtils.Response res = api.post("/api/my-cosmetics/" + cosmeticId + "/toggle", new Object());
            if (res.isSuccessful()) {
                ToggleEquipResponseDto toggleDto = res.as(ToggleEquipResponseDto.class);
                // Update cached list
                for (int i = 0; i < cachedCosmetics.size(); i++) {
                    PlayerCosmeticEntryDto entry = cachedCosmetics.get(i);
                    if (entry.cosmeticId().equals(cosmeticId)) {
                        cachedCosmetics.set(i, new PlayerCosmeticEntryDto(entry.id(), entry.cosmeticId(), entry.type(), toggleDto.equipped(), entry.textureUrl()));
                    } else if (toggleDto.equipped()) {
                        // If one item of the same type is equipped, unequip any other of the same type
                        PlayerCosmeticEntryDto targetEntry = cachedCosmetics.stream()
                                .filter(e -> e.cosmeticId().equals(cosmeticId))
                                .findFirst()
                                .orElse(null);
                        if (targetEntry != null && entry.type().equals(targetEntry.type())) {
                            cachedCosmetics.set(i, new PlayerCosmeticEntryDto(entry.id(), entry.cosmeticId(), entry.type(), false, entry.textureUrl()));
                        }
                    }
                }
                syncLocalCape();
                notifyListeners();
                return toggleDto;
            }
            throw new RuntimeException("Failed to toggle cosmetic: " + res.status());
        });
    }

    private void syncLocalCape() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null || mc.getUser().getProfileId() == null) return;
        UUID myUuid = mc.getUser().getProfileId();

        PlayerCosmeticEntryDto equippedCape = cachedCosmetics.stream()
                .filter(c -> "cape".equalsIgnoreCase(c.type()) && c.equipped())
                .findFirst()
                .orElse(null);

        if (equippedCape != null) {
            CapeManager.updateLocalPlayerCape(myUuid, equippedCape.cosmeticId(), equippedCape.textureUrl());
        } else {
            CapeManager.updateLocalPlayerCape(myUuid, null, null);
        }
    }
}
