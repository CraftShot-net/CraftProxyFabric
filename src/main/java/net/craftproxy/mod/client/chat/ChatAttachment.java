package net.craftproxy.mod.client.chat;

/**
 * An image waiting to be sent with the next message, already in an upload-ready format.
 *
 * @param id       local id, also used as preview texture key
 * @param fileName file name sent to the backend
 * @param mimeType content type of {@code data}
 * @param data     encoded image bytes
 */
public record ChatAttachment(String id, String fileName, String mimeType, byte[] data) {
}
