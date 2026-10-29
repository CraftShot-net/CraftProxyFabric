package net.craftproxy.mod.client.chat;

import net.minecraft.client.resources.language.I18n;

import java.util.List;

/**
 * One direct message between the local player and a friend.
 *
 * @param id           unique id (assigned by the service / backend)
 * @param friendId     id of the friend this conversation belongs to
 * @param outgoing     {@code true} if the local player sent it
 * @param text         message text (may be empty for image-only messages)
 * @param timestamp    epoch milliseconds
 * @param state        delivery state of outgoing messages
 * @param images       absolute URLs of attached images, never {@code null}
 */
public record ChatMessage(String id, int friendId, boolean outgoing, String text, long timestamp, State state,
                          List<String> images) {
    public enum State {SENDING, SENT, FAILED}

    public ChatMessage {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public ChatMessage(String id, int friendId, boolean outgoing, String text, long timestamp, State state) {
        this(id, friendId, outgoing, text, timestamp, state, List.of());
    }

    public ChatMessage withState(State state) {
        return new ChatMessage(id, friendId, outgoing, text, timestamp, state, images);
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }

    /** One-line text for lists and toasts: image-only messages get a placeholder instead of nothing. */
    public String previewText() {
        if (text != null && !text.isBlank()) return text;
        return hasImages() ? I18n.get("screen.craftproxy.social.chat.attachment") : "";
    }
}
