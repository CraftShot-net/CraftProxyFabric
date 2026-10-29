package net.craftproxy.mod.client.chat;

import net.craftproxy.mod.client.gui.ChatToast;
import net.craftproxy.mod.client.managers.FriendManager;
import net.minecraft.client.Minecraft;

/**
 * Bridges incoming chat messages to a toast. Registered once per active chat service
 * (i.e. after authentication), so it also works while the social screen is closed.
 */
public final class ChatNotifications {
    private ChatNotifications() {
    }

    public static void register() {
        ChatServices.get().addIncomingListener(ChatNotifications::show);
    }

    /** Called on a network thread; toasts must be added on the render thread. */
    private static void show(ChatMessage message) {
        String sender = senderName(message.friendId());
        Minecraft.getInstance().execute(() ->
                ChatToast.show(message.friendId(), sender, message.previewText()));
    }

    private static String senderName(int friendId) {
        if (FriendManager.isInitialized()) {
            String name = FriendManager.getInstance().nameForId(friendId);
            if (!"?".equals(name)) return name;
        }
        String known = ChatServices.get().displayName(friendId);
        return known != null ? known : "?";
    }
}
