package net.craftproxy.mod.client.chat;

/**
 * One direct message between the local player and a friend.
 *
 * @param id           unique id (assigned by the service / backend)
 * @param friendId     id of the friend this conversation belongs to
 * @param outgoing     {@code true} if the local player sent it
 * @param text         message text
 * @param timestamp    epoch milliseconds
 * @param state        delivery state of outgoing messages
 */
public record ChatMessage(String id, int friendId, boolean outgoing, String text, long timestamp, State state) {
    public enum State {SENDING, SENT, FAILED}

    public ChatMessage withState(State state) {
        return new ChatMessage(id, friendId, outgoing, text, timestamp, state);
    }
}
