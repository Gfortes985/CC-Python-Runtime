package dev.gfortes.ccpython.monitor;

public record MonitorGraphicsFrame(
    MonitorGraphicsKey key,
    int blockWidth,
    int blockHeight,
    int pixelWidth,
    int pixelHeight,
    byte[] pixels
) {
    public MonitorGraphicsFrame {
        pixels = pixels == null ? new byte[0] : pixels.clone();
    }

    @Override
    public byte[] pixels() {
        return pixels.clone();
    }
}
