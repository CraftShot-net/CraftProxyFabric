package net.craftproxy.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.craftproxy.mod.CraftProxy;
import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.config.Endpoints;
import net.craftproxy.mod.client.chat.ChatNotifications;
import net.craftproxy.mod.client.chat.ChatServices;
import net.craftproxy.mod.client.chat.CraftShotChatService;
import net.craftproxy.mod.client.dto.AuthResponseDto;
import net.craftproxy.mod.client.gui.ChatToast;
import net.craftproxy.mod.client.gui.IconButton;
import net.craftproxy.mod.client.managers.FriendManager;
import net.craftproxy.mod.client.managers.TunnelManager;
import net.craftproxy.mod.client.managers.WardrobeManager;
import net.craftproxy.mod.client.rpc.DiscordRPCManager;
import net.craftproxy.mod.client.screens.SocialScreen;
import net.craftproxy.mod.client.screens.WardrobeScreen;
import net.craftproxy.mod.client.utils.HttpUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
//? if >=1.21 {
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
//?} else {
/*import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
*///?}
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
    /** CraftShot social-core token; {@code null} until federated auth succeeds. */
    public static String craftshotToken = null;
    public static Integer craftshotUserId = null;
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
    // Hotkey pressed inside a menu; run on the next tick so the typed character does not reach the new screen.
    private static Runnable pendingHotkeyAction;

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

            // Chat toasts are regular vanilla toasts; clicks are routed through the screen events
            // so the toast can open the conversation (works on title, pause and social screens).
            //? if >=1.21.9 {
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                    !ChatToast.clickAt(event.x(), event.y(), event.button()));
            //?} else {
            /*ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) ->
                    !ChatToast.clickAt(mouseX, mouseY, button));
            *///?}

            // Vanilla only fires key mappings without an open screen, so menus get the hotkeys here.
            //? if >=1.21.9 {
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) ->
                    !handleMenuHotkey(s, openFriendsKey.matches(event), openWardrobeKey.matches(event)));
            //?} else {
            /*ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, modifiers) ->
                    !handleMenuHotkey(s, openFriendsKey.matches(key, scancode), openWardrobeKey.matches(key, scancode)));
            *///?}
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (pendingHotkeyAction != null) {
                Runnable action = pendingHotkeyAction;
                pendingHotkeyAction = null;
                action.run();
            }

            while (openFriendsKey.consumeClick()) {
                if (ClientCompat.currentScreen() == null && apiToken != null) {
                    ClientCompat.setScreen(SocialScreen.getInstance());
                }
            }

            while (openWardrobeKey.consumeClick()) {
                if (ClientCompat.currentScreen() == null && apiToken != null) {
                    ClientCompat.setScreen(WardrobeScreen.getInstance());
                }
            }

            //detect account switch

            String currentMcToken = mc.getUser().getAccessToken();

            // If the token changed...
            if (lastKnownMcToken != null && !lastKnownMcToken.equals(currentMcToken)) {
                lastKnownMcToken = currentMcToken;
                apiToken = null;
                craftshotToken = null;
                craftshotUserId = null;
                ChatServices.reset();

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
            ChatServices.reset();
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

        // Daemon thread so it never keeps the JVM alive after the client shuts down.
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CraftProxy-Heartbeat");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(() -> {
            try {
                if (apiToken != null) {
                    FriendManager.getInstance().sendHeartbeat();
                }
            } catch (Exception ignored) {
                // ingore to keep thread alive
            }

            try {
                DiscordRPCManager.updateDiscordPresence(mc);
            } catch (Exception e) {
                System.out.println("[CraftProxy] Discord presence update failed: " + e);
                e.printStackTrace();
            }

        }, 10, 30, TimeUnit.SECONDS);

    }

    /** Opens (or toggles) the social / wardrobe screen from any menu; returns true when the key was used. */
    private static boolean handleMenuHotkey(Screen screen, boolean social, boolean wardrobe) {
        if ((!social && !wardrobe) || apiToken == null || isTyping(screen)) return false;
        Screen target = social ? SocialScreen.getInstance() : WardrobeScreen.getInstance();
        pendingHotkeyAction = () -> {
            Screen current = ClientCompat.currentScreen();
            if (current == target) {
                ClientCompat.setScreen(null);
                return;
            }
            // Close containers properly so the server knows the menu is gone.
            if (current instanceof AbstractContainerScreen<?>) current.onClose();
            ClientCompat.setScreen(target);
        };
        return true;
    }

    private static boolean isTyping(Screen screen) {
        if (screen instanceof AbstractSignEditScreen || screen instanceof BookEditScreen || screen instanceof KeyBindsScreen) return true;
        for (GuiEventListener child : screen.children()) {
            if ((child instanceof EditBox || child instanceof MultiLineEditBox) && child.isFocused()) return true;
        }
        return false;
    }

    private static final Identifier FRIENDS_ICON = CraftProxy.id("textures/gui/icon_friends.png");
    private static final Identifier WARDROBE_ICON = CraftProxy.id("textures/gui/icon_wardrobe.png");
    private static final Identifier DISCORD_ICON = CraftProxy.id("textures/gui/icon_discord.png");
    private static final String DISCORD_URL = Endpoints.discord();

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
                ClientCompat.setScreen(SocialScreen.getInstance());
            }
        }).badge(MCTunnelClient::pendingSocialCount);

        friendsButton.active = apiToken != null;
        friendsButton.setTooltip(Tooltip.create(apiToken != null ? tr("open_friends_button") : tr("not_connected")));

        widgetsOf(screen).add(friendsButton);

        ScreenEvents.afterTick(screen).register(s -> {
            boolean connected = apiToken != null;
            friendsButton.active = connected;
            friendsButton.setTooltip(Tooltip.create(connected ? tr("open_friends_button") : tr("not_connected")));
        });
    }

    // Friend requests plus world invites, shown as badge on the friends button.
    private static int pendingSocialCount() {
        if (!FriendManager.isInitialized()) return 0;
        FriendManager manager = FriendManager.getInstance();
        return manager.getCachedPendingRequests().size()
                + manager.getCachedWorldInvites().size()
                + ChatServices.get().unreadTotal();
    }

    private static void addWardrobeIconButton(Screen screen, int x, int y) {
        IconButton wardrobeButton = new IconButton(x, y, 20, WARDROBE_ICON, tr("open_wardrobe_button"), button -> {
            if (apiToken != null) {
                ClientCompat.setScreen(WardrobeScreen.getInstance());
            }
        });

        wardrobeButton.active = apiToken != null;
        wardrobeButton.setTooltip(Tooltip.create(apiToken != null ? tr("open_wardrobe_button") : tr("not_connected")));

        widgetsOf(screen).add(wardrobeButton);

        ScreenEvents.afterTick(screen).register(s -> {
            boolean connected = apiToken != null;
            wardrobeButton.active = connected;
            wardrobeButton.setTooltip(Tooltip.create(connected ? tr("open_wardrobe_button") : tr("not_connected")));
        });
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

    private static volatile long lastCraftshotReauthAt;

    /**
     * Called when CraftShot rejects our token, e.g. because another client of the same player
     * replaced it. Logging in again fetches the current one; at most once a minute.
     */
    public static void onCraftshotUnauthorized() {
        long now = System.currentTimeMillis();
        if (apiToken == null || craftshotToken == null || now - lastCraftshotReauthAt < 60_000L) return;
        lastCraftshotReauthAt = now;
        System.out.println("[CraftProxy] CraftShot token rejected, re-authenticating...");
        authenticate(Minecraft.getInstance());
    }

    public static void authenticate(Minecraft mc) {
        new Thread(() -> {
            try {
                String mcToken = mc.getUser().getAccessToken();
                String username = mc.getUser().getName();
                String uuid = mc.getUser().getProfileId().toString().replace("-", "");
                String serverId = UUID.randomUUID().toString().replace("-", "");

                HttpUtils mojangApi = new HttpUtils(Endpoints.mojangSession());
                Map<String, String> joinBody = new HashMap<>();
                joinBody.put("accessToken", mcToken);
                joinBody.put("selectedProfile", uuid);
                joinBody.put("serverId", serverId);

                HttpUtils.Response mojangRes = mojangApi.post("/session/minecraft/join", joinBody);

                if (!mojangRes.isSuccessful() && mojangRes.status() != 204) {
                    System.err.println("[CraftProxy] Mojang join failed: " + mojangRes.status());
                    return;
                }

                HttpUtils api = new HttpUtils(Endpoints.craftProxyBackend());
                Map<String, String> body = new HashMap<>();
                body.put("username", username);
                body.put("server_id", serverId);

                HttpUtils.Response res = api.post("/auth/minecraft", body);

                if (res.isSuccessful()) {
                    AuthResponseDto authData = res.as(AuthResponseDto.class);
                    apiToken = authData.token;
                    craftshotToken = authData.craftshot_token;
                    craftshotUserId = authData.craftshot_user_id;

                    FriendManager.init(
                            Endpoints.craftProxyBackend(), apiToken,
                            Endpoints.craftShot(), craftshotToken);
                    WardrobeManager.init(Endpoints.craftProxyBackend(), apiToken);
                    WardrobeManager.getInstance().fetchMyCosmetics();

                    if (craftshotToken != null && craftshotUserId != null && craftshotUserId > 0) {
                        ChatServices.set(new CraftShotChatService(
                                Endpoints.craftShot(), craftshotToken, craftshotUserId, authData.craftshot_reverb));
                        ChatNotifications.register();
                    } else {
                        ChatServices.reset();
                    }

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
