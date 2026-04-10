package dev.gfortes.ccpython.monitor;

import com.mojang.blaze3d.platform.NativeImage;
import dev.gfortes.ccpython.CCPythonMod;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.imageio.ImageIO;

public final class ManagedImage {
    private static final long MAX_DOWNLOAD_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_IMAGE_PIXELS = 4_194_304L;

    private final BufferedImage image;

    private ManagedImage(BufferedImage image) {
        this.image = image;
    }

    public static ManagedImage fromBytes(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IOException("Image data is empty.");
        if (bytes.length > MAX_DOWNLOAD_BYTES) throw new IOException("Image exceeds the configured download size limit.");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IOException("Unsupported image format.");
        validateSize(image.getWidth(), image.getHeight());
        return new ManagedImage(toArgb(image));
    }

    public static ManagedImage fromUrl(String url, Map<String, String> headers, int timeoutSeconds) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IOException("Only http and https image URLs are supported.");
        }

        int timeout = Math.max(1, timeoutSeconds);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofSeconds(timeout))
            .header("User-Agent", "CCPythonMod/0.1.0");
        if (headers != null) {
            for (var entry : headers.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(timeout))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        int code = response.statusCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " while downloading image.");
        return fromBytes(response.body());
    }

    public int width() {
        return image.getWidth();
    }

    public int height() {
        return image.getHeight();
    }

    public BufferedImage image() {
        return image;
    }

    public ManagedImage resize(int width, int height, String resample) throws IOException {
        validateSize(width, height);
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            applyResampleHints(graphics, resample);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return new ManagedImage(resized);
    }

    public ManagedImage quantizeToMonitorPalette(boolean dither) {
        int width = width();
        int height = height();
        BufferedImage quantized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (!dither) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    quantized.setRGB(x, y, MonitorPalette.argb(Byte.toUnsignedInt(MonitorPalette.nearestIndex(image.getRGB(x, y)))));
                }
            }
            return new ManagedImage(quantized);
        }

        double[] red = new double[width * height];
        double[] green = new double[width * height];
        double[] blue = new double[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int index = y * width + x;
                red[index] = (argb >>> 16) & 0xFF;
                green[index] = (argb >>> 8) & 0xFF;
                blue[index] = argb & 0xFF;
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int argb = rgba(clamp(red[index]), clamp(green[index]), clamp(blue[index]));
                int paletteIndex = Byte.toUnsignedInt(MonitorPalette.nearestIndex(argb));
                int paletteArgb = MonitorPalette.argb(paletteIndex);
                quantized.setRGB(x, y, paletteArgb);

                double errorR = red[index] - ((paletteArgb >>> 16) & 0xFF);
                double errorG = green[index] - ((paletteArgb >>> 8) & 0xFF);
                double errorB = blue[index] - (paletteArgb & 0xFF);

                diffuse(red, green, blue, width, height, x + 1, y, errorR, errorG, errorB, 7.0 / 16.0);
                diffuse(red, green, blue, width, height, x - 1, y + 1, errorR, errorG, errorB, 3.0 / 16.0);
                diffuse(red, green, blue, width, height, x, y + 1, errorR, errorG, errorB, 5.0 / 16.0);
                diffuse(red, green, blue, width, height, x + 1, y + 1, errorR, errorG, errorB, 1.0 / 16.0);
            }
        }

        return new ManagedImage(quantized);
    }

    public byte[] toMonitorIndices() {
        int width = width();
        int height = height();
        byte[] pixels = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = MonitorPalette.nearestIndex(image.getRGB(x, y));
            }
        }
        return pixels;
    }

    public NativeImage toNativeImage() {
        NativeImage nativeImage = new NativeImage(width(), height(), false);
        for (int y = 0; y < height(); y++) {
            for (int x = 0; x < width(); x++) {
                nativeImage.setPixelRGBA(x, y, image.getRGB(x, y));
            }
        }
        return nativeImage;
    }

    private static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) return source;
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private static void validateSize(int width, int height) throws IOException {
        if (width <= 0 || height <= 0) throw new IOException("Image dimensions must be positive.");
        long pixels = (long) width * (long) height;
        if (pixels > MAX_IMAGE_PIXELS) throw new IOException("Image exceeds the configured pixel budget.");
    }

    private static void applyResampleHints(Graphics2D graphics, String resample) {
        String mode = resample == null ? "bilinear" : resample.toLowerCase(Locale.ROOT);
        Object interpolation = switch (mode) {
            case "nearest", "nearest_neighbor", "pixel" -> RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
            case "bicubic" -> RenderingHints.VALUE_INTERPOLATION_BICUBIC;
            default -> RenderingHints.VALUE_INTERPOLATION_BILINEAR;
        };
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    private static void diffuse(double[] red, double[] green, double[] blue, int width, int height, int x, int y, double er, double eg, double eb, double factor) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        int index = y * width + x;
        red[index] += er * factor;
        green[index] += eg * factor;
        blue[index] += eb * factor;
    }

    private static int clamp(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static int rgba(int red, int green, int blue) {
        return 0xFF00_0000 | (red << 16) | (green << 8) | blue;
    }
}
