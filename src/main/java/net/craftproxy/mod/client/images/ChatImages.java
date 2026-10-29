package net.craftproxy.mod.client.images;

import com.mojang.blaze3d.platform.NativeImage;
import net.craftproxy.mod.CraftProxy;
import net.craftproxy.mod.client.compat.Textures;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chat images as GPU textures: downloaded (or taken from local bytes), decoded and scaled on a
 * background thread, uploaded on the render thread and cached with an LRU pixel budget.
 * <p>
 * Textures use nearest filtering, so every image is scaled to roughly the size it is shown at
 * ({@code maxSide} in real pixels) instead of being minified by the GPU.
 * {@link #remote} and {@link #local} must be called on the render thread.
 */
public final class ChatImages {
    /** Upper bound for one texture's longest side. */
    public static final int MAX_SIDE = 2048;
    /** Cached texture pixels before unused ones are evicted (about 96 MB of RGBA). */
    private static final long PIXEL_BUDGET = 24L * 1024 * 1024;
    private static final int MAX_DOWNLOAD_BYTES = 16 * 1024 * 1024;
    /** Raw downloads kept so thumbnail and full view do not download twice. */
    private static final int RAW_CACHE_BYTES = 32 * 1024 * 1024;
    private static final long RETRY_FAILED_MS = 30_000L;
    private static final long KEEP_VISIBLE_MS = 2_000L;

    public enum Status {LOADING, READY, FAILED}

    /** State of one image at one size; fields are written on the render thread only. */
    public static final class Handle {
        private final String key;
        private volatile Status status = Status.LOADING;
        private Identifier texture;
        private int width;
        private int height;
        private int sourceWidth;
        private int sourceHeight;
        private long lastUsed;
        private long failedAt;

        private Handle(String key) {
            this.key = key;
        }

        public Status status() {
            return status;
        }

        public boolean ready() {
            return status == Status.READY;
        }

        /** Registered texture, only valid while {@link #ready()}. */
        public Identifier texture() {
            return texture;
        }

        /** Texture size in pixels. */
        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        /** Size of the original image, e.g. for the aspect ratio. */
        public int sourceWidth() {
            return sourceWidth;
        }

        public int sourceHeight() {
            return sourceHeight;
        }
    }

    private static final Map<String, Handle> CACHE = new LinkedHashMap<>(32, 0.75F, true);
    private static final Map<String, byte[]> RAW = new LinkedHashMap<>(16, 0.75F, true);
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "CraftProxyImages");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static long cachedPixels;

    private ChatImages() {
    }

    /** Image from a public URL, scaled to fit {@code maxSide} pixels. */
    public static Handle remote(String url, int maxSide) {
        return request(url, maxSide, () -> download(url));
    }

    /** The cached handle for a remote image without starting a download, or {@code null}. */
    public static Handle peek(String url, int maxSide) {
        return CACHE.get(url + "@" + bucket(maxSide));
    }

    private static int bucket(int maxSide) {
        // Few distinct sizes keep the cache small while windows and GUI scales change.
        return Math.min(MAX_SIDE, Math.max(64, (maxSide + 63) / 64 * 64));
    }

    /** Image from bytes already in memory (e.g. a pending attachment), identified by {@code key}. */
    public static Handle local(String key, byte[] data, int maxSide) {
        return request("local:" + key, maxSide, () -> data);
    }

    /** Frees every cached size of an image, e.g. when a pending attachment was removed or sent. */
    public static void forget(String key) {
        String prefix = "local:" + key + "@";
        Iterator<Map.Entry<String, Handle>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Handle handle = it.next().getValue();
            if (handle.key.startsWith(prefix)) {
                it.remove();
                drop(handle);
            }
        }
    }

    private static Handle request(String source, int maxSide, Callable<byte[]> bytes) {
        int bucket = bucket(maxSide);
        String key = source + "@" + bucket;
        long now = System.currentTimeMillis();

        Handle handle = CACHE.get(key);
        if (handle != null && handle.status == Status.FAILED && now - handle.failedAt > RETRY_FAILED_MS) {
            CACHE.remove(key);
            handle = null;
        }
        if (handle == null) {
            handle = new Handle(key);
            CACHE.put(key, handle);
            load(handle, bucket, bytes);
        }
        handle.lastUsed = now;
        evict(now);
        return handle;
    }

    private static void load(Handle handle, int maxSide, Callable<byte[]> bytes) {
        POOL.execute(() -> {
            NativeImage image = null;
            int width = 0, height = 0, sourceW = 0, sourceH = 0;
            try {
                ImageCodec.Decoded decoded = ImageCodec.decode(bytes.call(), maxSide);
                width = decoded.image().getWidth();
                height = decoded.image().getHeight();
                sourceW = decoded.sourceWidth();
                sourceH = decoded.sourceHeight();
                // NativeImage only reads PNG on newer versions; the round trip keeps one code path.
                image = NativeImage.read(ImageCodec.toPng(decoded.image()));
            } catch (Throwable e) {
                // Expected for e.g. WebP previews, which ImageIO cannot decode.
            }

            NativeImage result = image;
            int w = width, h = height, sw = sourceW, sh = sourceH;
            Minecraft.getInstance().execute(() -> finish(handle, result, w, h, sw, sh));
        });
    }

    private static void finish(Handle handle, NativeImage image, int width, int height, int sourceW, int sourceH) {
        if (image == null) {
            handle.status = Status.FAILED;
            handle.failedAt = System.currentTimeMillis();
            return;
        }
        if (CACHE.get(handle.key) != handle) {
            // Evicted or forgotten while loading.
            image.close();
            return;
        }
        Identifier id = CraftProxy.id("chat_image/" + IDS.incrementAndGet());
        Textures.register(id, image, "craftproxy_chat_image");
        handle.texture = id;
        handle.width = width;
        handle.height = height;
        handle.sourceWidth = sourceW;
        handle.sourceHeight = sourceH;
        handle.status = Status.READY;
        cachedPixels += (long) width * height;
        evict(System.currentTimeMillis());
    }

    /** Drops least recently used textures over the budget, but never ones drawn in the last moments. */
    private static void evict(long now) {
        if (cachedPixels <= PIXEL_BUDGET) return;
        Iterator<Map.Entry<String, Handle>> it = CACHE.entrySet().iterator();
        while (cachedPixels > PIXEL_BUDGET && it.hasNext()) {
            Handle handle = it.next().getValue();
            if (now - handle.lastUsed < KEEP_VISIBLE_MS) break;
            it.remove();
            drop(handle);
        }
    }

    private static void drop(Handle handle) {
        if (handle.status == Status.READY && handle.texture != null) {
            Textures.release(handle.texture);
            cachedPixels -= (long) handle.width * handle.height;
        }
        handle.status = Status.FAILED;
        handle.texture = null;
    }

    private static byte[] download(String url) throws IOException, InterruptedException {
        synchronized (RAW) {
            byte[] cached = RAW.get(url);
            if (cached != null) return cached;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        byte[] data;
        try (InputStream in = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }
            data = in.readNBytes(MAX_DOWNLOAD_BYTES + 1);
        }
        if (data.length > MAX_DOWNLOAD_BYTES) throw new IOException("Image too large");

        synchronized (RAW) {
            RAW.put(url, data);
            int total = 0;
            for (byte[] value : RAW.values()) total += value.length;
            Iterator<byte[]> it = RAW.values().iterator();
            while (total > RAW_CACHE_BYTES && it.hasNext()) {
                total -= it.next().length;
                it.remove();
            }
        }
        return data;
    }
}
