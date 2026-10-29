package net.craftproxy.mod.client.images;

import net.craftproxy.mod.client.chat.ChatAttachment;
import net.craftproxy.mod.client.chat.ChatSendException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

/**
 * Decoding, downscaling and re-encoding of chat images with ImageIO (works in Minecraft's
 * headless AWT mode). Never call these on the render thread: large images take a while.
 */
public final class ImageCodec {
    /** Refuse anything bigger than 16k x 8k; such files are decompression bombs, not screenshots. */
    private static final long MAX_PIXELS = 128L * 1024 * 1024;
    /** Smallest longest side an upload is shrunk to before giving up on the size limit. */
    private static final int MIN_UPLOAD_SIDE = 640;

    private ImageCodec() {
    }

    /** Decoded image scaled to fit {@code maxSide}, plus the size of the original. */
    public record Decoded(BufferedImage image, int sourceWidth, int sourceHeight) {
    }

    /** Detects the format from the magic bytes: png, jpeg, gif, webp, bmp or {@code null}. */
    public static String detectFormat(byte[] data) {
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') return "png";
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) return "jpeg";
        if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') return "gif";
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') return "webp";
        if (data.length >= 2 && data[0] == 'B' && data[1] == 'M') return "bmp";
        return null;
    }

    /**
     * Decodes the first frame and scales it to fit {@code maxSide}. Large images are subsampled
     * while reading, so a 4K screenshot never needs a full-size buffer.
     */
    public static Decoded decode(byte[] data, int maxSide) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("Unsupported image format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("Image too large: " + width + "x" + height);
                }
                ImageReadParam param = reader.getDefaultReadParam();
                // Subsample to roughly twice the target, the smooth scaling below does the rest.
                int step = Math.max(1, Math.max(width, height) / Math.max(1, maxSide * 2));
                if (step > 1) param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = reader.read(0, param);
                return new Decoded(scaleToFit(image, maxSide), width, height);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Scales down (never up) in halving steps, which looks much better than one bilinear pass. */
    public static BufferedImage scaleToFit(BufferedImage source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        double factor = Math.min(1.0, (double) maxSide / Math.max(width, height));
        int targetW = Math.max(1, (int) Math.round(width * factor));
        int targetH = Math.max(1, (int) Math.round(height * factor));

        BufferedImage current = source.getType() == BufferedImage.TYPE_INT_ARGB ? source : resize(source, width, height);
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        if (current.getWidth() != targetW || current.getHeight() != targetH) {
            current = resize(current, targetW, targetH);
        }
        return current;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    public static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) throw new IOException("No PNG writer");
        return out.toByteArray();
    }

    private static byte[] toJpeg(BufferedImage image, float quality) throws IOException {
        // JPEG has no alpha: flatten onto the dark chat background instead of black garbage.
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(new Color(0x111318));
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /**
     * Turns raw file or clipboard bytes into an attachment the backend accepts: png/jpeg/gif/webp
     * within {@code maxBytes} are sent unchanged, everything else is re-encoded and shrunk if needed.
     *
     * @throws ChatSendException {@code UNSUPPORTED} for non-images, {@code TOO_LARGE} when it cannot fit
     */
    public static ChatAttachment prepareUpload(byte[] data, int maxBytes) {
        String format = detectFormat(data);
        if (format == null) {
            throw new ChatSendException(ChatSendException.Reason.UNSUPPORTED, "Not an image");
        }
        if (data.length <= maxBytes && !format.equals("bmp")) {
            return attachment(data, format);
        }
        // Animated GIFs and WebP cannot be re-encoded without losing what makes them special.
        if (format.equals("gif") || format.equals("webp")) {
            throw new ChatSendException(ChatSendException.Reason.TOO_LARGE, "Image too large");
        }
        try {
            Decoded decoded = decode(data, Integer.MAX_VALUE / 4);
            return fromImage(decoded.image(), maxBytes);
        } catch (IOException e) {
            throw new ChatSendException(ChatSendException.Reason.UNSUPPORTED, e.getMessage());
        }
    }

    /** Encodes a decoded image (e.g. from the clipboard): PNG if it fits, else JPEG, shrinking as needed. */
    public static ChatAttachment fromImage(BufferedImage image, int maxBytes) {
        try {
            byte[] png = toPng(image);
            if (png.length <= maxBytes) return attachment(png, "png");
            BufferedImage current = image;
            while (true) {
                byte[] jpeg = toJpeg(current, 0.9F);
                if (jpeg.length <= maxBytes) return attachment(jpeg, "jpeg");
                int side = Math.max(current.getWidth(), current.getHeight());
                if (side <= MIN_UPLOAD_SIDE) break;
                current = scaleToFit(current, Math.max(MIN_UPLOAD_SIDE, side * 3 / 4));
            }
        } catch (IOException e) {
            throw new ChatSendException(ChatSendException.Reason.FAILED, e.getMessage());
        }
        throw new ChatSendException(ChatSendException.Reason.TOO_LARGE, "Image too large");
    }

    private static ChatAttachment attachment(byte[] data, String format) {
        String extension = format.equals("jpeg") ? "jpg" : format;
        String id = UUID.randomUUID().toString().replace("-", "");
        return new ChatAttachment(id, "image-" + id.substring(0, 8) + "." + extension, "image/" + format, data);
    }
}
