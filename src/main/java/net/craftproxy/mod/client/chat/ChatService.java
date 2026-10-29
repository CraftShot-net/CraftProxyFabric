package net.craftproxy.mod.client.chat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Direct messages between friends. The UI only talks to this interface, so a real backend
 * can replace {@link LocalChatService} later through {@link ChatServices#set(ChatService)}.
 * <p>
 * Listeners may be called from any thread; UI code must hop to the render thread itself.
 */
public interface ChatService {
    /** {@code false} while the service is only a local placeholder without a server. */
    boolean isConnected();

    /** Messages of the conversation with {@code friendId}, oldest first. */
    List<ChatMessage> history(int friendId);

    /** Sends a message; the returned future completes with the stored (sent or failed) message. */
    CompletableFuture<ChatMessage> send(int friendId, String text);

    int unreadCount(int friendId);

    void markRead(int friendId);

    /** Called with the friend id whenever a conversation changed (new message, state change). */
    void addListener(Consumer<Integer> listener);

    void removeListener(Consumer<Integer> listener);

    /** Maximum message length accepted by {@link #send}. */
    default int maxLength() {
        return 256;
    }
}
