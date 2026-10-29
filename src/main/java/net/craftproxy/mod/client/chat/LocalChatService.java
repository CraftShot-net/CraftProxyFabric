package net.craftproxy.mod.client.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Placeholder chat until the backend exists: keeps conversations in memory for the
 * current game session and marks every sent message as delivered.
 */
public class LocalChatService implements ChatService {
    private final Map<Integer, List<ChatMessage>> conversations = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> unread = new ConcurrentHashMap<>();
    private final List<Consumer<Integer>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ChatMessage>> incomingListeners = new CopyOnWriteArrayList<>();

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public List<ChatMessage> history(int friendId) {
        List<ChatMessage> messages = conversations.get(friendId);
        return messages == null ? List.of() : List.copyOf(messages);
    }

    @Override
    public CompletableFuture<ChatMessage> send(int friendId, String text) {
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Empty message"));
        }
        if (trimmed.length() > maxLength()) trimmed = trimmed.substring(0, maxLength());
        ChatMessage message = new ChatMessage(UUID.randomUUID().toString(), friendId, true, trimmed,
                System.currentTimeMillis(), ChatMessage.State.SENT);
        conversations.computeIfAbsent(friendId, id -> new CopyOnWriteArrayList<>()).add(message);
        notifyListeners(friendId);
        return CompletableFuture.completedFuture(message);
    }

    /** Adds an incoming message; meant for a future backend bridge and for testing the UI. */
    public void receive(int friendId, String text) {
        ChatMessage message = new ChatMessage(UUID.randomUUID().toString(), friendId, false, text,
                System.currentTimeMillis(), ChatMessage.State.SENT);
        conversations.computeIfAbsent(friendId, id -> new CopyOnWriteArrayList<>()).add(message);
        unread.merge(friendId, 1, Integer::sum);
        for (Consumer<ChatMessage> listener : incomingListeners) {
            listener.accept(message);
        }
        notifyListeners(friendId);
    }

    @Override
    public int unreadTotal() {
        int total = 0;
        for (int value : unread.values()) {
            total += value;
        }
        return total;
    }

    @Override
    public void addIncomingListener(Consumer<ChatMessage> listener) {
        incomingListeners.add(listener);
    }

    @Override
    public void removeIncomingListener(Consumer<ChatMessage> listener) {
        incomingListeners.remove(listener);
    }

    @Override
    public int unreadCount(int friendId) {
        return unread.getOrDefault(friendId, 0);
    }

    @Override
    public void markRead(int friendId) {
        if (unread.remove(friendId) != null) notifyListeners(friendId);
    }

    @Override
    public void addListener(Consumer<Integer> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Consumer<Integer> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(int friendId) {
        for (Consumer<Integer> listener : new ArrayList<>(listeners)) {
            listener.accept(friendId);
        }
    }
}
