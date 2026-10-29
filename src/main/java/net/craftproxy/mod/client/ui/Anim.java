package net.craftproxy.mod.client.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * A value that eases towards a target with frame-rate independent exponential smoothing.
 * Every instance measures the time since its own last update, so it can be used from any
 * drawing code without a shared frame clock.
 */
public final class Anim {
    private float value;
    private long lastNanos = System.nanoTime();

    public Anim(float initial) {
        this.value = initial;
    }

    public Anim() {
        this(0.0F);
    }

    public static float easeOutCubic(float t) {
        t = Theme.clamp01(t);
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    /** Moves the value towards {@code target}; {@code speed} is roughly "1/seconds to settle". */
    public float update(float target, float speed) {
        long now = System.nanoTime();
        float seconds = Math.min(0.1F, Math.max(0.0F, (now - lastNanos) / 1_000_000_000.0F));
        lastNanos = now;
        float k = 1.0F - (float) Math.exp(-speed * seconds);
        value += (target - value) * k;
        if (Math.abs(target - value) < 0.001F) value = target;
        return value;
    }

    /** Eases towards 1 when {@code on}, otherwise towards 0. */
    public float update(boolean on) {
        return update(on ? 1.0F : 0.0F, 18.0F);
    }

    public float get() {
        return value;
    }

    public void set(float value) {
        this.value = value;
    }

    /** One {@link Anim} per key, e.g. hover states of list rows. */
    public static final class Group<K> {
        private final Map<K, Anim> anims = new HashMap<>();

        public float update(K key, boolean on) {
            return anims.computeIfAbsent(key, k -> new Anim()).update(on);
        }

        public void clear() {
            anims.clear();
        }
    }
}
