package net.craftproxy.mod.client.utils;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class HttpUtils {

    private static final Gson GSON = new Gson();

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Response wrapper with the status code and raw body, plus a Gson shortcut for deserializing.
     */
    public record Response(int status, String body) {

        public boolean isSuccessful() {
            return status >= 200 && status < 300;
        }

        public boolean isUnauthorized() {
            return status == 401;
        }

        /**
         * Deserializes the response body into the given type using Gson.
         */
        public <T> T as(Class<T> type) {
            return GSON.fromJson(body, type);
        }
    }

    /**
     * Thrown when the request itself fails (connection error, timeout, etc.).
     */
    public static class HttpUtilsException extends RuntimeException {
        public HttpUtilsException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final String baseUrl;
    private final Map<String, String> defaultHeaders = new HashMap<>();

    public HttpUtils(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Sets the Bearer token sent with every request made through this instance.
     */
    public HttpUtils authToken(String token) {
        defaultHeaders.put("Authorization", "Bearer " + token);
        return this;
    }

    public Response get(String path) {
        return send(request(path).GET());
    }

    public Response post(String path, Object body) {
        return send(request(path).header("Content-Type", "application/json").POST(BodyPublishers.ofString(GSON.toJson(body))));
    }

    public Response put(String path, Object body) {
        return send(request(path).header("Content-Type", "application/json").PUT(BodyPublishers.ofString(GSON.toJson(body))));
    }

    public Response delete(String path) {
        return send(request(path).DELETE());
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).timeout(REQUEST_TIMEOUT);
        defaultHeaders.forEach(builder::header);
        return builder;
    }

    private Response send(HttpRequest.Builder requestBuilder) {
        HttpRequest request = requestBuilder.build();
        try {
            HttpResponse<String> response = CLIENT.send(request, BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (Exception e) {
            throw new HttpUtilsException("Request failed: " + request.uri(), e);
        }
    }
}