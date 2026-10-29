package net.craftproxy.mod.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
//? if >=26.2 {
import net.minecraft.server.MinecraftServer;
//?}

/**
 * Small helpers for Minecraft APIs that moved or changed shape between the supported versions.
 */
public final class ClientCompat {
    public static final int LAN_PORT = 25565;

    private ClientCompat() {
    }

    public static Identifier id(String namespace, String path) {
        //? if >=1.21 {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new Identifier(namespace, path);
        *///?}
    }

    public static Screen currentScreen() {
        //? if >=26.2 {
        return Minecraft.getInstance().gui.screen();
        //?} else {
        /*return Minecraft.getInstance().screen;
        *///?}
    }

    public static void setScreen(Screen screen) {
        //? if >=26.2 {
        Minecraft.getInstance().gui.setScreen(screen);
        //?} else {
        /*Minecraft.getInstance().setScreen(screen);
        *///?}
    }

    public static void addToast(Toast toast) {
        //? if >=26.2 {
        Minecraft.getInstance().gui.toastManager().addToast(toast);
        //?} else if >=1.21.2 {
        /*Minecraft.getInstance().getToastManager().addToast(toast);
        *///?} else {
        /*Minecraft.getInstance().getToasts().addToast(toast);
        *///?}
    }

    /** Opens the integrated server to LAN on {@link #LAN_PORT}, keeping the world's default game mode. */
    public static void publishLan(IntegratedServer server) {
        //? if >=26.3 {
        server.publishServer(MinecraftServer.MultiplayerScope.LAN, false, LAN_PORT);
        //?} else if >=26.2 {
        /*server.publishServer(MinecraftServer.MultiplayerScope.LAN, server.getDefaultGameType(), false, LAN_PORT);
        *///?} else {
        /*server.publishServer(server.getDefaultGameType(), false, LAN_PORT);
        *///?}
    }

    /** Shows a message in the local player's chat. */
    public static void chat(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        //? if >=26.1 {
        player.sendSystemMessage(message);
        //?} else {
        /*player.displayClientMessage(message, false);
        *///?}
    }

    /** Leaves the current level the same way on every version. */
    public static void disconnectLevel(Minecraft mc) {
        if (mc.level == null) return;
        //? if >=1.21.6 {
        mc.level.disconnect(Component.empty());
        //?} else {
        /*mc.level.disconnect();
        *///?}
    }

    /** Style that copies {@code text} to the clipboard on click and shows {@code hover} as tooltip. */
    public static Style copyOnClick(Style style, String text, Component hover) {
        //? if >=1.21.5 {
        return style.withClickEvent(new ClickEvent.CopyToClipboard(text))
                .withHoverEvent(new HoverEvent.ShowText(hover));
        //?} else {
        /*return style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        *///?}
    }

    public static String gameVersionName() {
        //? if >=1.21.6 {
        return net.minecraft.SharedConstants.getCurrentVersion().name();
        //?} else {
        /*return net.minecraft.SharedConstants.getCurrentVersion().getName();
        *///?}
    }
}
