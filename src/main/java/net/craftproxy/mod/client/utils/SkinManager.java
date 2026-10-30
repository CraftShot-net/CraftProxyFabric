package net.craftproxy.mod.client.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.craftproxy.mod.client.compat.SkinData;
import net.craftproxy.mod.client.config.Endpoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
//? if >=1.21.9 {
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
//?} else if >=1.20.2 {
/*import net.minecraft.client.resources.PlayerSkin;
*///?} else {
/*import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.resources.Identifier;
*///?}

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Resolves player skins by name for the mod's menus and toasts, the way vanilla does it.
 * <p>
 * Known skins come from a disk cache and show immediately (Minecraft keeps the texture files in
 * its own skin cache). Mojang is only asked once for players that were never seen before. A
 * changed skin is picked up when we share a server with that player: the server sends their
 * current profile, which updates the cache, just like the vanilla tab list. Lookups for new
 * players run in a queue (heads on screen first) behind a request budget, so even very long
 * friend lists stay far below Mojang's rate limits; a 429 pauses that host as long as Mojang asks.
 */
public class SkinManager {

    /** A name without a Mojang account is asked again after this. */
    private static final long MISSING_RETRY_MS = TimeUnit.MINUTES.toMillis(30);
    /** How long a changed skin may download while the old one is still shown. */
    private static final long SWAP_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long RETRY_BASE_MS = TimeUnit.SECONDS.toMillis(5);
    private static final long RETRY_MAX_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long DEFAULT_RATE_LIMIT_MS = TimeUnit.SECONDS.toMillis(60);
    /** Mojang requests per host: short bursts of this many, then one per {@link #REQUEST_INTERVAL_MS}. */
    private static final int REQUEST_BURST = 20;
    private static final long REQUEST_INTERVAL_MS = 1000;
    /** How often the current server's player list is compared with the cache. */
    private static final int SERVER_SYNC_TICKS = 40;
    private static final int MAX_PERSISTED = 4096;

    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    // Pending Mojang lookups: heads on screen go to the front, preloading to the back.
    private static final LinkedBlockingDeque<Entry> QUEUE = new LinkedBlockingDeque<>();
    private static final RequestBudget PROFILE_API_BUDGET = new RequestBudget();
    private static final RequestBudget SESSION_API_BUDGET = new RequestBudget();
    private static final ScheduledExecutorService POOL = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "CraftProxySkinCache");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final AtomicBoolean SAVE_SCHEDULED = new AtomicBoolean();
    private static volatile boolean diskLoaded;
    // Mojang rate limits per host; while blocked no request goes out and entries wait.
    private static volatile long profileApiBlockedUntil;
    private static volatile long sessionApiBlockedUntil;
    private static int serverSyncTicks;

    static {
        for (int i = 0; i < 2; i++) {
            Thread worker = new Thread(SkinManager::work, "CraftProxySkins-" + i);
            worker.setDaemon(true);
            worker.start();
        }
    }

    public static Supplier<SkinData> getSkinAsyncPublic(String username) {
        return getSkin(username, null);
    }

    /**
     * Live skin of a player: the cached skin right away, the default skin only for players never
     * seen before until their first lookup finished. Cheap enough to call every frame. A known
     * UUID skips the name to UUID lookup.
     */
    public static Supplier<SkinData> getSkin(String username, String uuidHint) {
        Entry entry = entry(username);
        entry.hint(parseUuid(uuidHint));
        return entry;
    }

    /**
     * Prepares a player's head so menus can show it immediately: a cached skin starts loading its
     * texture, a player never seen before is queued behind everything on screen.
     */
    public static void preload(String username, String uuidHint) {
        if (username == null || username.isBlank()) return;
        Entry entry = entry(username);
        entry.hint(parseUuid(uuidHint));
        entry.ensureLoaded(false);
        Minecraft.getInstance().execute(entry::warm);
    }

    /**
     * Client tick: takes current skins of known players from the server we are on. The server
     * fetched them from Mojang when those players joined, so this costs no request of our own.
     */
    public static void tick(Minecraft client) {
        if (++serverSyncTicks < SERVER_SYNC_TICKS) return;
        serverSyncTicks = 0;
        ClientPacketListener connection = client.getConnection();
        if (connection == null || ENTRIES.isEmpty()) return;
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            String name = profileName(info.getProfile());
            if (name == null) continue;
            Entry entry = ENTRIES.get(name.toLowerCase(Locale.ROOT));
            if (entry != null) entry.syncFrom(info);
        }
    }

    private static Entry entry(String username) {
        if (!diskLoaded) loadFromDisk();
        String name = username == null ? "" : username;
        return ENTRIES.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new Entry(name));
    }

    private static void work() {
        while (true) {
            Entry entry;
            try {
                entry = QUEUE.takeFirst();
            } catch (InterruptedException e) {
                return;
            }
            Throwable error = null;
            try {
                entry.resolve();
            } catch (Throwable t) {
                error = t;
            }
            entry.onDone(error);
        }
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

    private static UUID profileId(GameProfile profile) {
        //? if >=1.21.9 {
        return profile.id();
        //?} else {
        /*return profile.getId();
        *///?}
    }

    private static String profileName(GameProfile profile) {
        //? if >=1.21.9 {
        return profile.name();
        //?} else {
        /*return profile.getName();
        *///?}
    }

    private static Property texturesOf(GameProfile profile) {
        //? if >=1.21.9 {
        java.util.Collection<Property> values = profile.properties().get("textures");
        //?} else {
        /*java.util.Collection<Property> values = profile.getProperties().get("textures");
        *///?}
        return values.isEmpty() ? null : values.iterator().next();
    }

    private static String propertyValue(Property property) {
        //? if >=1.20.2 {
        return property.value();
        //?} else {
        /*return property.getValue();
        *///?}
    }

    private static String propertySignature(Property property) {
        //? if >=1.20.2 {
        return property.signature();
        //?} else {
        /*return property.getSignature();
        *///?}
    }

    private static GameProfile profileWithTextures(UUID uuid, String name, String value, String signature) {
        Property textures = new Property("textures", value, signature);
        //? if >=1.21.9 {
        return new GameProfile(uuid, name, new PropertyMap(ImmutableMultimap.of("textures", textures)));
        //?} else {
        /*GameProfile profile = new GameProfile(uuid, name);
        profile.getProperties().put("textures", textures);
        return profile;
        *///?}
    }

    /** One player's skin state; doubles as the live supplier handed to the UI. */
    private static final class Entry implements Supplier<SkinData> {
        private final String requestedName;
        private final SkinData fallback;
        private volatile UUID uuid;
        private volatile String profileName;
        private volatile String textures;
        private volatile String signature;
        private volatile long fetchedAt;
        private volatile long nextAttemptAt;
        private volatile boolean dirty;
        private volatile boolean queued;
        private volatile boolean queuedUrgent;
        private int failures;
        private volatile Supplier<SkinData> lookup;
        private volatile Supplier<SkinData> previous;
        private volatile long previousUntil;

        Entry(String name) {
            this.requestedName = name;
            this.fallback = defaultFor(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public SkinData get() {
            ensureLoaded(true);
            Supplier<SkinData> current = warm();
            if (current == null) return fallback;
            SkinData skin = current.get();
            Supplier<SkinData> old = previous;
            if (old != null) {
                // A changed skin is still downloading: keep showing the old one instead of Steve.
                boolean loading = uuid != null && skin.texture().equals(defaultFor(uuid).texture());
                if (loading && System.currentTimeMillis() < previousUntil) return old.get();
                previous = null;
            }
            return skin;
        }

        /** Builds the texture lookup (render thread only); this also starts the texture download. */
        Supplier<SkinData> warm() {
            if (dirty || (lookup == null && textures != null)) {
                dirty = false;
                String value = textures;
                if (value != null && uuid != null) {
                    String name = profileName != null ? profileName : requestedName;
                    if (lookup != null) {
                        previous = lookup;
                        previousUntil = System.currentTimeMillis() + SWAP_TIMEOUT_MS;
                    }
                    lookup = SkinManager.lookup(profileWithTextures(uuid, name, value, signature));
                }
            }
            return lookup;
        }

        void hint(UUID hinted) {
            if (hinted == null || hinted.equals(uuid)) return;
            synchronized (this) {
                // A different account under this name (or unknown so far): look it up by UUID.
                boolean replaced = uuid != null;
                uuid = hinted;
                if (replaced) {
                    textures = null;
                    fetchedAt = 0;
                    nextAttemptAt = 0;
                    failures = 0;
                }
            }
        }

        /** Takes the textures the current server sent for this player (render thread). */
        void syncFrom(PlayerInfo info) {
            Property property = texturesOf(info.getProfile());
            if (property == null) return;
            String value = propertyValue(property);
            if (value == null || value.equals(textures)) return;
            uuid = profileId(info.getProfile());
            profileName = profileName(info.getProfile());
            textures = value;
            signature = propertySignature(property);
            fetchedAt = System.currentTimeMillis();
            dirty = true;
            scheduleSave();
        }

        /** Queues a Mojang lookup, but only for a player whose skin we do not know yet. */
        void ensureLoaded(boolean urgent) {
            if (textures != null) return;
            if (queued && (queuedUrgent || !urgent)) return;
            long now = System.currentTimeMillis();
            if (now < nextAttemptAt) return;
            synchronized (this) {
                if (textures != null || now < nextAttemptAt) return;
                if (queued && (queuedUrgent || !urgent || !QUEUE.remove(this))) return;
                queued = true;
                queuedUrgent = urgent;
                if (urgent) QUEUE.addFirst(this);
                else QUEUE.addLast(this);
            }
        }

        private void resolve() {
            if (textures != null) return;
            if (requestedName.isBlank()) throw new ProfileMissing();
            UUID id = uuid;
            boolean fromHint = id != null;
            if (id == null) id = localUuidFor(requestedName);
            if (id == null) id = fetchUuidBlocking(requestedName);
            JsonObject profile = fetchSessionProfile(id);
            if (profile == null && fromHint) {
                // The hinted UUID has no Mojang profile (e.g. an offline account); try the name instead.
                id = fetchUuidBlocking(requestedName);
                profile = fetchSessionProfile(id);
            }
            if (profile == null) throw new ProfileMissing();
            applyProfile(id, profile);
        }

        private void applyProfile(UUID id, JsonObject profile) {
            String value = null;
            String sig = null;
            if (profile.has("properties")) {
                for (JsonElement element : profile.getAsJsonArray("properties")) {
                    JsonObject property = element.getAsJsonObject();
                    if ("textures".equals(property.get("name").getAsString())) {
                        value = property.get("value").getAsString();
                        sig = property.has("signature") ? property.get("signature").getAsString() : null;
                    }
                }
            }
            if (value == null) throw new ProfileMissing();
            uuid = id;
            if (profile.has("name")) profileName = profile.get("name").getAsString();
            signature = sig;
            textures = value;
            fetchedAt = System.currentTimeMillis();
            dirty = true;
            Minecraft.getInstance().execute(this::warm);
        }

        private synchronized void onDone(Throwable error) {
            queued = false;
            queuedUrgent = false;
            long now = System.currentTimeMillis();
            if (error == null) {
                failures = 0;
                nextAttemptAt = 0;
                scheduleSave();
            } else if (error instanceof ProfileMissing) {
                failures = 0;
                nextAttemptAt = now + MISSING_RETRY_MS;
            } else if (error instanceof RateLimited limited) {
                nextAttemptAt = Math.max(limited.until, now + RETRY_BASE_MS);
            } else {
                failures = Math.min(failures + 1, 10);
                nextAttemptAt = now + Math.min(RETRY_MAX_MS, RETRY_BASE_MS << (failures - 1));
            }
        }
    }

    // --- Mojang API -------------------------------------------------------------------------

    private static final class ProfileMissing extends RuntimeException {
        ProfileMissing() {
            super(null, null, false, false);
        }
    }

    private static final class RateLimited extends RuntimeException {
        final long until;

        RateLimited(long until) {
            super(null, null, false, false);
            this.until = until;
        }
    }

    /** Token bucket that keeps us well below Mojang's limits even for very long friend lists. */
    private static final class RequestBudget {
        private double tokens = REQUEST_BURST;
        private long refilledAt = System.currentTimeMillis();

        void acquire() throws InterruptedException {
            long waitMs;
            synchronized (this) {
                long now = System.currentTimeMillis();
                tokens = Math.min(REQUEST_BURST, tokens + (now - refilledAt) / (double) REQUEST_INTERVAL_MS);
                refilledAt = now;
                tokens -= 1;
                waitMs = tokens >= 0 ? 0 : (long) Math.ceil(-tokens * REQUEST_INTERVAL_MS);
            }
            if (waitMs > 0) Thread.sleep(waitMs);
        }
    }

    private static UUID localUuidFor(String name) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getUser().getName().equalsIgnoreCase(name)) return mc.getUser().getProfileId();
        } catch (Exception ignored) {
        }
        return null;
    }

    private static UUID fetchUuidBlocking(String name) {
        HttpResponse<String> response = send(Endpoints.mojangProfile() + "/users/profiles/minecraft/" + name, true);
        if (response.statusCode() == 200) {
            UUID id = parseUuid(JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString());
            if (id != null) return id;
        }
        if (response.statusCode() == 204 || response.statusCode() == 404) throw new ProfileMissing();
        throw new IllegalStateException("Profile lookup failed: " + response.statusCode());
    }

    /** The session server profile with signed textures, or null when the UUID has no profile. */
    private static JsonObject fetchSessionProfile(UUID uuid) {
        String id = uuid.toString().replace("-", "");
        HttpResponse<String> response = send(Endpoints.mojangSession() + "/session/minecraft/profile/" + id + "?unsigned=false", false);
        if (response.statusCode() == 200 && !response.body().isBlank()) {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        }
        if (response.statusCode() == 204 || response.statusCode() == 404 || response.statusCode() == 400) return null;
        throw new IllegalStateException("Session profile failed: " + response.statusCode());
    }

    private static HttpResponse<String> send(String url, boolean profileApi) {
        long blockedUntil = profileApi ? profileApiBlockedUntil : sessionApiBlockedUntil;
        if (System.currentTimeMillis() < blockedUntil) throw new RateLimited(blockedUntil);
        try {
            (profileApi ? PROFILE_API_BUDGET : SESSION_API_BUDGET).acquire();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                long until = System.currentTimeMillis() + retryAfterMs(response);
                if (profileApi) profileApiBlockedUntil = until;
                else sessionApiBlockedUntil = until;
                throw new RateLimited(until);
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long retryAfterMs(HttpResponse<?> response) {
        try {
            return response.headers().firstValue("Retry-After")
                    .map(value -> TimeUnit.SECONDS.toMillis(Long.parseLong(value.trim())))
                    .orElse(DEFAULT_RATE_LIMIT_MS);
        } catch (NumberFormatException e) {
            return DEFAULT_RATE_LIMIT_MS;
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.length() == 32) {
            value = value.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})", "$1-$2-$3-$4-$5");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static CompletableFuture<UUID> fetchUuid(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetchUuidBlocking(username);
            } catch (RuntimeException e) {
                return null;
            }
        }, POOL);
    }

    // --- Disk cache -------------------------------------------------------------------------

    private record StoredProfile(String name, String uuid, String profileName, String textures, String signature, long fetchedAt) {
    }

    private record StoredFile(List<StoredProfile> profiles) {
    }

    private static Path cacheFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("craftproxy").resolve("skin-profiles.json");
    }

    private static synchronized void loadFromDisk() {
        if (diskLoaded) return;
        diskLoaded = true;
        try {
            Path file = cacheFile();
            if (!Files.isRegularFile(file)) return;
            StoredFile stored;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                stored = GSON.fromJson(reader, StoredFile.class);
            }
            if (stored == null || stored.profiles() == null) return;
            for (StoredProfile profile : stored.profiles()) {
                UUID id = parseUuid(profile.uuid());
                if (profile.name() == null || id == null || profile.textures() == null) continue;
                Entry entry = new Entry(profile.name());
                entry.uuid = id;
                entry.profileName = profile.profileName();
                entry.textures = profile.textures();
                entry.signature = profile.signature();
                entry.fetchedAt = profile.fetchedAt();
                ENTRIES.putIfAbsent(profile.name().toLowerCase(Locale.ROOT), entry);
            }
        } catch (Exception ignored) {
            // A broken cache only costs a refetch.
        }
    }

    private static void scheduleSave() {
        if (SAVE_SCHEDULED.compareAndSet(false, true)) {
            POOL.schedule(SkinManager::saveToDisk, 3, TimeUnit.SECONDS);
        }
    }

    private static void saveToDisk() {
        SAVE_SCHEDULED.set(false);
        List<StoredProfile> profiles = new ArrayList<>();
        for (Entry entry : ENTRIES.values()) {
            if (entry.textures == null || entry.uuid == null) continue;
            profiles.add(new StoredProfile(entry.requestedName, entry.uuid.toString(), entry.profileName,
                    entry.textures, entry.signature, entry.fetchedAt));
        }
        profiles.sort(Comparator.comparingLong(StoredProfile::fetchedAt).reversed());
        if (profiles.size() > MAX_PERSISTED) profiles = profiles.subList(0, MAX_PERSISTED);
        try {
            Path file = cacheFile();
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(new StoredFile(profiles), writer);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
        }
    }
}
