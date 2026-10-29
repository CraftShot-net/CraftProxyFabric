package net.craftproxy.mod.client.gui;

import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.compat.Gfx;
import net.craftproxy.mod.client.compat.SkinData;
import net.craftproxy.mod.client.screens.SocialScreen;
import net.craftproxy.mod.client.ui.Icon;
import net.craftproxy.mod.client.utils.SkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Toast shown when a direct message arrives while its conversation is not open. Further
 * messages from the same sender update the visible toast instead of stacking new ones.
 * Clicking the toast opens the conversation. Render thread only.
 */
public class ChatToast extends CraftToast {

    private static final Map<Integer, ChatToast> ACTIVE = new HashMap<>();

    private final int friendId;
    private final String sender;
    private final Supplier<SkinData> skinSupplier;
    private String preview;
    private int count = 1;

    private ChatToast(int friendId, String sender, String preview) {
        this.friendId = friendId;
        this.sender = sender;
        this.preview = oneLine(preview);
        this.skinSupplier = SkinManager.getSkinAsyncPublic(sender);
    }

    public static void show(int friendId, String sender, String preview) {
        ChatToast existing = ACTIVE.get(friendId);
        if (existing != null && existing.isLive()) {
            existing.preview = oneLine(preview);
            existing.count++;
            existing.restartTimer();
            return;
        }
        ChatToast toast = new ChatToast(friendId, sender, preview);
        ACTIVE.put(friendId, toast);
        ClientCompat.addToast(toast);
    }

    /**
     * Opens the conversation when the mouse clicked a visible chat toast. Called from the
     * screen mouse handler; returns true when the click was consumed.
     */
    public static boolean clickAt(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        for (ChatToast toast : ACTIVE.values()) {
            if (toast.isClickableAt(mouseX, mouseY)) {
                toast.dismiss();
                // Changing screens while the click is still being dispatched is unsafe.
                Minecraft.getInstance().execute(() -> SocialScreen.openChat(toast.friendId));
                return true;
            }
        }
        return false;
    }

    @Override
    protected void draw(Gfx graphics, Font font, long elapsedMs) {
        drawCard(graphics, font, skinSupplier.get().texture(), Icon.CHAT, sender, preview, count, elapsedMs);
    }
}
