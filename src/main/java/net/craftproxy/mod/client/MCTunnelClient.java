package net.craftproxy.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.craftproxy.mod.CraftProxy;
import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.dto.AuthResponseDto;
import net.craftproxy.mod.client.gui.IconButton;
import net.craftproxy.mod.client.managers.FriendManager;
import net.craftproxy.mod.client.managers.TunnelManager;
import net.craftproxy.mod.client.managers.WardrobeManager;
import net.craftproxy.mod.client.rpc.DiscordRPCManager;
import net.craftproxy.mod.client.screens.FriendsScreen;
import net.craftproxy.mod.client.screens.WardrobeScreen;
import net.craftproxy.mod.client.utils.HttpUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
//?} else {
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
//? if >=1.21.9 {
import net.craftproxy.mod.client.cosmetics.CustomCapeLayer;
import net.craftproxy.mod.client.managers.CapeManager;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRenderEvents;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?}
//? if >=1.21.9 && <26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
*///?}
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MCTunnelClient implements ClientModInitializer {
    private static final String TRANSLATION_PREFIX = "message.craftproxy.client.";
    public static MCTunnelClient instance;
    public static TunnelClient tunnelClient;
    public static String apiToken = null;
    private static String lastKnownMcToken = null;
    //? if >=1.21.9 {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CraftProxy.id("keys"));
    //?} else {
    /*public static final String CATEGORY = "key.category.craftproxy.keys";
    *///?}
    //? if >=26.3 {
    private static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYBOARD;
    //?} else {
    /*private static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYSYM;
    *///?}
    private static final KeyMapping openFriendsKey = registerKey(new KeyMapping("key.craftproxy.open_friends", KEYBOARD, InputConstants.KEY_I, CATEGORY));
    private static final KeyMapping openWardrobeKey = registerKey(new KeyMapping("key.craftproxy.open_wardrobe", KEYBOARD, InputConstants.KEY_H, CATEGORY));

    // Layout for the vertical mod-button column next to the (204px wide) vanilla pause menu.
    private static final int MOD_BUTTON_MARGIN = 2;
    private static final int MOD_BUTTON_COLUMN_TOP = 92;

    private static KeyMapping registerKey(KeyMapping key) {
        //? if >=26.1 {
        return KeyMappingHelper.registerKeyMapping(key);
        //?} else {
        /*return KeyBindingHelper.registerKeyBinding(key);
        *///?}
    }

    @Override
    public void onInitializeClient() {
        final Minecraft mc = Minecraft.getInstance();
        tunnelClient = new TunnelClient();
        instance = this;

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            lastKnownMcToken = mc.getUser().getAccessToken();
            authenticate(mc);

            DiscordRPCManager.init();
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen) {
                findButtonByText(screen, Component.translatable("menu.returnToGame").getString())
                        .ifPresentOrElse(
                                anchor -> {
                                    int bx = anchor.getX() + anchor.getWidth() + MOD_BUTTON_MARGIN;
                                    int by = anchor.getY();
                                    addFriendsIconButton(screen, bx, by);
                                    addWardrobeIconButton(screen, bx, by + 24);
                                },
                                () -> {
                                    int bx = (scaledWidth / 2) + (204 / 2) + MOD_BUTTON_MARGIN;
                                    addFriendsIconButton(screen, bx, MOD_BUTTON_COLUMN_TOP);
                                    addWardrobeIconButton(screen, bx, MOD_BUTTON_COLUMN_TOP + 24);
                                }
                        );
            } else if (screen instanceof TitleScreen) {
                // Top-right corner: Friends, Wardrobe, Discord
                addFriendsIconButton(screen, scaledWidth - 24, 6);
                addWardrobeIconButton(screen, scaledWidth - 48, 6);
                addDiscordIconButton(screen, scaledWidth - 72, 6);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            while (openFriendsKey.consumeClick()) {
                if (ClientCompat.currentScreen() == null) {
                    ClientCompat.setScreen(FriendsScreen.getInstance());
                }
            }

            while (openWardrobeKey.consumeClick()) {
                if (ClientCompat.currentScreen() == null) {
                    ClientCompat.setScreen(WardrobeScreen.getInstance());
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
                        ClientCompat.chat(tr("account_changed_disconnected").withStyle(ChatFormatting.RED));
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
                    WardrobeManager.shutdown();
                }
            }
        });
        //? if >=26.1 {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommands.literal("host").executes(ctx -> {
        //?} else {
        /*ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("host").executes(ctx -> {
        *///?}


            if (mc.getSingleplayerServer() == null) {
                ctx.getSource().sendFeedback(tr("singleplayer_required").withStyle(ChatFormatting.RED));
                return 0;
            }

            if (tunnelClient.isConnected()) {
                ctx.getSource().sendFeedback(tr("already_hosting_prefix").withStyle(ChatFormatting.YELLOW).append(Component.literal(tunnelClient.getHostname()).withStyle(ChatFormatting.WHITE)));
                return 0;
            }


            TunnelManager.host(mc, tunnelClient, hostname -> {});

            return 1;
        })));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (tunnelClient.isConnected()) tunnelClient.disconnect();
            FriendManager.shutdownAndSetOffline();
            WardrobeManager.shutdown();
            DiscordRPCManager.shutdown();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> DiscordRPCManager.updateDiscordPresence(mc));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (tunnelClient.isConnected()) { tunnelClient.disconnect();
                FriendManager.getInstance().clearWorldInvites();
            }
            DiscordRPCManager.updateDiscordPresence(mc);
        });

        // Before 1.21.9 capes are injected through AbstractClientPlayerMixin instead.
        //? if >=26.1 {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
        //?} else if >=1.21.9 {
        /*LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
        *///?}
        //? if >=1.21.9 {
            if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
                registrationHelper.register(new CustomCapeLayer(avatarRenderer, context.getModelSet(), context.getEquipmentAssets()));
            }
        });

        LivingEntityFeatureRenderEvents.ALLOW_CAPE_RENDER.register(state -> {
            if (state instanceof AvatarRenderState avatarState) {
                return CapeManager.getCapeFor(avatarState.id) == null;
            }
            return true;
        });
        //?}

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                if (apiToken != null) {
                    FriendManager.getInstance().sendHeartbeat();
                }
            } catch (Exception ignored) {
                // ingore to keep thread alive
            }

            try {
                System.out.println("[CraftProxy] Calling updateDiscordPresence...");
                DiscordRPCManager.updateDiscordPresence(mc);
            } catch (Exception e) {
                System.out.println("[CraftProxy] Discord presence update failed: " + e);
                e.printStackTrace();
            }

        }, 10, 30, TimeUnit.SECONDS);

    }

    private static final Identifier FRIENDS_ICON = CraftProxy.id("textures/gui/icon_friends.png");
    private static final Identifier WARDROBE_ICON = CraftProxy.id("textures/gui/icon_wardrobe.png");
    private static final Identifier DISCORD_ICON = CraftProxy.id("textures/gui/icon_discord.png");
    private static final String DISCORD_URL = "https://discord.gg/wBj5Jf9pcm";

    private static List<AbstractWidget> widgetsOf(Screen screen) {
        //? if >=26.1 {
        return Screens.getWidgets(screen);
        //?} else {
        /*return Screens.getButtons(screen);
        *///?}
    }

    private static Optional<AbstractWidget> findButtonByText(Screen screen, String text) {
        return widgetsOf(screen).stream()
                .filter(widget -> widget instanceof Button button && button.getMessage().getString().equals(text))
                .findFirst();
    }

    private static void addFriendsIconButton(Screen screen, int x, int y) {
        IconButton friendsButton = new IconButton(x, y, 20, FRIENDS_ICON, tr("open_friends_button"), button -> {
            if (apiToken != null) {
                ClientCompat.setScreen(FriendsScreen.getInstance());
            }
        });

        friendsButton.active = apiToken != null;
        friendsButton.setTooltip(Tooltip.create(tr("open_friends_button")));

        widgetsOf(screen).add(friendsButton);

        ScreenEvents.afterTick(screen).register(s -> friendsButton.active = apiToken != null);
    }

    private static void addWardrobeIconButton(Screen screen, int x, int y) {
        IconButton wardrobeButton = new IconButton(x, y, 20, WARDROBE_ICON, tr("open_wardrobe_button"), button -> {
            if (apiToken != null) {
                ClientCompat.setScreen(WardrobeScreen.getInstance());
            }
        });

        wardrobeButton.active = apiToken != null;
        wardrobeButton.setTooltip(Tooltip.create(tr("open_wardrobe_button")));

        widgetsOf(screen).add(wardrobeButton);

        ScreenEvents.afterTick(screen).register(s -> wardrobeButton.active = apiToken != null);
    }

    private static void addDiscordIconButton(Screen screen, int x, int y) {
        IconButton discordButton = new IconButton(
                x,
                y,
                20,
                DISCORD_ICON,
                tr("open_discord_button"),
                //? if >=1.20.5 {
                ConfirmLinkScreen.confirmLink(screen, URI.create(DISCORD_URL), false)
                //?} else {
                /*ConfirmLinkScreen.confirmLink(DISCORD_URL, screen, false)
                *///?}
        );
        discordButton.setTooltip(Tooltip.create(tr("open_discord_button")));

        widgetsOf(screen).add(discordButton);
    }

    public static MCTunnelClient getInstance() {
        return instance;
    }

    public TunnelClient getTunnelClient() {
        return tunnelClient;
    }

    public static void authenticate(Minecraft mc) {
        new Thread(() -> {
            try {
                String mcToken = mc.getUser().getAccessToken();
                String username = mc.getUser().getName();
                String uuid = mc.getUser().getProfileId().toString().replace("-", "");
                String serverId = UUID.randomUUID().toString().replace("-", "");

                HttpUtils mojangApi = new HttpUtils("https://sessionserver.mojang.com");
                Map<String, String> joinBody = new HashMap<>();
                joinBody.put("accessToken", mcToken);
                joinBody.put("selectedProfile", uuid);
                joinBody.put("serverId", serverId);

                HttpUtils.Response mojangRes = mojangApi.post("/session/minecraft/join", joinBody);

                if (!mojangRes.isSuccessful() && mojangRes.status() != 204) {
                    System.err.println("[CraftProxy] Mojang join failed: " + mojangRes.status());
                    return;
                }

                HttpUtils api = new HttpUtils("https://backend.craftproxy.net/");
                Map<String, String> body = new HashMap<>();
                body.put("username", username);
                body.put("server_id", serverId);

                HttpUtils.Response res = api.post("/auth/minecraft", body);

                if (res.isSuccessful()) {
                    AuthResponseDto authData = res.as(AuthResponseDto.class);
                    apiToken = authData.token;

                    FriendManager.init("https://backend.craftproxy.net", apiToken);
                    WardrobeManager.init("https://backend.craftproxy.net", apiToken);
                    WardrobeManager.getInstance().fetchMyCosmetics();

                    System.out.println("[CraftProxy] Successfully authenticated as " + authData.user.name);

                    FriendManager.getInstance().setIngameOnline();
                } else {
                    apiToken = null;
                    System.err.println("[CraftProxy] Auth failed! Status: " + res.status());
                    System.err.println("[CraftProxy] Backend Error: " + res.body());
                    FriendManager.shutdown();
                    WardrobeManager.shutdown();
                }
            } catch (Exception e) {
                apiToken = null;
                FriendManager.shutdown();
                WardrobeManager.shutdown();
                e.printStackTrace();
            }
        }).start();
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(TRANSLATION_PREFIX + key, args);
    }
}
