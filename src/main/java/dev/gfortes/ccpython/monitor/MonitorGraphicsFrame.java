package dev.gfortes.ccpython.monitor;

public record MonitorGraphicsFrame(
    MonitorGraphicsKey key,
    int blockWidth,
    int blockHeight,
    int pixelWidth,
    int pixelHeight,
    int[] pixels
) {
    public MonitorGraphicsFrame {
        pixels = pixels == null ? new int[0] : pixels.clone();
    }

    @Override
    public int[] pixels() {
        return pixels.clone();
    }
}
