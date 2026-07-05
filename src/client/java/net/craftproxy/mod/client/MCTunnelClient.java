package net.craftproxy.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.craftproxy.mod.client.dto.AuthResponseDto;
import net.craftproxy.mod.client.managers.FriendManager;
import net.craftproxy.mod.client.screens.FriendsScreen;
import net.craftproxy.mod.client.utils.HttpUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MCTunnelClient implements ClientModInitializer {
    private static final String TRANSLATION_PREFIX = "message.craftproxy.client.";

    public static TunnelClient tunnelClient;
    public static String apiToken = null;
    private static String lastKnownMcToken = null;
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("craftproxy", "keys"));
    private static final KeyMapping openFriendsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.craftproxy.open_friends", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY));

    @Override
    public void onInitializeClient() {
        final Minecraft mc = Minecraft.getInstance();
        tunnelClient = new TunnelClient();

        ClientLifecycleEvents.CLIENT_STARTED.register(_ -> {
            lastKnownMcToken = mc.getUser().getAccessToken();
            authenticate(mc);
        });

        ClientTickEvents.END_CLIENT_TICK.register(_ -> {

            while (openFriendsKey.consumeClick()) {
                if (mc.gui.screen() == null) {
                    mc.gui.setScreen(FriendsScreen.getInstance());
                }
            }

            //detect account switch

            String currentMcToken = mc.getUser().getAccessToken();

            // If the token changed...
            if (lastKnownMcToken != null && !lastKnownMcToken.equals(currentMcToken)) {
                lastKnownMcToken = currentMcToken;
                apiToken = null;

                // If the tunnel is currently active, kill it immediately
                if (tunnelClient.isConnected()) {
                    tunnelClient.disconnect();
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(tr("account_changed_disconnected").withStyle(ChatFormatting.RED));
                    }
                }

                // Check if the new token is actually a real token (not offline/temporary)
                boolean isRealToken = currentMcToken.length() > 20;

                if (isRealToken) {
                    System.out.println("[CraftProxy] Valid new account detected! Re-authenticating...");
                    authenticate(mc);
                } else {
                    System.out.println("[CraftProxy] Account cleared/offline. Waiting for valid login...");
                    FriendManager.shutdownAndSetOffline();
                }
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> dispatcher.register(ClientCommands.literal("host").executes(ctx -> {

            if (mc.getSingleplayerServer() == null) {
                ctx.getSource().sendFeedback(tr("singleplayer_required").withStyle(ChatFormatting.RED));
                return 0;
            }

            if (tunnelClient.isConnected()) {
                ctx.getSource().sendFeedback(tr("already_hosting_prefix").withStyle(ChatFormatting.YELLOW).append(Component.literal(tunnelClient.getHostname()).withStyle(ChatFormatting.WHITE)));
                return 0;
            }

            mc.getSingleplayerServer().publishServer(MinecraftServer.MultiplayerScope.LAN, GameType.SURVIVAL, false, 25565);

            tunnelClient.connect(hostname -> mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.empty().append(tr("tunnel_active").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).append(Component.literal("\n")).append(tr("share_ip_prefix").withStyle(ChatFormatting.GRAY)).append(Component.literal(hostname).withStyle(style -> style.withColor(ChatFormatting.GOLD).withUnderlined(true).withClickEvent(new ClickEvent.CopyToClipboard(hostname)).withHoverEvent(new HoverEvent.ShowText(tr("click_to_copy").withStyle(ChatFormatting.YELLOW))))));
                }
            }));

            return 1;
        })));

        ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> {
            if (tunnelClient.isConnected()) tunnelClient.disconnect();
            FriendManager.shutdownAndSetOffline();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
            if (tunnelClient.isConnected()) tunnelClient.disconnect();
        });

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> FriendManager.getInstance().sendHeartbeat(), 0, 30, TimeUnit.SECONDS);

    }

    public static void authenticate(Minecraft mc) {
        new Thread(() -> {
            try {
                String mcToken = mc.getUser().getAccessToken();

                HttpUtils api = new HttpUtils("https://backend.craftproxy.net/");
                Map<String, String> body = new HashMap<>();
                body.put("minecraft_token", mcToken);

                HttpUtils.Response res = api.post("/auth/minecraft", body);

                if (res.isSuccessful()) {
                    AuthResponseDto authData = res.as(AuthResponseDto.class);
                    apiToken = authData.token;

                    FriendManager.init("https://backend.craftproxy.net", apiToken);

                    System.out.println("[CraftProxy] Successfully authenticated as " + authData.user.name);

                    FriendManager.getInstance().setIngameOnline();
                } else {
                    apiToken = null;
                    System.err.println("[CraftProxy] Auth failed! Status: " + res.status());
                    System.err.println("[CraftProxy] Backend Error: " + res.body());
                    FriendManager.shutdown();
                }
            } catch (Exception e) {
                apiToken = null;
                FriendManager.shutdown();
                e.printStackTrace();
            }
        }).start();
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(TRANSLATION_PREFIX + key, args);
    }
}