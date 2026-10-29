package net.craftproxy.mod.client.rpc;

import net.minecraft.client.Minecraft;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordRPCManager {

    private static final long CLIENT_ID = 1530008033923436584L;

    private static final int MAX_INIT_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 750L;

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CraftProxy-DiscordRPC");
        t.setDaemon(true);
        return t;
    });

    private static final PartyRichPresence presence = new PartyRichPresence();
    private static volatile boolean started = false;
    private static volatile boolean initCalled = false;

    // Identifies the current "context" (menu / singleplayer / a specific server) so we
    // only reset the elapsed-time timestamp when the player actually moves to a new
    // context, not on every player-count update within the same one.
    private static volatile String currentContextKey = null;

    private DiscordRPCManager() {
    }

    /**
     * Starts the Discord IPC connection asynchronously. Safe to call from the main thread —
     * it never blocks. Idempotent: calling this more than once is a no-op.
     */
    public static synchronized void init() {
        if (initCalled) {
            return;
        }
        initCalled = true;

        CraftProxyDiscordIPC.setOnError((code, message) -> System.out.println("[CraftProxy] Discord IPC error " + code + ": " + message));

        SCHEDULER.execute(() -> attemptConnect(1));
    }

    private static void attemptConnect(int attempt) {
        if (started) {
            return;
        }

        boolean ok = CraftProxyDiscordIPC.start(CLIENT_ID, () -> System.out.println("[CraftProxy] Discord IPC ready."));

        if (ok) {
            started = true;
            presence.setStart(System.currentTimeMillis() / 1000L);
            return;
        }

        if (attempt < MAX_INIT_ATTEMPTS) {
            SCHEDULER.schedule(() -> attemptConnect(attempt + 1), RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
        } else {
            System.out.println("[CraftProxy] Could not connect to Discord IPC after " + MAX_INIT_ATTEMPTS + " attempts.");
        }
    }

    /**
     * Updates the Rich Presence, resetting the elapsed-time timer whenever {@code contextKey}
     * differs from the previous call — e.g. pass "menu", "singleplayer", or the server IP.
     * Player-count-only updates within the same context (someone joins/leaves) won't reset it,
     * since the key stays the same.
     *
     * @param contextKey     stable identifier for the current context (menu/world/server); the
     *                       timer resets only when this changes from the last call, or null to
     *                       reset on every call (matches the previous no-tracking behavior)
     */
    public static void updatePresence(String details, String state, int currentPlayers, int maxPlayers, String contextKey) {
        if (!started) {
            return;
        }

        if (contextKey == null || !contextKey.equals(currentContextKey)) {
            currentContextKey = contextKey;
            presence.setStart(System.currentTimeMillis() / 1000L);
        }

        presence.setDetails(details);
        presence.setState(state);
        presence.setLargeImage("logo", "CraftProxy Mod");

        if (maxPlayers > 0) {
            presence.setParty("craftproxy-party", currentPlayers, maxPlayers);
        } else {
            presence.clearParty();
        }

        presence.setButtons(
                "CraftProxy Website", "https://craftproxy.net?utm_source=discord-rpc",
                "Join our Discord", "https://discord.gg/HP9yR8A2m9"
        );

        CraftProxyDiscordIPC.setActivity(presence);
    }

    /**
     * Stops the Discord IPC connection. Call on shutdown / when the module is disabled.
     */
    public static void shutdown() {
        if (!started) {
            return;
        }

        CraftProxyDiscordIPC.stop();
        started = false;
        initCalled = false;
        currentContextKey = null;
    }

    private static final String CRAFTPROXY_DOMAIN_SUFFIX = ".craftproxy.net";

    public static void updateDiscordPresence(Minecraft mc) {
        String version = "Minecraft " + net.craftproxy.mod.client.compat.ClientCompat.gameVersionName();

        if (mc.level == null) {
            updatePresence(version, "In menu", 0, 0, "menu");
            return;
        }

        if (mc.getSingleplayerServer() != null) {
            int current = mc.getSingleplayerServer().getPlayerCount();
            int max = mc.getSingleplayerServer().getMaxPlayers();
            // Singleplayer worlds don't have a stable name/id readily at hand here, so this
            // key just distinguishes "singleplayer" from every other context. If you want the
            // timer to also reset between different singleplayer worlds, swap in the world's
            // save name/folder instead.
            updatePresence(version, "In World", current, max, "singleplayer");
            return;
        }

        if (mc.getConnection() != null) {
            int max = mc.getConnection().getOnlinePlayers().size();

            net.minecraft.client.multiplayer.ServerData server = mc.getCurrentServer();
            String ip = server != null ? server.ip : null;

            boolean isOwnNetwork = ip != null && ip.toLowerCase().endsWith(CRAFTPROXY_DOMAIN_SUFFIX);

            if (isOwnNetwork || ip == null) {
                // Our own hosted/tunneled worlds (via /host) feel like singleplayer to the player,
                // so treat them the same. Unknown IP (e.g. no ServerData available) also falls back here.
                // Use the actual ip (when known) as the context key so switching between two of our
                // own hosted worlds still resets the timer; fall back to a fixed key when ip is null.
                updatePresence(version, "In World", max, max, "own-network:" + (ip != null ? ip : "unknown"));
            } else {
                // Any other, actually global/public server: just show the IP.
                // The IP itself is a perfectly stable context key here.
                updatePresence(version, ip, 0, 0, "public:" + ip);
            }
            return;
        }

        updatePresence(version, "In World", 0, 0, "unknown-connection");
    }
}