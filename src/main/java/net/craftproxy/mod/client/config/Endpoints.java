package net.craftproxy.mod.client.config;

/**
 * Single source of truth for every external endpoint the mod talks to.
 * <p>
 * Defaults point at production. For local development, override any value without
 * recompiling, either with a JVM system property or an environment variable:
 *
 * <pre>
 *   ./gradlew runActiveClient -Pcraftshot.url=http://localhost:8000
 *   java -Dcraftshot.url=http://localhost:8000 ...
 *   CRAFTSHOT_URL=http://localhost:8000 ...
 * </pre>
 *
 * The Gradle run config forwards the matching {@code -P} properties as {@code -D} flags.
 */
public final class Endpoints {
    private Endpoints() {
    }

    private static final String DEFAULT_CRAFTPROXY_BACKEND = "https://backend.craftproxy.net";
    private static final String DEFAULT_CRAFTPROXY_WEBSITE = "https://craftproxy.net";
    private static final String DEFAULT_CRAFTSHOT = "https://craftshot.net";
    private static final String DEFAULT_MOJANG_SESSION = "https://sessionserver.mojang.com";
    private static final String DEFAULT_MOJANG_PROFILE = "https://api.mojang.com";
    private static final String DEFAULT_TUNNEL_HOST = "craftproxy.net";
    private static final String DEFAULT_DISCORD = "https://discord.gg/wBj5Jf9pcm";
    private static final String DEFAULT_DISCORD_RPC = "https://discord.gg/HP9yR8A2m9";

    /** CraftProxy REST backend: auth, world invites, cosmetics, presence relay. */
    public static String craftProxyBackend() {
        return url("craftproxy.backend.url", "CRAFTPROXY_BACKEND_URL", DEFAULT_CRAFTPROXY_BACKEND);
    }

    /** Public CraftProxy website (Discord RPC button). */
    public static String craftProxyWebsite() {
        return url("craftproxy.website.url", "CRAFTPROXY_WEBSITE_URL", DEFAULT_CRAFTPROXY_WEBSITE);
    }

    /** CraftShot social core: friends, DMs, presence, Reverb auth. */
    public static String craftShot() {
        return url("craftshot.url", "CRAFTSHOT_URL", DEFAULT_CRAFTSHOT);
    }

    /** Mojang session server ({@code /session/minecraft/join}). */
    public static String mojangSession() {
        return url("craftproxy.mojang.session.url", "CRAFTPROXY_MOJANG_SESSION_URL", DEFAULT_MOJANG_SESSION);
    }

    /** Mojang profile API (username to UUID lookups). */
    public static String mojangProfile() {
        return url("craftproxy.mojang.profile.url", "CRAFTPROXY_MOJANG_PROFILE_URL", DEFAULT_MOJANG_PROFILE);
    }

    /** Hostname of the tunnel server (no scheme). */
    public static String tunnelHost() {
        return raw("craftproxy.tunnel.host", "CRAFTPROXY_TUNNEL_HOST", DEFAULT_TUNNEL_HOST);
    }

    /** Suffix of tunnel hostnames, used to recognise hosted worlds in Discord RPC. */
    public static String tunnelDomainSuffix() {
        return "." + tunnelHost();
    }

    /** Discord invite shown by the mod's social buttons. */
    public static String discord() {
        return url("craftproxy.discord.url", "CRAFTPROXY_DISCORD_URL", DEFAULT_DISCORD);
    }

    /** Discord invite shown in the rich presence buttons. */
    public static String discordRpc() {
        return url("craftproxy.discord.rpc.url", "CRAFTPROXY_DISCORD_RPC_URL", DEFAULT_DISCORD_RPC);
    }

    private static String url(String property, String environment, String fallback) {
        return trimTrailingSlash(raw(property, environment, fallback));
    }

    private static String raw(String property, String environment, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
