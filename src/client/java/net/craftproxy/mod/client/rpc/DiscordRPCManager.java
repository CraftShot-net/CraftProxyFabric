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
     * Updates the Rich Presence.
     *
     * @param details        first line, e.g. "Minecraft 26.2"
     * @param state          second line, e.g. "In World" (player count gets appended automatically)
     * @param currentPlayers current player count, or 0 to hide the party info
     * @param maxPlayers     max player count
     */
    public static void updatePresence(String details, String state, int currentPlayers, int maxPlayers) {
        if (!started) {
            return;
        }

        presence.setDetails(details);
        presence.setState(state);
        presence.setLargeImage("logo", "CraftProxy Mod");

        if (maxPlayers > 0) {
            presence.setParty("craftproxy-party", currentPlayers, maxPlayers);
        } else {
            presence.clearParty();
        }

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
    }

    public static void updateDiscordPresence(Minecraft mc) {
        String version = "Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().name();

        if (mc.level == null) {
            updatePresence(version, "In menu", 0, 0);
            return;
        }

        if (mc.getSingleplayerServer() != null) {
            int current = mc.getSingleplayerServer().getPlayerCount();
            int max = mc.getSingleplayerServer().getMaxPlayers();
            updatePresence(version, "In World", current, max);
        } else if (mc.getConnection() != null) {
            int current = mc.getConnection().getOnlinePlayers().size();
            updatePresence(version, "In World", current, current);
        } else {
            updatePresence(version, "In World", 0, 0);
        }
    }
}