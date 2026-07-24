package net.craftproxy.mod.client.rpc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BiConsumer;

public class CraftProxyDiscordIPC {

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;

    private static RandomAccessFile pipe;
    private static Thread readThread;

    private static volatile boolean receivedDispatch = false;
    private static volatile JsonObject queuedActivity;

    private static Runnable onReady;
    private static BiConsumer<Integer, String> onError = (code, message) -> System.err.println("[CraftProxy] Discord IPC error " + code + ": " + message);

    private CraftProxyDiscordIPC() {
    }

    public static void setOnError(BiConsumer<Integer, String> handler) {
        onError = handler;
    }

    /**
     * Tries to open a connection to a locally running Discord instance.
     *
     * @param appId   the application id to use
     * @param onReady callback invoked once the handshake completes
     * @return true if a pipe was opened successfully
     */
    public static synchronized boolean start(long appId, Runnable onReady) {
        if (pipe != null) {
            return true;
        }

        CraftProxyDiscordIPC.onReady = onReady;
        receivedDispatch = false;
        queuedActivity = null;

        for (int i = 0; i < 10; i++) {
            String pipeName = "\\\\.\\pipe\\discord-ipc-" + i;
            try {
                pipe = new RandomAccessFile(pipeName, "rw");
                break;
            } catch (IOException ignored) {
                // try the next pipe index
            }
        }

        if (pipe == null) {
            return false;
        }

        JsonObject handshake = new JsonObject();
        handshake.addProperty("v", 1);
        handshake.addProperty("client_id", Long.toString(appId));
        writeFrame(OP_HANDSHAKE, handshake);

        readThread = new Thread(CraftProxyDiscordIPC::readLoop, "CraftProxy Discord IPC Read Thread");
        readThread.setDaemon(true);
        readThread.start();

        return true;
    }

    /**
     * Sets the account's activity. Safe to call before the handshake completes; it will be queued.
     */
    public static synchronized void setActivity(RichPresenceJson presence) {
        if (pipe == null) {
            return;
        }

        queuedActivity = presence.toJson();
        if (receivedDispatch) {
            sendQueuedActivity();
        }
    }

    public static synchronized void stop() {
        if (pipe == null) {
            return;
        }

        try {
            pipe.close();
        } catch (IOException ignored) {
        }

        pipe = null;
        readThread = null;
        onReady = null;
        receivedDispatch = false;
        queuedActivity = null;
    }

    private static void sendQueuedActivity() {
        JsonObject args = new JsonObject();
        args.addProperty("pid", getPid());
        args.add("activity", queuedActivity);

        JsonObject o = new JsonObject();
        o.addProperty("cmd", "SET_ACTIVITY");
        o.add("args", args);

        writeFrame(OP_FRAME, o);
        queuedActivity = null;
    }

    private static synchronized void writeFrame(int opcode, JsonObject o) {
        if (pipe == null) {
            return;
        }

        o.addProperty("nonce", UUID.randomUUID().toString());

        byte[] data = o.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(data.length + 8);
        buffer.putInt(Integer.reverseBytes(opcode));
        buffer.putInt(Integer.reverseBytes(data.length));
        buffer.put(data);

        try {
            pipe.write(buffer.array());
        } catch (IOException e) {
            System.out.println("[CraftProxy] Failed writing to Discord pipe: " + e.getMessage());
        }
    }

    private static void readLoop() {
        try {
            while (true) {
                int opcode = Integer.reverseBytes(readInt());
                int length = Integer.reverseBytes(readInt());

                byte[] data = new byte[length];
                readFully(data);

                String json = new String(data, StandardCharsets.UTF_8);
                onPacket(opcode, JsonParser.parseString(json).getAsJsonObject());
            }
        } catch (Exception e) {
            System.out.println("[CraftProxy] Discord IPC connection closed: " + e.getMessage());
            stop();
        }
    }

    private static int readInt() throws IOException {
        byte[] bytes = new byte[4];
        readFully(bytes);
        return ByteBuffer.wrap(bytes).getInt();
    }

    private static void readFully(byte[] out) throws IOException {
        int offset = 0;

        while (offset < out.length) {
            while (pipe.length() <= offset) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    throw new IOException("Interrupted while waiting for pipe data", e);
                }
            }

            int read = pipe.read(out, offset, out.length - offset);
            if (read < 0) {
                throw new IOException("Pipe closed");
            }
            offset += read;
        }
    }

    private static void onPacket(int opcode, JsonObject data) {
        if (opcode == OP_CLOSE) {
            int code = data.has("code") ? data.get("code").getAsInt() : -1;
            String message = data.has("message") ? data.get("message").getAsString() : "connection closed";
            onError.accept(code, message);
            stop();
        } else if (opcode == OP_FRAME) {
            if (data.has("evt") && "ERROR".equals(data.get("evt").getAsString())) {
                JsonObject d = data.getAsJsonObject("data");
                onError.accept(d.get("code").getAsInt(), d.get("message").getAsString());
            } else if (data.has("cmd") && "DISPATCH".equals(data.get("cmd").getAsString())) {
                receivedDispatch = true;

                if (onReady != null) {
                    onReady.run();
                }
                if (queuedActivity != null) {
                    sendQueuedActivity();
                }
            }
        }
    }

    private static int getPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        return Integer.parseInt(name.substring(0, name.indexOf('@')));
    }

    public interface RichPresenceJson {
        JsonObject toJson();
    }
}