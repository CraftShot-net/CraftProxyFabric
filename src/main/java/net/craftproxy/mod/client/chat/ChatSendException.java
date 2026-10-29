package net.craftproxy.mod.client.chat;

/**
 * Failure of {@link ChatService#send} with a reason the UI can explain to the player.
 */
public class ChatSendException extends RuntimeException {
    public enum Reason {
        /** An attachment exceeds the backend's size limit. */
        TOO_LARGE,
        /** An attachment is not an image the backend accepts. */
        UNSUPPORTED,
        /** Too many messages or uploads in a short time. */
        RATE_LIMITED,
        /** Not allowed to message this user (not mutual friends, blocked, ...). */
        FORBIDDEN,
        /** Network error or anything unexpected. */
        FAILED
    }

    private final Reason reason;

    public ChatSendException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** Unwraps {@code CompletionException}s and maps anything unknown to {@link Reason#FAILED}. */
    public static Reason reasonOf(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ChatSendException send) return send.reason();
            current = current.getCause();
        }
        return Reason.FAILED;
    }
}
