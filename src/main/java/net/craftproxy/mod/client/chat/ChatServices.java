package net.craftproxy.mod.client.chat;

/**
 * Holds the active {@link ChatService}. Defaults to the in-memory {@link LocalChatService};
 * a backend implementation only needs to call {@link #set} (e.g. after authentication).
 */
public final class ChatServices {
    private static volatile ChatService current = new LocalChatService();

    private ChatServices() {
    }

    public static ChatService get() {
        return current;
    }

    public static void set(ChatService service) {
        ChatService previous = current;
        current = service;

        // Release background pollers of a replaced service.
        if (previous instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Falls back to the local placeholder (e.g. on logout or account switch). */
    public static void reset() {
        set(new LocalChatService());
    }
}
