package net.craftproxy.mod.client.managers;

import net.craftproxy.mod.client.TunnelClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;

import java.util.function.Consumer;

import static net.craftproxy.mod.client.MCTunnelClient.tr;

public class TunnelManager {

    public static void host(Minecraft mc, TunnelClient tunnelClient, Consumer<String> onConnected) {
        assert mc.getSingleplayerServer() != null;
        mc.getSingleplayerServer().publishServer(MinecraftServer.MultiplayerScope.LAN, GameType.SURVIVAL, false, 25565);

        tunnelClient.connect(hostname -> mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.empty().append(tr("tunnel_active").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).append(Component.literal("\n")).append(tr("share_ip_prefix").withStyle(ChatFormatting.GRAY)).append(Component.literal(hostname).withStyle(style -> style.withColor(ChatFormatting.GOLD).withUnderlined(true).withClickEvent(new ClickEvent.CopyToClipboard(hostname)).withHoverEvent(new HoverEvent.ShowText(tr("click_to_copy").withStyle(ChatFormatting.YELLOW))))));
            }

            if (onConnected != null) {
                onConnected.accept(hostname);
            }
        }));
    }

}
