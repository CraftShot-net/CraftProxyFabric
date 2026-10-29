package net.craftproxy.mod.client.social;

import net.craftproxy.mod.client.MCTunnelClient;
import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.managers.FriendManager;
import net.craftproxy.mod.client.managers.TunnelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.sounds.SoundEvents;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Sending and joining world invites: hosting the singleplayer world through the tunnel
 * (opening it to LAN first) and connecting to a friend's tunnel.
 */
public final class WorldInvites {
    private static final long INVITE_COOLDOWN_MS = 2000L;
    private static final Map<String, Long> inviteCooldowns = new ConcurrentHashMap<>();

    private WorldInvites() {
    }

    public static boolean isInSingleplayerWorld() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    public static boolean isOnCooldown(String friendName) {
        Long startedAt = inviteCooldowns.get(friendName);
        return startedAt != null && System.currentTimeMillis() - startedAt < INVITE_COOLDOWN_MS;
    }

    /** Whether an invite to {@code friendName} can be sent right now. */
    public static boolean canInvite(String friendName) {
        return isInSingleplayerWorld() && !isOnCooldown(friendName);
    }

    /**
     * Opens the world to LAN if needed, starts the tunnel if it is not running yet and sends the
     * invite. {@code onResult} receives whether the backend accepted it (on an arbitrary thread).
     */
    public static void sendInvite(String friendName, String friendUuid, Consumer<Boolean> onResult) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null) {
            return;
        }

        inviteCooldowns.put(friendName, System.currentTimeMillis());
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.2f);
        }

        String worldName = mc.getSingleplayerServer().getWorldData().getLevelName();

        if (!mc.getSingleplayerServer().isPublished()) {
            ClientCompat.publishLan(mc.getSingleplayerServer());
        }

        if (MCTunnelClient.getInstance().getTunnelClient().isConnected()) {
            executeApiInvite(friendName, friendUuid, worldName, MCTunnelClient.getInstance().getTunnelClient().getHostname(), onResult);
        } else {
            TunnelManager.host(mc, MCTunnelClient.getInstance().getTunnelClient(), hostname -> executeApiInvite(friendName, friendUuid, worldName, hostname, onResult));
        }
    }

    private static void executeApiInvite(String friendName, String friendUuid, String worldName, String tunnelIp, Consumer<Boolean> onResult) {
        FriendManager.getInstance().sendWorldInvite(friendName, friendUuid, worldName, tunnelIp).thenAccept(success -> {
            if (success) {
                System.out.println("[CraftProxy] Sent invite to " + friendName + " for world " + worldName + " at " + tunnelIp);
            } else {
                System.err.println("[CraftProxy] Error sending invite to " + friendName + " for world " + worldName + " at " + tunnelIp);
            }
            onResult.accept(success);
        }).exceptionally(ex -> {
            System.err.println("[CraftProxy] Error sending invite to " + friendName + " for world " + worldName + " at " + tunnelIp);
            ex.printStackTrace();
            onResult.accept(false);
            return null;
        });
    }

    /** Leaves the current world/tunnel and connects to the invite's tunnel address. */
    public static void join(FriendManager.WorldInviteDto invite) {
        Minecraft mc = Minecraft.getInstance();
        if (FriendManager.isInitialized()) FriendManager.getInstance().dismissWorldInvite(invite);
        ClientCompat.setScreen(null);
        ClientCompat.disconnectLevel(mc);
        if (MCTunnelClient.getInstance().getTunnelClient().isConnected()) {
            MCTunnelClient.getInstance().getTunnelClient().disconnect();
        }

        ServerAddress address = ServerAddress.parseString(invite.tunnel_ip());
        //? if >=1.20.2 {
        ServerData serverData = new ServerData(
                invite.worldName(),
                invite.tunnel_ip(),
                ServerData.Type.OTHER
        );
        //?} else {
        /*ServerData serverData = new ServerData(invite.worldName(), invite.tunnel_ip(), false);
        *///?}

        ConnectScreen.startConnecting(
                new TitleScreen(),
                mc,
                address,
                serverData,
                //? if >=1.20.5 {
                false,
                null
                //?} else {
                /*false
                *///?}
        );
    }
}
