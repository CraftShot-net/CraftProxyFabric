package net.craftproxy.mod.client.managers;

import net.craftproxy.mod.client.TunnelClient;
import net.craftproxy.mod.client.compat.ClientCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

import static net.craftproxy.mod.client.MCTunnelClient.tr;

public class TunnelManager {

    public static void host(Minecraft mc, TunnelClient tunnelClient, Consumer<String> onConnected) {
        assert mc.getSingleplayerServer() != null;
        ClientCompat.publishLan(mc.getSingleplayerServer());

        if(tunnelClient.isConnected()) {
            tunnelClient.disconnect();
            //close previous connected tunnel in case it's not successfully closed yet
        }

        tunnelClient.connect(hostname -> mc.execute(() -> {
            if (mc.player != null) {
                ClientCompat.chat(Component.empty().append(tr("tunnel_active").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).append(Component.literal("\n")).append(tr("share_ip_prefix").withStyle(ChatFormatting.GRAY)).append(Component.literal(hostname).withStyle(style -> ClientCompat.copyOnClick(style.withColor(ChatFormatting.GOLD).withUnderlined(true), hostname, tr("click_to_copy").withStyle(ChatFormatting.YELLOW)))));
            }

            if (onConnected != null) {
                onConnected.accept(hostname);
            }
        }));
    }

}
