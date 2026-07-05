package net.craftproxy.mod.client.managers;

import net.craftproxy.mod.client.utils.HttpUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class FriendManager {

    private static FriendManager instance;
    private final HttpUtils api;

    public record UserDto(int id, String name, String minecraft_uuid) {}
    public record FriendsListResponse(List<UserDto> friends) {}
    public record PendingListResponse(List<UserDto> pending_requests) {}
    public record IngameStatusResponse(String username, boolean is_ingame_online, String last_seen) {}
    private final CopyOnWriteArrayList<UserDto> cachedFriends = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<UserDto> cachedPending = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService pollingExecutor;
    private static final long BACKGROUND_POLL_SECONDS = 20;
    private static final long FOREGROUND_POLL_SECONDS = 4;


    private final CopyOnWriteArrayList<Consumer<List<UserDto>>> pendingListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<List<UserDto>>> friendsListeners = new CopyOnWriteArrayList<>();

    public FriendManager(String baseUrl, String token) {
        this.api = new HttpUtils(baseUrl).authToken(token);
    }

    private volatile boolean screenOpen = false;

    public void setScreenOpen(boolean open) {
        this.screenOpen = open;
        if (open) forceRefresh(); // beim Öffnen sofort einmal pushen
        restartPollingWithCurrentInterval();
    }

    private void restartPollingWithCurrentInterval() {
        if (pollingExecutor != null) pollingExecutor.shutdownNow();

        pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FriendManager-Poll");
            t.setDaemon(true);
            return t;
        });

        long interval = screenOpen ? FOREGROUND_POLL_SECONDS : BACKGROUND_POLL_SECONDS;
        pollingExecutor.scheduleWithFixedDelay(this::pollOnce, 0, interval, TimeUnit.SECONDS);
    }

    public static void init(String baseUrl, String token) {
        if (instance != null) {
            instance.stopPolling();
        }
        instance = new FriendManager(baseUrl, token);
        instance.startPolling();
    }

    public static FriendManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("FriendManager not initialized. Call FriendManager.init(baseUrl, token) first.");
        }
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.stopPolling();
            instance = null;
        }
    }

    public static void shutdownAndSetOffline() {
        if (instance != null) {
            try {
                instance.setIngameOffline().get(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[CraftProxy] Interrupted while setting ingame offline state.");
            } catch (ExecutionException | TimeoutException e) {
                System.err.println("[CraftProxy] Failed to set ingame offline state: " + e.getMessage());
            } finally {
                instance.stopPolling();
                instance = null;
            }
        }
    }

    public List<UserDto> getCachedFriends() {
        return Collections.unmodifiableList(cachedFriends);
    }

    public List<UserDto> getCachedPendingRequests() {
        return Collections.unmodifiableList(cachedPending);
    }

    public void addPendingRequestsListener(Consumer<List<UserDto>> listener) {
        pendingListeners.add(listener);
    }

    public void addFriendsListener(Consumer<List<UserDto>> listener) {
        friendsListeners.add(listener);
    }

    public void removePendingRequestsListener(Consumer<List<UserDto>> listener) {
        pendingListeners.remove(listener);
    }

    public void removeFriendsListener(Consumer<List<UserDto>> listener) {
        friendsListeners.remove(listener);
    }

    private void startPolling() {
        restartPollingWithCurrentInterval();
    }

    private void stopPolling() {
        if (pollingExecutor != null) {
            pollingExecutor.shutdownNow();
            pollingExecutor = null;
        }
    }

    private void pollOnce() {
        try {
            HttpUtils.Response friendsRes = api.get("/api/friends");
            if (friendsRes.isSuccessful()) {
                List<UserDto> fresh = friendsRes.as(FriendsListResponse.class).friends();
                cachedFriends.clear();
                cachedFriends.addAll(fresh);
                notifyListeners(friendsListeners, cachedFriends);
            }
        } catch (Exception e) {
            // will auto retry so dont care
        }

        try {
            HttpUtils.Response pendingRes = api.get("/api/friends/pending");
            if (pendingRes.isSuccessful()) {
                List<UserDto> fresh = pendingRes.as(PendingListResponse.class).pending_requests();
                cachedPending.clear();
                cachedPending.addAll(fresh);
                notifyListeners(pendingListeners, cachedPending);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyListeners(List<Consumer<List<UserDto>>> listeners, List<UserDto> data) {
        List<UserDto> snapshot = List.copyOf(data);
        for (Consumer<List<UserDto>> listener : listeners) {
            listener.accept(snapshot);
        }
    }

    public void forceRefresh() {
        if (pollingExecutor != null && !pollingExecutor.isShutdown()) {
            pollingExecutor.execute(this::pollOnce);
        }
    }

    public CompletableFuture<List<UserDto>> getFriends() {
        return CompletableFuture.supplyAsync(() -> {
            HttpUtils.Response res = api.get("/api/friends");
            if (res.isSuccessful()) {
                List<UserDto> fresh = res.as(FriendsListResponse.class).friends();
                cachedFriends.clear();
                cachedFriends.addAll(fresh);
                notifyListeners(friendsListeners, cachedFriends);
                return fresh;
            }
            throw new RuntimeException("Error getting friends.");
        });
    }

    public CompletableFuture<List<UserDto>> getPendingRequests() {
        return CompletableFuture.supplyAsync(() -> {
            HttpUtils.Response res = api.get("/api/friends/pending");
            if (res.isSuccessful()) {
                List<UserDto> fresh = res.as(PendingListResponse.class).pending_requests();
                cachedPending.clear();
                cachedPending.addAll(fresh);
                notifyListeners(pendingListeners, cachedPending);
                return fresh;
            }
            throw new RuntimeException("Error getting pending requests.");
        });
    }

    public CompletableFuture<Boolean> sendRequestByName(String username) {
        return CompletableFuture.supplyAsync(() -> {
            HttpUtils.Response res = api.post("/api/friends/request/" + username, new Object());
            return res.isSuccessful();
        });
    }

    public CompletableFuture<Boolean> acceptRequest(int senderId) {
        return CompletableFuture.supplyAsync(() -> api.post("/api/friends/accept/" + senderId, new Object()).isSuccessful())
                .thenApply(success -> {
                    if (success) forceRefresh();
                    return success;
                });
    }

    public CompletableFuture<IngameStatusResponse> getIngameOnlineStatus(String username) {
        return CompletableFuture.supplyAsync(() -> {
            HttpUtils.Response res = api.get("/api/mod-status/" + username);
            if (res.isSuccessful()) {
                return res.as(IngameStatusResponse.class);
            }
            throw new RuntimeException("Error getting ingame online status.");
        });
    }

    public void setIngameOnline() {
        CompletableFuture.supplyAsync(() -> api.post("/api/mod-status/online", new Object()).isSuccessful());
    }

    public void sendHeartbeat() {
        CompletableFuture.supplyAsync(() -> api.post("/api/mod-status/heartbeat", new Object()).isSuccessful());
    }

    public CompletableFuture<Boolean> setIngameOffline() {
        return CompletableFuture.supplyAsync(() -> api.post("/api/mod-status/offline", new Object()).isSuccessful());
    }

    public CompletableFuture<Boolean> declineRequest(int senderId) {
        return CompletableFuture.supplyAsync(() -> api.post("/api/friends/decline/" + senderId, new Object()).isSuccessful())
                .thenApply(success -> {
                    if (success) forceRefresh();
                    return success;
                });
    }

    public CompletableFuture<Boolean> removeFriend(int friendId) {
        return CompletableFuture.supplyAsync(() -> api.delete("/api/friends/" + friendId).isSuccessful())
                .thenApply(success -> {
                    if (success) forceRefresh();
                    return success;
                });
    }
}