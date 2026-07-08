package net.craftproxy.mod.client.managers;

import net.craftproxy.mod.client.TunnelClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameMode;

import java.util.function.Consumer;

import static net.craftproxy.mod.client.MCTunnelClient.tr;

public class TunnelManager {

    public static void host(MinecraftClient mc, TunnelClient tunnelClient, Consumer<String> onConnected) {

        MinecraftServer server = mc.getServer();
        assert server != null;
        if (!server.isRemote()) {
            server.openToLan(GameMode.SURVIVAL, false, 25565);
        }

        tunnelClient.connect(hostname -> mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(
                        Text.empty()
                                .append(tr("tunnel_active").formatted(Formatting.GREEN, Formatting.BOLD))
                                .append(Text.literal("\n"))
                                .append(tr("share_ip_prefix").formatted(Formatting.GRAY))
                                .append(
                                        Text.literal(hostname).styled(style -> style
                                                .withColor(Formatting.GOLD)
                                                .withUnderline(true)
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, hostname))
                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tr("click_to_copy").formatted(Formatting.YELLOW)))
                                        )
                                ),
                        false
                );
            }

            if (onConnected != null) {
                onConnected.accept(hostname);
            }
        }));
    }

}
