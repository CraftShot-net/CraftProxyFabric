package net.craftproxy.mod.client.images;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Reads images from the system clipboard. GLFW/SDL only expose text and AWT is headless in
 * Minecraft, so this talks to the OS directly: the Win32 clipboard through JNA (shipped with
 * Minecraft), {@code osascript} on macOS and {@code wl-paste}/{@code xclip} on Linux.
 */
public final class ClipboardImages {
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean WINDOWS = OS.contains("win");
    private static final boolean MAC = OS.contains("mac");

    private ClipboardImages() {
    }

    /**
     * Cheap check whether a paste should be treated as an image paste. {@code clipboardText} is the
     * text Minecraft sees; outside Windows we can only guess "image" when there is no text.
     */
    public static boolean mayHaveImage(String clipboardText) {
        if (WINDOWS) {
            try {
                return Win.hasImage();
            } catch (Throwable e) {
                return false;
            }
        }
        return clipboardText == null || clipboardText.isEmpty();
    }

    /** Raw image bytes (any format {@link ImageCodec} understands) from the clipboard; empty if there are none. */
    public static CompletableFuture<List<byte[]>> read() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (WINDOWS) return Win.read();
                if (MAC) return single(readMac());
                return single(readLinux());
            } catch (Throwable e) {
                System.out.println("[CraftProxy] Could not read clipboard image: " + e.getMessage());
                return List.of();
            }
        });
    }

    private static List<byte[]> single(byte[] data) {
        return data == null || data.length == 0 ? List.of() : List.of(data);
    }

    private static boolean isImageFile(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp");
    }

    // --- macOS / Linux ---

    private static byte[] readMac() throws IOException, InterruptedException {
        Path file = Files.createTempFile("craftproxy-clipboard", ".png");
        try {
            String target = file.toAbsolutePath().toString().replace("\"", "\\\"");
            Process process = new ProcessBuilder("osascript",
                    "-e", "set png to (the clipboard as «class PNGf»)",
                    "-e", "set f to open for access POSIX file \"" + target + "\" with write permission",
                    "-e", "write png to f",
                    "-e", "close access f")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) return null;
            return Files.size(file) > MAX_BYTES ? null : Files.readAllBytes(file);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static byte[] readLinux() throws IOException, InterruptedException {
        boolean wayland = System.getenv("WAYLAND_DISPLAY") != null;
        ProcessBuilder builder = wayland
                ? new ProcessBuilder("wl-paste", "--no-newline", "--type", "image/png")
                : new ProcessBuilder("xclip", "-selection", "clipboard", "-t", "image/png", "-o");
        Process process = builder.redirectError(ProcessBuilder.Redirect.DISCARD).start();
        byte[] data;
        try (InputStream in = process.getInputStream()) {
            data = in.readNBytes(MAX_BYTES);
        }
        if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) return null;
        return data;
    }

    // --- Windows ---

    private static final class Win {
        private static final int CF_DIB = 8;
        private static final int CF_HDROP = 15;
        // Browsers and most image tools put a "PNG" format next to the DIB, which keeps transparency.
        private static final int CF_PNG = User32.INSTANCE.RegisterClipboardFormatW(new WString("PNG"));

        interface User32 extends Library {
            User32 INSTANCE = Native.load("user32", User32.class);

            boolean OpenClipboard(Pointer owner);

            boolean CloseClipboard();

            boolean IsClipboardFormatAvailable(int format);

            Pointer GetClipboardData(int format);

            int RegisterClipboardFormatW(WString name);
        }

        interface Kernel32 extends Library {
            Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

            Pointer GlobalLock(Pointer memory);

            boolean GlobalUnlock(Pointer memory);

            long GlobalSize(Pointer memory);
        }

        interface Shell32 extends Library {
            Shell32 INSTANCE = Native.load("shell32", Shell32.class);

            int DragQueryFileW(Pointer drop, int index, char[] file, int length);
        }

        static boolean hasImage() {
            User32 user32 = User32.INSTANCE;
            return (CF_PNG != 0 && user32.IsClipboardFormatAvailable(CF_PNG))
                    || user32.IsClipboardFormatAvailable(CF_DIB)
                    || user32.IsClipboardFormatAvailable(CF_HDROP);
        }

        static List<byte[]> read() throws IOException, InterruptedException {
            List<Path> files = new ArrayList<>();
            byte[] image = null;
            if (!open()) return List.of();
            try {
                User32 user32 = User32.INSTANCE;
                if (user32.IsClipboardFormatAvailable(CF_HDROP)) {
                    files = droppedFiles(user32.GetClipboardData(CF_HDROP));
                }
                if (files.isEmpty() && CF_PNG != 0 && user32.IsClipboardFormatAvailable(CF_PNG)) {
                    image = globalBytes(user32.GetClipboardData(CF_PNG));
                }
                if (image == null && files.isEmpty() && user32.IsClipboardFormatAvailable(CF_DIB)) {
                    byte[] dib = globalBytes(user32.GetClipboardData(CF_DIB));
                    if (dib != null) image = dibToBmp(dib);
                }
            } finally {
                User32.INSTANCE.CloseClipboard();
            }

            // Files copied in Explorer: read them after releasing the clipboard.
            List<byte[]> result = new ArrayList<>();
            if (image != null) result.add(image);
            for (Path file : files) {
                if (isImageFile(file) && Files.isRegularFile(file) && Files.size(file) <= MAX_BYTES) {
                    result.add(Files.readAllBytes(file));
                }
            }
            return result;
        }

        /** Another program may hold the clipboard for a moment; retry briefly. */
        private static boolean open() throws InterruptedException {
            for (int attempt = 0; attempt < 10; attempt++) {
                if (User32.INSTANCE.OpenClipboard(null)) return true;
                Thread.sleep(20);
            }
            return false;
        }

        private static byte[] globalBytes(Pointer handle) {
            if (handle == null) return null;
            long size = Kernel32.INSTANCE.GlobalSize(handle);
            if (size <= 0 || size > MAX_BYTES) return null;
            Pointer data = Kernel32.INSTANCE.GlobalLock(handle);
            if (data == null) return null;
            try {
                return data.getByteArray(0, (int) size);
            } finally {
                Kernel32.INSTANCE.GlobalUnlock(handle);
            }
        }

        private static List<Path> droppedFiles(Pointer drop) {
            List<Path> files = new ArrayList<>();
            if (drop == null) return files;
            int count = Shell32.INSTANCE.DragQueryFileW(drop, -1, null, 0);
            for (int i = 0; i < count; i++) {
                int length = Shell32.INSTANCE.DragQueryFileW(drop, i, null, 0);
                char[] buffer = new char[length + 1];
                Shell32.INSTANCE.DragQueryFileW(drop, i, buffer, buffer.length);
                files.add(Path.of(new String(buffer, 0, length)));
            }
            return files;
        }

        /** CF_DIB is a BMP file without its 14-byte file header; prepend one so ImageIO can read it. */
        private static byte[] dibToBmp(byte[] dib) {
            if (dib.length < 40) return null;
            ByteBuffer in = ByteBuffer.wrap(dib).order(ByteOrder.LITTLE_ENDIAN);
            int headerSize = in.getInt(0);
            int bitCount = in.getShort(14) & 0xFFFF;
            int compression = in.getInt(16);
            int colorsUsed = in.getInt(32);
            // BI_BITFIELDS / BI_ALPHABITFIELDS store their masks right after a plain 40-byte header.
            int masks = headerSize == 40 ? (compression == 3 ? 12 : compression == 6 ? 16 : 0) : 0;
            int colors = colorsUsed != 0 ? colorsUsed : (bitCount <= 8 ? 1 << bitCount : 0);
            int pixelOffset = 14 + headerSize + masks + colors * 4;

            ByteBuffer out = ByteBuffer.allocate(14 + dib.length).order(ByteOrder.LITTLE_ENDIAN);
            out.put((byte) 'B').put((byte) 'M').putInt(14 + dib.length).putInt(0).putInt(pixelOffset).put(dib);
            return out.array();
        }
    }
}
