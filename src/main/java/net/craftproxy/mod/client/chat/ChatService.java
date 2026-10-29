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

    /**
     * Latest message of the conversation for list previews, or {@code null} when there is none.
     * Unlike {@link #history} this must not trigger any loading.
     */
    default ChatMessage lastMessage(int friendId) {
        List<ChatMessage> history = history(friendId);
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    /** Epoch millis of the latest activity in the conversation, 0 when there is none (used for sorting). */
    default long lastActivity(int friendId) {
        ChatMessage last = lastMessage(friendId);
        return last == null ? 0L : last.timestamp();
    }

    /** Name of a chat partner known to the service, or {@code null} (fallback for toasts). */
    default String displayName(int friendId) {
        return null;
    }

    /** Total unread messages across all conversations. */
    default int unreadTotal() {
        return 0;
    }

    /** Called when a new incoming message arrives (not for history loads or outgoing messages). */
    default void addIncomingListener(Consumer<ChatMessage> listener) {
    }

    default void removeIncomingListener(Consumer<ChatMessage> listener) {
    }

    /** Maximum message length accepted by {@link #send}. */
    default int maxLength() {
        return 256;
    }

    /** Whether {@link #send(int, String, List)} accepts image attachments. */
    default boolean supportsImages() {
        return false;
    }

    /** Largest attachment in bytes the backend accepts. */
    default int maxImageBytes() {
        return 0;
    }

    /** Most attachments allowed on one message. */
    default int maxImages() {
        return 0;
    }

    /**
     * Sends a message with image attachments; the text may be empty when there is at least one image.
     * Failures complete exceptionally, ideally with a {@link ChatSendException}.
     */
    default CompletableFuture<ChatMessage> send(int friendId, String text, List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return send(friendId, text);
        }
        return CompletableFuture.failedFuture(
                new ChatSendException(ChatSendException.Reason.UNSUPPORTED, "Attachments are not supported"));
    }
}
