package net.craftproxy.mod.client.chat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.craftproxy.mod.client.MCTunnelClient;
import net.craftproxy.mod.client.dto.ReverbConfigDto;
import net.craftproxy.mod.client.managers.FriendManager;
import net.craftproxy.mod.client.utils.HttpUtils;
import net.minecraft.client.resources.language.I18n;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Direct messages backed by the CraftShot API ({@code /api/v2/messages/*}).
 * <p>
 * The interface is synchronous for rendering, so everything is cached in memory:
 * <ul>
 *     <li>the conversation list (unread counts, last message preview, last activity) is polled
 *     right after login and then every {@value #LIST_POLL_SECONDS}s, so badges and sorting work
 *     without opening any chat;</li>
 *     <li>the full history is only loaded for conversations that are actually opened;</li>
 *     <li>Reverb pushes new messages instantly, polling stays as a fallback.</li>
 * </ul>
 * All state changes from the network run on the single poller thread.
 */
public class CraftShotChatService implements ChatService, AutoCloseable {
    private static final Gson GSON = new Gson();
    private static final int MAX_LENGTH = 2000;
    /** Backend validation: {@code attachments.* image|max:5120} (KiB) and at most 10 files. */
    private static final int MAX_IMAGE_BYTES = 5120 * 1024;
    private static final int MAX_IMAGES = 10;
    private static final long ACTIVE_POLL_SECONDS = 3;
    private static final long LIST_POLL_SECONDS = 15;
    /** {@code before_id} larger than any message id: the backend then returns the newest page. */
    private static final int NEWEST_PAGE = Integer.MAX_VALUE;
    private static final Comparator<ChatMessage> BY_ID = Comparator.comparingLong(m -> parseId(m.id()));

    private final HttpUtils api;
    private final String baseUrl;
    private final int myUserId;
    private final Map<Integer, Conversation> conversations = new ConcurrentHashMap<>();
    private final Map<Integer, String> names = new ConcurrentHashMap<>();
    private final List<Consumer<Integer>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ChatMessage>> incomingListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService poller;
    private final ReverbClient reverb;
    private volatile int activeFriendId = -1;
    private volatile boolean synced;
    private volatile boolean closed;

    public CraftShotChatService(String baseUrl, String token, int myUserId) {
        this(baseUrl, token, myUserId, null);
    }

    public CraftShotChatService(String baseUrl, String token, int myUserId, ReverbConfigDto reverbConfig) {
        this.api = new HttpUtils(baseUrl).authToken(token)
                .onUnauthorized(MCTunnelClient::onCraftshotUnauthorized);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.myUserId = myUserId;
        this.poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "CraftShotChat");
            thread.setDaemon(true);
            return thread;
        });
        // List first and immediately: it feeds the unread badge on the title screen.
        this.poller.scheduleWithFixedDelay(this::pollConversations, 0, LIST_POLL_SECONDS, TimeUnit.SECONDS);
        this.poller.scheduleWithFixedDelay(this::pollActive, 1, ACTIVE_POLL_SECONDS, TimeUnit.SECONDS);

        this.reverb = reverbConfig != null && reverbConfig.isComplete()
                ? new ReverbClient(reverbConfig, baseUrl, token, myUserId, this::onReverbEvent)
                : null;
    }

    @Override
    public boolean isConnected() {
        return !closed;
    }

    @Override
    public List<ChatMessage> history(int friendId) {
        // Friends without a conversation stay without one until the first message is sent.
        Conversation conversation = conversations.get(friendId);
        if (conversation == null) {
            return List.of();
        }
        if (!conversation.loaded) {
            loadHistory(conversation, friendId);
        }
        return conversation.messages;
    }

    @Override
    public ChatMessage lastMessage(int friendId) {
        Conversation conversation = conversations.get(friendId);
        if (conversation == null) {
            return null;
        }
        List<ChatMessage> messages = conversation.messages;
        return messages.isEmpty() ? conversation.preview : messages.get(messages.size() - 1);
    }

    @Override
    public long lastActivity(int friendId) {
        Conversation conversation = conversations.get(friendId);
        return conversation == null ? 0L : conversation.lastActivity;
    }

    @Override
    public String displayName(int friendId) {
        return names.get(friendId);
    }

    @Override
    public CompletableFuture<ChatMessage> send(int friendId, String text) {
        return send(friendId, text, List.of());
    }

    @Override
    public CompletableFuture<ChatMessage> send(int friendId, String text, List<ChatAttachment> attachments) {
        List<ChatAttachment> files = attachments == null ? List.of() : List.copyOf(attachments);
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty() && files.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Empty message"));
        }
        if (files.size() > MAX_IMAGES) {
            return CompletableFuture.failedFuture(new ChatSendException(ChatSendException.Reason.FAILED, "Too many attachments"));
        }
        for (ChatAttachment file : files) {
            if (file.data().length > MAX_IMAGE_BYTES) {
                return CompletableFuture.failedFuture(new ChatSendException(ChatSendException.Reason.TOO_LARGE, "Attachment too large"));
            }
        }
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }
        final String content = trimmed;

        return CompletableFuture.supplyAsync(() -> {
            Conversation conversation = ensureConversation(friendId);
            String path = "/api/v2/messages/" + conversation.conversationId;
            HttpUtils.Response raw;
            try {
                if (files.isEmpty()) {
                    raw = api.post(path, Map.of("content", content));
                } else {
                    List<HttpUtils.FilePart> parts = new ArrayList<>();
                    for (ChatAttachment file : files) {
                        parts.add(new HttpUtils.FilePart("attachments[]", file.fileName(), file.mimeType(), file.data()));
                    }
                    raw = api.postMultipart(path, content.isEmpty() ? Map.of() : Map.of("content", content), parts);
                }
            } catch (HttpUtils.HttpUtilsException e) {
                throw new ChatSendException(ChatSendException.Reason.FAILED, e.getMessage());
            }

            if (!raw.isSuccessful()) {
                throw new ChatSendException(failureReason(raw), "Message could not be sent (" + raw.status() + ")");
            }
            SendResponse response = raw.as(SendResponse.class);
            if (response == null || !response.success() || response.message() == null) {
                throw new ChatSendException(ChatSendException.Reason.FAILED, "Message could not be sent");
            }

            ChatMessage message = toChatMessage(response.message(), friendId);
            if (appendIfNew(conversation, response.message(), friendId)) {
                notifyListeners(friendId);
            }
            return message;
        });
    }

    /** Maps an error response of the send endpoint to something the player can act on. */
    private static ChatSendException.Reason failureReason(HttpUtils.Response response) {
        String body = response.body() == null ? "" : response.body();
        return switch (response.status()) {
            // 413 comes from the web server before Laravel even sees the upload.
            case 413 -> ChatSendException.Reason.TOO_LARGE;
            case 403 -> ChatSendException.Reason.FORBIDDEN;
            case 429 -> ChatSendException.Reason.RATE_LIMITED;
            // Laravel validation: "attachments.0 ... greater than 5120 kilobytes" or "... must be an image".
            case 422 -> body.contains("kilobytes") ? ChatSendException.Reason.TOO_LARGE
                    : body.contains("attachments") ? ChatSendException.Reason.UNSUPPORTED
                    : ChatSendException.Reason.FAILED;
            default -> ChatSendException.Reason.FAILED;
        };
    }

    @Override
    public int unreadCount(int friendId) {
        Conversation conversation = conversations.get(friendId);
        return conversation == null ? 0 : conversation.unread.get();
    }

    @Override
    public void markRead(int friendId) {
        activeFriendId = friendId;

        Conversation conversation = conversations.get(friendId);
        if (conversation == null) {
            return;
        }
        // Only talk to the server when something was actually unread; the social screen
        // calls this on every conversation change.
        if (conversation.unread.getAndSet(0) > 0) {
            sendReadReceipt(conversation);
            notifyListeners(friendId);
        }
        if (!conversation.loaded) {
            loadHistory(conversation, friendId);
        }
    }

    @Override
    public int maxLength() {
        return MAX_LENGTH;
    }

    @Override
    public boolean supportsImages() {
        return true;
    }

    @Override
    public int maxImageBytes() {
        return MAX_IMAGE_BYTES;
    }

    @Override
    public int maxImages() {
        return MAX_IMAGES;
    }

    @Override
    public void addListener(Consumer<Integer> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Consumer<Integer> listener) {
        listeners.remove(listener);
    }

    @Override
    public int unreadTotal() {
        int total = 0;
        for (Conversation conversation : conversations.values()) {
            total += conversation.unread.get();
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
    public void close() {
        closed = true;
        poller.shutdownNow();
        if (reverb != null) {
            reverb.close();
        }
    }

    // --- push ---

    /** Called on the Reverb thread; the event is applied on the poller thread. */
    private void onReverbEvent(String event, JsonObject payload) {
        if (!"message.sent".equals(event) || payload == null || !payload.has("message")) {
            return;
        }
        try {
            MessageDto message = GSON.fromJson(payload.get("message"), MessageDto.class);
            if (message != null) {
                poller.execute(() -> applyPushed(message, true));
            }
        } catch (RejectedExecutionException ignored) {
            // Service closed.
        } catch (Exception e) {
            System.out.println("[CraftProxy] Invalid chat push: " + e.getMessage());
        }
    }

    private void applyPushed(MessageDto message, boolean firstAttempt) {
        int friendId = friendIdForConversation(message.conversation_id());
        if (friendId < 0) {
            // First message of a conversation we did not know yet: refresh the list once.
            // The refreshed unread count already includes this message.
            if (firstAttempt) {
                pollConversations();
                applyPushed(message, false);
            }
            return;
        }
        onNewMessage(conversations.get(friendId), message, friendId, firstAttempt);
    }

    /** Stores a newly received message and updates unread count, read receipt and toast. */
    private void onNewMessage(Conversation conversation, MessageDto message, int friendId, boolean countUnread) {
        if (!appendIfNew(conversation, message, friendId)) {
            return;
        }
        if (message.sender_id() != myUserId) {
            if (message.sender() != null && message.sender().username() != null) {
                names.put(friendId, message.sender().username());
            }
            if (isVisible(friendId)) {
                sendReadReceipt(conversation);
            } else {
                if (countUnread) {
                    conversation.unread.incrementAndGet();
                }
                fireIncoming(toChatMessage(message, friendId));
            }
        }
        notifyListeners(friendId);
    }

    /** True when the conversation is currently visible in the open social screen. */
    private boolean isVisible(int friendId) {
        return friendId == activeFriendId && isScreenOpen();
    }

    private static boolean isScreenOpen() {
        return FriendManager.isInitialized() && FriendManager.getInstance().isScreenOpen();
    }

    private int friendIdForConversation(int conversationId) {
        for (Map.Entry<Integer, Conversation> entry : conversations.entrySet()) {
            if (entry.getValue().conversationId == conversationId) {
                return entry.getKey();
            }
        }
        return -1;
    }

    // --- background loading ---

    private Conversation ensureConversation(int friendId) {
        Conversation existing = conversations.get(friendId);
        if (existing != null) {
            return existing;
        }

        InitResponse response = api
                .post("/api/v2/messages/conversations/init", Map.of("user_ids", List.of(friendId)))
                .as(InitResponse.class);

        if (response == null || response.conversation_id() <= 0) {
            throw new IllegalStateException("Could not open conversation");
        }

        Conversation created = new Conversation(response.conversation_id());
        Conversation raced = conversations.putIfAbsent(friendId, created);
        return raced != null ? raced : created;
    }

    private void loadHistory(Conversation conversation, int friendId) {
        synchronized (conversation) {
            if (conversation.loading || conversation.loaded) {
                return;
            }
            conversation.loading = true;
        }

        CompletableFuture.runAsync(() -> {
            try {
                MessagesResponse response = api
                        .get("/api/v2/messages/conversations/" + conversation.conversationId
                                + "/messages?before_id=" + NEWEST_PAGE)
                        .as(MessagesResponse.class);

                if (response != null && response.messages() != null) {
                    List<ChatMessage> loaded = new ArrayList<>();
                    for (MessageDto message : response.messages()) {
                        loaded.add(toChatMessage(message, friendId));
                    }
                    synchronized (conversation) {
                        // Keep messages pushed while the request was running.
                        long newestLoaded = loaded.isEmpty() ? 0 : parseId(loaded.get(loaded.size() - 1).id());
                        for (ChatMessage existing : conversation.messages) {
                            if (parseId(existing.id()) > newestLoaded) {
                                loaded.add(existing);
                            }
                        }
                        conversation.replaceMessages(loaded);
                    }
                }
                conversation.loaded = true;
                notifyListeners(friendId);
            } catch (Exception ignored) {
                // Keep the conversation unloaded so the next call retries.
            } finally {
                conversation.loading = false;
            }
        });
    }

    /** Fetches new messages of the open conversation (fallback when Reverb is down). */
    private void pollActive() {
        if (closed || !isScreenOpen()) {
            return;
        }
        int friendId = activeFriendId;
        Conversation conversation = friendId < 0 ? null : conversations.get(friendId);
        if (conversation == null) {
            return;
        }
        if (!conversation.loaded) {
            loadHistory(conversation, friendId);
            return;
        }

        try {
            MessagesResponse response = api
                    .get("/api/v2/messages/conversations/" + conversation.conversationId
                            + "/messages?since_id=" + conversation.lastMessageId)
                    .as(MessagesResponse.class);
            if (response == null) {
                return;
            }
            if (response.messages() != null) {
                for (MessageDto message : response.messages()) {
                    onNewMessage(conversation, message, friendId, true);
                }
            }
            if (response.deleted_ids() != null && !response.deleted_ids().isEmpty()) {
                Set<Integer> deleted = Set.copyOf(response.deleted_ids());
                boolean removed;
                synchronized (conversation) {
                    List<ChatMessage> kept = new ArrayList<>(conversation.messages);
                    removed = kept.removeIf(message -> deleted.contains((int) parseId(message.id())));
                    if (removed) {
                        conversation.replaceMessages(kept);
                    }
                }
                if (removed) {
                    notifyListeners(friendId);
                }
            }
        } catch (Exception ignored) {
            // Network hiccup; the next poll retries.
        }
    }

    /** Refreshes unread counts, previews and last activity of all private conversations. */
    private void pollConversations() {
        if (closed) {
            return;
        }
        try {
            ConversationsResponse response = api.get("/api/v2/messages").as(ConversationsResponse.class);
            if (response == null || response.conversations() == null) {
                return;
            }

            boolean pushActive = reverb != null && reverb.isConnected();
            for (ConversationDto dto : response.conversations()) {
                if (!"private".equalsIgnoreCase(dto.type())
                        || dto.other_users() == null
                        || dto.other_users().size() != 1) {
                    continue;
                }

                OtherUserDto other = dto.other_users().get(0);
                int friendId = other.id();
                if (other.username() != null) {
                    names.put(friendId, other.username());
                }
                Conversation conversation = conversations.computeIfAbsent(friendId, id -> new Conversation(dto.id()));

                LatestMessageDto latest = dto.latest_message();
                long activity = parseTimestamp(dto.last_message_at(),
                        latest == null ? 0L : parseTimestamp(latest.created_at(), 0L));
                conversation.lastActivity = Math.max(conversation.lastActivity, activity);
                if (latest != null) {
                    boolean outgoing = latest.sender_id() != null
                            ? latest.sender_id() == myUserId
                            : "Du".equals(latest.sender_name());
                    conversation.preview = new ChatMessage(
                            latest.id() == null ? "preview" : String.valueOf(latest.id()),
                            friendId, outgoing, displayText(latest.content(), latest.has_attachments()),
                            activity, ChatMessage.State.SENT);
                }

                if (isVisible(friendId)) {
                    // Read on screen, but the server has not seen the receipt yet.
                    if (dto.unread_count() > 0) {
                        sendReadReceipt(conversation);
                    }
                    continue;
                }

                int previous = conversation.unread.getAndSet(dto.unread_count());
                // Without a live socket this poll is the only signal for new messages.
                if (synced && !pushActive && dto.unread_count() > previous
                        && conversation.preview != null && !conversation.preview.outgoing()) {
                    fireIncoming(conversation.preview);
                }
            }
            synced = true;
        } catch (Exception ignored) {
            // Network hiccup; the next poll retries.
        }
    }

    private void sendReadReceipt(Conversation conversation) {
        CompletableFuture.runAsync(() -> {
            try {
                api.post("/api/v2/messages/" + conversation.conversationId + "/read", Map.of());
            } catch (Exception ignored) {
                // Read receipts are best effort.
            }
        });
    }

    /** Returns true when the message was not in the cache yet. */
    private boolean appendIfNew(Conversation conversation, MessageDto message, int friendId) {
        String id = String.valueOf(message.id());
        synchronized (conversation) {
            for (ChatMessage existing : conversation.messages) {
                if (existing.id().equals(id)) {
                    return false;
                }
            }
            List<ChatMessage> updated = new ArrayList<>(conversation.messages);
            updated.add(toChatMessage(message, friendId));
            updated.sort(BY_ID);
            conversation.replaceMessages(updated);
        }
        return true;
    }

    private ChatMessage toChatMessage(MessageDto message, int friendId) {
        return new ChatMessage(
                String.valueOf(message.id()),
                friendId,
                message.sender_id() == myUserId,
                message.content() == null ? "" : message.content(),
                parseTimestamp(message.created_at(), System.currentTimeMillis()),
                ChatMessage.State.SENT,
                imageUrls(message.attachments())
        );
    }

    /**
     * The backend stores attachments as paths on its public disk ({@code messages/attachments/x.png}),
     * served without auth under {@code /storage/}.
     */
    private List<String> imageUrls(JsonElement attachments) {
        if (attachments == null || !attachments.isJsonArray()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (JsonElement element : attachments.getAsJsonArray()) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String path = element.getAsString();
            if (path.startsWith("http://") || path.startsWith("https://")) {
                urls.add(path);
            } else if (!path.isBlank()) {
                urls.add(baseUrl + "/storage/" + (path.startsWith("/") ? path.substring(1) : path));
            }
        }
        return urls;
    }

    /** List previews have no attachment paths; show a placeholder instead of an empty line. */
    private static String displayText(String content, boolean hasAttachments) {
        if (content != null && !content.isBlank()) {
            return content;
        }
        return hasAttachments ? I18n.get("screen.craftproxy.social.chat.attachment") : "";
    }

    private static long parseTimestamp(String iso, long fallback) {
        if (iso == null || iso.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void fireIncoming(ChatMessage message) {
        for (Consumer<ChatMessage> listener : incomingListeners) {
            listener.accept(message);
        }
    }

    private void notifyListeners(int friendId) {
        for (Consumer<Integer> listener : listeners) {
            listener.accept(friendId);
        }
    }

    // --- DTOs (Gson) ---

    private record InitResponse(String redirect, int conversation_id) {
    }

    private record SendResponse(boolean success, MessageDto message) {
    }

    private record MessagesResponse(List<MessageDto> messages, List<Integer> deleted_ids) {
    }

    private record ConversationsResponse(int my_id, List<ConversationDto> conversations) {
    }

    private record ConversationDto(int id, String type, String name, int unread_count, String last_message_at,
                                   LatestMessageDto latest_message, List<OtherUserDto> other_users) {
    }

    /** {@code id}, {@code sender_id} and {@code created_at} are missing on older backends. */
    private record LatestMessageDto(Integer id, Integer sender_id, String content, boolean has_attachments,
                                    String sender_name, String created_at) {
    }

    private record OtherUserDto(int id, String username, String uuid) {
    }

    private record SenderDto(int id, String username, String uuid) {
    }

    private record MessageDto(int id, int conversation_id, int sender_id, String content, JsonElement attachments,
                              String created_at, SenderDto sender) {
    }

    private static final class Conversation {
        final int conversationId;
        /** Immutable snapshot, replaced while holding the conversation's lock. */
        volatile List<ChatMessage> messages = List.of();
        /** Latest message from the conversation list, used until the history is loaded. */
        volatile ChatMessage preview;
        final AtomicInteger unread = new AtomicInteger();
        volatile long lastActivity;
        volatile long lastMessageId;
        volatile boolean loaded;
        volatile boolean loading;

        Conversation(int conversationId) {
            this.conversationId = conversationId;
        }

        void replaceMessages(List<ChatMessage> updated) {
            messages = List.copyOf(updated);
            if (!updated.isEmpty()) {
                ChatMessage newest = updated.get(updated.size() - 1);
                lastMessageId = Math.max(lastMessageId, parseId(newest.id()));
                lastActivity = Math.max(lastActivity, newest.timestamp());
            }
        }
    }
}
