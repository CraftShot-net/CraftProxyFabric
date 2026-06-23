package net.mctunnel.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;

public class MCTunnelClient implements ClientModInitializer {

	public static TunnelClient tunnelClient;

	@Override
	public void onInitializeClient() {
		tunnelClient = new TunnelClient();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			dispatcher.register(ClientCommands.literal("host").executes(ctx -> {
				Minecraft mc = Minecraft.getInstance();

				if (mc.getSingleplayerServer() == null) {
					ctx.getSource().sendFeedback(
							Component.literal("§cYou need to be in a singleplayer world!")
					);
					return 0;
				}

				if (tunnelClient.isConnected()) {
					ctx.getSource().sendFeedback(
							Component.literal("§eAlready hosting at §f" + tunnelClient.getHostname())
					);
					return 0;
				}

				mc.getSingleplayerServer().publishServer(
						MinecraftServer.MultiplayerScope.LAN,
						GameType.SURVIVAL,
						false,
						25565
				);

				tunnelClient.connect(hostname -> {
					mc.execute(() -> {
						if (mc.player != null) {
							mc.player.sendSystemMessage(
									Component.empty()
											.append(Component.literal("✔ Tunnel active!\n").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
											.append(Component.literal("Share this IP with your friends: ").withStyle(ChatFormatting.GRAY))
											.append(Component.literal(hostname)
													.withStyle(style -> style
															.withColor(ChatFormatting.GOLD)
															.withUnderlined(true)
															.withClickEvent(new ClickEvent.CopyToClipboard(hostname))
															.withHoverEvent(new HoverEvent.ShowText(
																	Component.literal("Click to copy").withStyle(ChatFormatting.YELLOW)
															))
													)
											)
							);
						}
					});
				});

				return 1;
			}));
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> {
			if (tunnelClient.isConnected()) tunnelClient.disconnect();
		});

		ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
			if (tunnelClient.isConnected()) tunnelClient.disconnect();
		});
	}

}