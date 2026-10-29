package net.craftproxy.mod.client.utils;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HttpUtils {

    private static final Gson GSON = new Gson();

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(60);

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
    private Runnable onUnauthorized;

    public HttpUtils(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // Laravel answers some routes with a redirect or HTML page unless JSON is requested.
        defaultHeaders.put("Accept", "application/json");
    }

    /**
     * Sets the Bearer token sent with every request made through this instance.
     */
    public HttpUtils authToken(String token) {
        if (token != null && !token.isBlank()) {
            defaultHeaders.put("Authorization", "Bearer " + token);
        }
        return this;
    }

    /**
     * Runs {@code callback} whenever a request through this instance is answered with 401.
     */
    public HttpUtils onUnauthorized(Runnable callback) {
        this.onUnauthorized = callback;
        return this;
    }

    public Response get(String path) {
        return send(request(path).GET());
    }

    public Response post(String path, Object body) {
        return send(request(path).header("Content-Type", "application/json").POST(BodyPublishers.ofString(GSON.toJson(body))));
    }

    /**
     * A file in a {@code multipart/form-data} request.
     */
    public record FilePart(String field, String fileName, String contentType, byte[] data) {
    }

    /**
     * Posts {@code multipart/form-data} with plain text fields and files, e.g. for uploads.
     * Uses a longer timeout than JSON requests because the body can be several megabytes.
     */
    public Response postMultipart(String path, Map<String, String> fields, List<FilePart> files) {
        String boundary = "----CraftProxy" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        fields.forEach((name, value) -> writeAscii(body, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + quote(name) + "\"\r\n\r\n", value + "\r\n"));
        for (FilePart file : files) {
            writeAscii(body, "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + quote(file.field()) + "\"; filename=\"" + quote(file.fileName()) + "\"\r\n"
                    + "Content-Type: " + file.contentType() + "\r\n\r\n", null);
            body.writeBytes(file.data());
            writeAscii(body, "\r\n", null);
        }
        writeAscii(body, "--" + boundary + "--\r\n", null);

        return send(request(path)
                .timeout(UPLOAD_TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(BodyPublishers.ofByteArray(body.toByteArray())));
    }

    /** Writes the header in ASCII and the optional value in UTF-8. */
    private static void writeAscii(ByteArrayOutputStream out, String header, String utf8Value) {
        out.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
        if (utf8Value != null) out.writeBytes(utf8Value.getBytes(StandardCharsets.UTF_8));
    }

    private static String quote(String value) {
        return value.replaceAll("[\"\\\\\\r\\n]", "_");
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
        HttpResponse<String> response;
        try {
            response = CLIENT.send(request, BodyHandlers.ofString());
        } catch (Exception e) {
            throw new HttpUtilsException("Request failed: " + request.uri(), e);
        }
        if (response.statusCode() == 401 && onUnauthorized != null) {
            onUnauthorized.run();
        }
        return new Response(response.statusCode(), response.body());
    }
}