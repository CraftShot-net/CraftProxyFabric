package net.craftproxy.mod.client;

import net.craftproxy.mod.client.dto.AuthResponseDto;
import net.craftproxy.mod.client.gui.IconButton;
import net.craftproxy.mod.client.managers.FriendManager;
import net.craftproxy.mod.client.managers.TunnelManager;
import net.craftproxy.mod.client.screens.FriendsScreen;
import net.craftproxy.mod.client.utils.HttpUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MCTunnelClient implements ClientModInitializer {
    private static final String TRANSLATION_PREFIX = "message.craftproxy.client.";
    public static MCTunnelClient instance;
    public static TunnelClient tunnelClient;
    public static String apiToken = null;
    private static String lastKnownMcToken = null;
    private static final KeyBinding openFriendsKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.craftproxy.open_friends", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.craftproxy")
    );

    // Layout for the vertical mod-button column next to the (204px wide) vanilla pause menu.
    private static final int MOD_BUTTON_MARGIN = 2;
    private static final int MOD_BUTTON_COLUMN_TOP = 92;

    @Override
    public void onInitializeClient() {
        final MinecraftClient mc = MinecraftClient.getInstance();
        tunnelClient = new TunnelClient();
        instance = this;

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            lastKnownMcToken = mc.getSession().getAccessToken();
            authenticate(mc);
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GameMenuScreen) {
                findButtonByText(screen, Text.translatable("menu.returnToGame").getString())
                        .ifPresentOrElse(
                                anchor -> addFriendsIconButton(client, screen, anchor.getX() + anchor.getWidth() + MOD_BUTTON_MARGIN, anchor.getY()),
                                () -> addFriendsIconButton(client, screen, (scaledWidth / 2) + (204 / 2) + MOD_BUTTON_MARGIN, MOD_BUTTON_COLUMN_TOP)
                        );
            } else if (screen instanceof TitleScreen) {
                // Top-right corner;
                addFriendsIconButton(client, screen, scaledWidth - 24, 6);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            while (openFriendsKey.wasPressed()) {
                if (mc.currentScreen == null && apiToken != null && FriendManager.isInitialized()) {
                    mc.setScreen(FriendsScreen.getInstance());
                }
            }

            //detect account switch

            String currentMcToken = mc.getSession().getAccessToken();

            // If the token changed...
            if (lastKnownMcToken != null && !lastKnownMcToken.equals(currentMcToken)) {
                lastKnownMcToken = currentMcToken;
                apiToken = null;

                // If the tunnel is currently active, kill it immediately
                if (tunnelClient.isConnected()) {
                    tunnelClient.disconnect();
                    if (mc.player != null) {
                        mc.player.sendMessage(tr("account_changed_disconnected").formatted(Formatting.RED), false);
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
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("host").executes(ctx -> {

            if (mc.getServer() == null) {
                ctx.getSource().sendFeedback(tr("singleplayer_required").formatted(Formatting.RED));
                return 0;
            }

            if (tunnelClient.isConnected()) {
                ctx.getSource().sendFeedback(tr("already_hosting_prefix").formatted(Formatting.YELLOW).append(Text.literal(tunnelClient.getHostname()).formatted(Formatting.WHITE)));
                return 0;
            }


            TunnelManager.host(mc, tunnelClient, hostname -> {});

            return 1;
        })));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            tunnelClient.disconnect();
            FriendManager.shutdownAndSetOffline();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            tunnelClient.disconnect();
            FriendManager manager = FriendManager.getInstanceOrNull();
            if (manager != null) {
                manager.clearWorldInvites();
            }
        });

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                if (apiToken != null) {
                    FriendManager manager = FriendManager.getInstanceOrNull();
                    if (manager != null) {
                        manager.sendHeartbeat();
                    }
                }
            } catch (Exception ignored) {
                // ingore to keep thread alive
            }
        }, 10, 30, TimeUnit.SECONDS);

    }

    private static final Identifier FRIENDS_ICON = new Identifier("craftproxy", "textures/gui/icon_friends.png");

    private static java.util.Optional<ClickableWidget> findButtonByText(Screen screen, String text) {
        return Screens.getButtons(screen).stream()
                .filter(widget -> widget instanceof ButtonWidget button && button.getMessage().getString().equals(text))
                .findFirst();
    }

    private static void addFriendsIconButton(MinecraftClient client, Screen screen, int x, int y) {
        IconButton friendsButton = new IconButton(x, y, 20, FRIENDS_ICON, tr("open_friends_button"), button -> {
            if (apiToken != null && FriendManager.isInitialized()) {
                client.setScreen(FriendsScreen.getInstance());
            }
        });

        friendsButton.active = apiToken != null && FriendManager.isInitialized();
        friendsButton.setTooltip(Tooltip.of(tr("open_friends_button")));

        Screens.getButtons(screen).add(friendsButton);

        ScreenEvents.afterTick(screen).register(s -> friendsButton.active = apiToken != null && FriendManager.isInitialized());
    }

    public static MCTunnelClient getInstance() {
        return instance;
    }

    public TunnelClient getTunnelClient() {
        return tunnelClient;
    }

    public static void authenticate(MinecraftClient mc) {
        new Thread(() -> {
            try {
                String mcToken = mc.getSession().getAccessToken();

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

    public static MutableText tr(String key, Object... args) {
        return Text.translatable(TRANSLATION_PREFIX + key, args);
    }
}