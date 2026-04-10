package dev.gfortes.ccpython.monitor;

import dan200.computercraft.shared.peripheral.monitor.MonitorBlockEntity;
import dev.gfortes.ccpython.network.NetworkSyncManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class MonitorGraphicsManager {
    private static final MonitorGraphicsManager INSTANCE = new MonitorGraphicsManager();

    private final Map<MonitorGraphicsKey, MonitorGraphicsFrame> frames = new ConcurrentHashMap<>();

    private MonitorGraphicsManager() {
    }

    public static MonitorGraphicsManager getInstance() {
        return INSTANCE;
    }

    public int[] size(MonitorBlockEntity monitor) {
        return new int[] {
            MonitorGraphicsUtil.pixelWidth(monitor),
            MonitorGraphicsUtil.pixelHeight(monitor),
        };
    }

    public void disable(MonitorBlockEntity monitor) {
        var key = MonitorGraphicsUtil.key(monitor);
        frames.remove(key);
        if (monitor.getLevel() instanceof ServerLevel level) {
            NetworkSyncManager.broadcastMonitorClear(level, key);
        }
    }

    public void clear(MonitorBlockEntity monitor, int argb) {
        var frame = ensureFrame(monitor);
        byte[] pixels = frame.pixels();
        Arrays.fill(pixels, MonitorPalette.nearestIndex(argb));
        publish(new MonitorGraphicsFrame(frame.key(), frame.blockWidth(), frame.blockHeight(), frame.pixelWidth(), frame.pixelHeight(), pixels));
    }

    public void setPixel(MonitorBlockEntity monitor, int x, int y, int argb) {
        var frame = ensureFrame(monitor);
        int pixelX = x - 1;
        int pixelY = y - 1;
        if (pixelX < 0 || pixelY < 0 || pixelX >= frame.pixelWidth() || pixelY >= frame.pixelHeight()) return;
        byte[] pixels = frame.pixels();
        pixels[pixelY * frame.pixelWidth() + pixelX] = MonitorPalette.nearestIndex(argb);
        publish(new MonitorGraphicsFrame(frame.key(), frame.blockWidth(), frame.blockHeight(), frame.pixelWidth(), frame.pixelHeight(), pixels));
    }

    public void drawImage(MonitorBlockEntity monitor, ManagedImage image, int x, int y, boolean clearFirst) {
        var frame = ensureFrame(monitor);
        byte[] pixels = frame.pixels();
        if (clearFirst) Arrays.fill(pixels, (byte) 15);

        byte[] source = image.toMonitorIndices();
        int startX = x - 1;
        int startY = y - 1;
        for (int row = 0; row < image.height(); row++) {
            int destY = startY + row;
            if (destY < 0 || destY >= frame.pixelHeight()) continue;
            for (int column = 0; column < image.width(); column++) {
                int destX = startX + column;
                if (destX < 0 || destX >= frame.pixelWidth()) continue;
                pixels[destY * frame.pixelWidth() + destX] = source[row * image.width() + column];
            }
        }

        publish(new MonitorGraphicsFrame(frame.key(), frame.blockWidth(), frame.blockHeight(), frame.pixelWidth(), frame.pixelHeight(), pixels));
    }

    public Collection<MonitorGraphicsFrame> activeFrames() {
        return new ArrayList<>(frames.values());
    }

    public void syncPlayer(ServerPlayer player) {
        if (player == null || player.server == null) return;
        NetworkSyncManager.syncMonitorFrames(player, activeFrames());
    }

    public void shutdownServer(MinecraftServer server) {
        frames.clear();
    }

    private MonitorGraphicsFrame ensureFrame(MonitorBlockEntity monitor) {
        var key = MonitorGraphicsUtil.key(monitor);
        return frames.compute(key, (ignored, existing) -> {
            int blockWidth = monitor.getWidth();
            int blockHeight = monitor.getHeight();
            int pixelWidth = MonitorGraphicsUtil.pixelWidth(monitor);
            int pixelHeight = MonitorGraphicsUtil.pixelHeight(monitor);
            if (existing != null && existing.pixelWidth() == pixelWidth && existing.pixelHeight() == pixelHeight) return existing;
            return new MonitorGraphicsFrame(key, blockWidth, blockHeight, pixelWidth, pixelHeight, new byte[pixelWidth * pixelHeight]);
        });
    }

    private void publish(MonitorGraphicsFrame frame) {
        frames.put(frame.key(), frame);
        NetworkSyncManager.broadcastMonitorFrame(frame);
    }
}
