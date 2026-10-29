package net.craftproxy.mod.client;

import net.craftproxy.mod.client.compat.ClientCompat;
import net.craftproxy.mod.client.config.Endpoints;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import net.craftproxy.mod.client.utils.HttpUtils;

public class TunnelClient {
    private static final String TRANSLATION_PREFIX = "message.craftproxy.tunnel.";

    public static final AttributeKey<String> PLAYER_IP = AttributeKey.valueOf("playerIp");
    private static final String SERVER_HOST = Endpoints.tunnelHost();
    private static final int CONTROL_PORT = 8000;
    private static final int BRIDGE_PORT = 8001;

    private Channel controlChannel;
    private NioEventLoopGroup group;
    private volatile String assignedHostname;
    private volatile boolean connected = false;
    private volatile boolean intentionalDisconnect = false;
    private Consumer<String> onReadyCallback;
    private static final String prefix = "§7(§6CraftProxy§7) ";

    public void connect(Consumer<String> onReady) {
        this.onReadyCallback = onReady;
        this.intentionalDisconnect = false;
        this.group = new NioEventLoopGroup(2);
        doConnect();
    }

    private void doConnect() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new IdleStateHandler(0, 0, 40), new LineBasedFrameDecoder(8192), new StringDecoder(StandardCharsets.UTF_8), new StringEncoder(StandardCharsets.UTF_8), new ControlHandler());
            }
        });

        bootstrap.connect(SERVER_HOST, CONTROL_PORT).addListener((ChannelFuture f) -> {
            if (f.isSuccess()) {
                controlChannel = f.channel();
                
                new Thread(() -> {
                    try {
                        String mcToken = Minecraft.getInstance().getUser().getAccessToken();
                        String username = Minecraft.getInstance().getUser().getName();
                        String uuid = Minecraft.getInstance().getUser().getProfileId().toString().replace("-", "");
                        String serverId = UUID.randomUUID().toString().replace("-", "");

                        HttpUtils mojangApi = new HttpUtils(Endpoints.mojangSession());
                        Map<String, String> joinBody = new HashMap<>();
                        joinBody.put("accessToken", mcToken);
                        joinBody.put("selectedProfile", uuid);
                        joinBody.put("serverId", serverId);

                        mojangApi.post("/session/minecraft/join", joinBody);

                        controlChannel.writeAndFlush("LOGIN " + username + " " + serverId + "\n");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
                
                connected = true;
            } else {
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (!intentionalDisconnect && group != null && !group.isShuttingDown()) {
            group.schedule(this::doConnect, 5, TimeUnit.SECONDS);
        }
    }

    public void disconnect() {
        intentionalDisconnect = true;
        connected = false;
        assignedHostname = null;
        if (controlChannel != null) controlChannel.close();
        if (group != null) group.shutdownGracefully();
        group = null;
    }

    public String getHostname() {
        return assignedHostname;
    }

    public boolean isConnected() {
        return connected;
    }

    private class ControlHandler extends SimpleChannelInboundHandler<String> {

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                System.out.println("[CraftProxy] Connection dead, forcing reconnect...");
                ctx.close();
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[CraftProxy] Control Channel Error: " + cause.getMessage());
            ctx.close();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            msg = msg.trim();

            if (msg.startsWith("OK ")) {
                assignedHostname = msg.substring(3);
                if (onReadyCallback != null) onReadyCallback.accept(assignedHostname);

            } else if (msg.startsWith("INCOMING ")) {
                String[] parts = msg.split(" ");
                String sessionId = parts[1];
                String playerIp = parts.length > 2 ? parts[2] : "127.0.0.1";
                openBridge(sessionId, playerIp);
            } else if (msg.startsWith("BROADCAST ")) {
                String text = msg.substring(10);
                Minecraft mc = Minecraft.getInstance();
                if (mc.getSingleplayerServer() != null) {
                    mc.execute(() -> {
                        assert mc.getSingleplayerServer() != null;
                        mc.getSingleplayerServer().getPlayerList().broadcastSystemMessage(Component.literal(text), false);
                    });
                }

            } else if (msg.equals("SHUTDOWN")) {
                intentionalDisconnect = true;
                ctx.close();
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.player != null) {
                        ClientCompat.chat(Component.literal(prefix + "§c" + tr("server_closed").getString()));
                    }
                });
            } else if (msg.equals("PING")) {
                ctx.writeAndFlush("PONG\n");
            } else if (msg.startsWith("ERROR ")) {
                intentionalDisconnect = true;
                connected = false;
                String errorMsg = msg.substring(6);
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.player != null) {
                        ClientCompat.chat(Component.literal(prefix + "§c" + tr("error", errorMsg).getString()));
                    }
                });
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            connected = false;
            assignedHostname = null;
            if (!intentionalDisconnect) {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.player != null) {
                        ClientCompat.chat(Component.literal(prefix + "§c" + tr("reconnecting").getString()));
                    }
                });
                scheduleReconnect();
            }
        }
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(TRANSLATION_PREFIX + key, args);
    }

    private void openBridge(String sessionId, String playerIp) {
        Bootstrap bridge = new Bootstrap();
        bridge.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.attr(PLAYER_IP).set(playerIp);
                ch.pipeline().addLast(new BridgeInitHandler(sessionId));
            }
        });

        bridge.connect(SERVER_HOST, BRIDGE_PORT).addListener((ChannelFuture f) -> {
            if (!f.isSuccess()) {
                System.out.println("[CraftProxy] Bridge connect failed for session " + sessionId);
            }
        });
    }

    private class BridgeInitHandler extends ChannelInboundHandlerAdapter {

        private final String sessionId;
        private boolean identified = false;
        private final List<ByteBuf> queue = new ArrayList<>();

        BridgeInitHandler(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ByteBuf id = Unpooled.copiedBuffer(sessionId + "\n", StandardCharsets.UTF_8);
            ctx.writeAndFlush(id);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) return;

            if (!identified) {
                identified = true;

                ctx.channel().config().setAutoRead(false);

                queue.add(buf.retain());

                Bootstrap local = new Bootstrap();
                local.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.attr(PLAYER_IP).set(ctx.channel().attr(PLAYER_IP).get());
                                ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                            }
                        });

                local.connect("127.0.0.1", 25565).addListener((ChannelFuture f) -> {
                    ctx.executor().execute(() -> {
                        if (f.isSuccess()) {
                            io.netty.channel.Channel localChannel = f.channel();
                            controlChannel.writeAndFlush("READY " + sessionId + "\n");
                            ctx.pipeline().addLast(new RelayHandler(localChannel));

                            for (ByteBuf queuedMsg : queue) {
                                localChannel.writeAndFlush(queuedMsg);
                            }
                            queue.clear();

                            ctx.pipeline().remove(BridgeInitHandler.this);

                            ctx.channel().config().setAutoRead(true);
                        } else {
                            System.err.println("[CraftProxy] FATAL: Failed to connect to local Minecraft server on 127.0.0.1:25565! Cause: " + f.cause());
                            if (f.cause() != null) {
                                f.cause().printStackTrace();
                            }
                            for (ByteBuf queuedMsg : queue) {
                                queuedMsg.release();
                            }
                            queue.clear();
                            ctx.close();
                        }
                    });
                });
            } else {
                queue.add(buf.retain());
            }

            buf.release();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[CraftProxy] BridgeInitHandler Exception: " + cause);
            cause.printStackTrace();
            for (ByteBuf queuedMsg : queue) {
                queuedMsg.release();
            }
            queue.clear();
            ctx.close();
        }
    }

    private static class RelayHandler extends ChannelInboundHandlerAdapter {

        private final Channel target;

        RelayHandler(Channel target) {
            this.target = target;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (target.isActive()) {
                target.writeAndFlush(msg);
            } else {
                ((ByteBuf) msg).release();
                ctx.close();
            }
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            if (target.isActive()) {
                target.config().setAutoRead(ctx.channel().isWritable());
            }
            super.channelWritabilityChanged(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (target.isActive()) target.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[CraftProxy] RelayHandler Exception: " + cause);
            cause.printStackTrace();
            ctx.close();
        }
    }
}