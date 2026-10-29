package net.craftproxy.mod.client.chat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.craftproxy.mod.client.MCTunnelClient;
import net.craftproxy.mod.client.dto.ReverbConfigDto;
import net.craftproxy.mod.client.utils.HttpUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Minimal Pusher-protocol client for CraftShot's Reverb server.
 * <p>
 * Connects to {@code /app/{key}}, authenticates the {@code private-user.{id}} channel through
 * CraftShot's existing {@code /api/v2/messages/broadcasting/auth} endpoint and forwards
 * application events to the given handler. Reconnects every 10s while disconnected.
 * The chat service keeps polling as a fallback, so a broken socket never breaks DMs.
 */
public class ReverbClient implements AutoCloseable {
    private static final Gson GSON = new Gson();

    private final String websocketUrl;
    private final String authBaseUrl;
    private final String token;
    private final String channel;
    private final BiConsumer<String, JsonObject> handler;
    private final HttpClient http;
    private final ScheduledExecutorService scheduler;

    private volatile WebSocket socket;
    private volatile String socketId;
    private volatile boolean subscribed;
    private volatile boolean closed;
    /** Last logged problem; errors are only logged when they change, so reconnect loops stay quiet. */
    private volatile String lastError;
    private volatile boolean announced;
    private volatile long connectedAt;

    public ReverbClient(ReverbConfigDto config, String authBaseUrl, String token, int userId,
                        BiConsumer<String, JsonObject> handler) {
        String scheme = "https".equalsIgnoreCase(config.scheme) ? "wss" : "ws";
        this.websocketUrl = scheme + "://" + config.host + ":" + config.port
                + "/app/" + config.key + "?protocol=7&client=java&version=1.0&flash=false";
        this.authBaseUrl = authBaseUrl;
        this.token = token;
        this.channel = "private-user." + userId;
        this.handler = handler;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "CraftShotReverb");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler.scheduleWithFixedDelay(this::ensureConnected, 0, 10, TimeUnit.SECONDS);
    }

    public boolean isConnected() {
        return !closed && subscribed && socket != null;
    }

    private void ensureConnected() {
        if (closed) {
            return;
        }

        if (socket != null) {
            // Opened but never subscribed (silent auth rejection): reconnect.
            if (!subscribed && connectedAt > 0 && System.currentTimeMillis() - connectedAt > 15_000) {
                logError("subscription stuck, reconnecting");
                abortSocket();
            } else {
                return;
            }
        }

        try {
            socket = http.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create(websocketUrl), new Listener())
                    .get(10, TimeUnit.SECONDS);
            connectedAt = System.currentTimeMillis();
            subscribed = false;
        } catch (Exception e) {
            logError("connect failed: " + e.getMessage());
            socket = null;
        }
    }

    private void abortSocket() {
        WebSocket current = socket;
        socket = null;
        subscribed = false;
        socketId = null;
        connectedAt = 0;
        if (current != null) {
            try {
                current.abort();
            } catch (Exception ignored) {
            }
        }
    }

    private void subscribe() {
        String currentSocketId = socketId;
        if (currentSocketId == null) {
            logError("no socket id");
            return;
        }
        try {
            HttpUtils api = new HttpUtils(authBaseUrl).authToken(token)
                    .onUnauthorized(MCTunnelClient::onCraftshotUnauthorized);
            HttpUtils.Response response = api.post("/api/v2/messages/broadcasting/auth", Map.of(
                    "socket_id", currentSocketId,
                    "channel_name", channel
            ));

            if (!response.isSuccessful()) {
                logError("auth http " + response.status());
                abortSocket();
                return;
            }

            JsonObject auth = GSON.fromJson(response.body(), JsonObject.class);
            if (auth == null || !auth.has("auth")) {
                logError("auth response missing auth");
                abortSocket();
                return;
            }

            JsonObject data = new JsonObject();
            data.addProperty("channel", channel);
            data.addProperty("auth", auth.get("auth").getAsString());

            JsonObject message = new JsonObject();
            message.addProperty("event", "pusher:subscribe");
            message.add("data", data);
            send(GSON.toJson(message));
        } catch (Exception e) {
            logError("subscribe failed: " + e.getMessage());
            abortSocket();
        }
    }

    private void send(String text) {
        WebSocket current = socket;
        if (current == null) {
            logError("send skipped: no socket");
            return;
        }
        try {
            current.sendText(text, true).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            logError("send failed: " + e.getMessage());
            abortSocket();
        }
    }

    @Override
    public void close() {
        closed = true;
        scheduler.shutdownNow();
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.abort();
            } catch (Exception ignored) {
            }
        }
    }

    private void handle(String text) {
        try {
            JsonObject event = GSON.fromJson(text, JsonObject.class);
            if (event == null || !event.has("event")) {
                return;
            }

            String eventName = event.get("event").getAsString();

            switch (eventName) {
                case "pusher:connection_established" -> {
                    JsonObject payload = parsePayload(event);
                    if (payload == null || !payload.has("socket_id")) {
                        logError("connection payload missing socket_id");
                        return;
                    }
                    socketId = payload.get("socket_id").getAsString();
                    subscribe();
                }
                case "pusher_internal:subscription_succeeded" -> {
                    subscribed = true;
                    // One line for the first connection and after a logged problem, silence otherwise.
                    if (!announced || lastError != null) {
                        System.out.println("[CraftProxy] Reverb subscribed to " + channel);
                    }
                    announced = true;
                    lastError = null;
                }
                case "pusher:ping" -> send("{\"event\":\"pusher:pong\",\"data\":{}}");
                case "pusher:error" -> {
                    logError("pusher error: " + (event.has("data") ? event.get("data") : "unknown"));
                    abortSocket();
                }
                default -> {
                    JsonObject payload = parsePayload(event);
                    if (payload != null) {
                        handler.accept(event.get("event").getAsString(), payload);
                    }
                }
            }
        } catch (Exception e) {
            logError("event parse failed: " + e.getMessage());
        }
    }

    /** Reverb sends {@code data} as a JSON string; accept an object too. */
    private static JsonObject parsePayload(JsonObject event) {
        if (!event.has("data") || event.get("data").isJsonNull()) {
            return null;
        }
        JsonElement data = event.get("data");
        return data.isJsonObject() ? data.getAsJsonObject() : GSON.fromJson(data.getAsString(), JsonObject.class);
    }

    private void logError(String message) {
        if (!message.equals(lastError)) {
            lastError = message;
            System.out.println("[CraftProxy] Reverb: " + message);
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            // connection_established can arrive before buildAsync().get() returns,
            // so assign the socket here before any message is handled.
            socket = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                handle(text);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            logError("closed (" + statusCode + " " + reason + ")");
            socket = null;
            subscribed = false;
            socketId = null;
            connectedAt = 0;
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logError("error: " + (error == null ? "unknown" : error.getMessage()));
            socket = null;
            subscribed = false;
            socketId = null;
            connectedAt = 0;
        }
    }
}
