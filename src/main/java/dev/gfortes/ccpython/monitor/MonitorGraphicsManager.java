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
    private volatile MinecraftServer loadedServer;
    private volatile MonitorGraphicsSavedData savedData;

    private MonitorGraphicsManager() {
    }

    public static MonitorGraphicsManager getInstance() {
        return INSTANCE;
    }

    public void initializeServer(MinecraftServer server) {
        ensureLoaded(server);
    }

    public int[] size(MonitorBlockEntity monitor) {
        ensureLoaded(monitor.getLevel() == null ? null : monitor.getLevel().getServer());
        return new int[] {
            MonitorGraphicsUtil.pixelWidth(monitor),
            MonitorGraphicsUtil.pixelHeight(monitor),
        };
    }

    public void disable(MonitorBlockEntity monitor) {
        ensureLoaded(monitor.getLevel() == null ? null : monitor.getLevel().getServer());
        var key = MonitorGraphicsUtil.key(monitor);
        frames.remove(key);
        if (savedData != null) savedData.remove(key);
        if (monitor.getLevel() instanceof ServerLevel level) {
            NetworkSyncManager.broadcastMonitorClear(level, key);
        }
    }

    public boolean disableIfPresent(MonitorBlockEntity monitor) {
        ensureLoaded(monitor.getLevel() == null ? null : monitor.getLevel().getServer());
        var key = MonitorGraphicsUtil.key(monitor);
        if (!frames.containsKey(key)) return false;
        disable(monitor);
        return true;
    }

    public void clear(MonitorBlockEntity monitor, int argb) {
        ensureLoaded(monitor.getLevel() == null ? null : monitor.getLevel().getServer());
        var frame = ensureFrame(monitor);
        int[] pixels = frame.pixels();
        Arrays.fill(pixels, opaque(argb));
        publish(new MonitorGraphicsFrame(frame.key(), frame.blockWidth(), frame.blockHeight(), frame.pixelWidth(), frame.pixelHeight(), pixels));
    }

    public void setPixel(MonitorBlockEntity monitor, int x, int y, int argb) {
        ensureLoaded(monitor.getLevel() == null ? null : monitor.getLevel().getServer());
        var frame = ensureFrame(monitor);
        int pixelX = x - 1;
        int pixelY = y - 1;
        if (pixelX < 0 || pixelY < 0 || pixelX >= frame.pixelWidth() || pixelY >= frame.pixelHeight()) return;
        int[] pixels = frame.pixels();
        pixels[pixelY * frame.pixelWidth() + pixelX] = opaque(argb);
        publish(new MonitorGraphicsFrame(frame.key(), frame.blockWidth(), frame.blockHeight(), frame.pixelWidth(), frame.pixelHeight(), pixels));
    }

    public void drawImage(MonitorBlockEntity monitor, ManagedImage image, int x, int y, boolean clearFirst) {
        ensureLoaded(monitor.getLevel() == null ? null : monitor.getLevel().getServer());
        var frame = ensureFrame(monitor);
        int[] pixels = frame.pixels();
        if (clearFirst) Arrays.fill(pixels, MonitorPalette.argb(15));

        int[] source = image.toArgbPixels();
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
        ensureLoaded(loadedServer);
        return new ArrayList<>(frames.values());
    }

    public void syncPlayer(ServerPlayer player) {
        ensureLoaded(player == null ? null : player.server);
        if (player == null || player.server == null) return;
        NetworkSyncManager.syncMonitorFrames(player, activeFrames());
    }

    public void shutdownServer(MinecraftServer server) {
        frames.clear();
        savedData = null;
        loadedServer = null;
    }

    private MonitorGraphicsFrame ensureFrame(MonitorBlockEntity monitor) {
        var key = MonitorGraphicsUtil.key(monitor);
        return frames.compute(key, (ignored, existing) -> {
            int blockWidth = monitor.getWidth();
            int blockHeight = monitor.getHeight();
            int pixelWidth = MonitorGraphicsUtil.pixelWidth(monitor);
            int pixelHeight = MonitorGraphicsUtil.pixelHeight(monitor);
            if (existing != null && existing.pixelWidth() == pixelWidth && existing.pixelHeight() == pixelHeight) return existing;
            clearTerminalLayer(monitor);
            return new MonitorGraphicsFrame(key, blockWidth, blockHeight, pixelWidth, pixelHeight, new int[pixelWidth * pixelHeight]);
        });
    }

    private void publish(MonitorGraphicsFrame frame) {
        frames.put(frame.key(), frame);
        if (savedData != null) savedData.put(frame);
        NetworkSyncManager.broadcastMonitorFrame(frame);
    }

    private void ensureLoaded(MinecraftServer server) {
        if (server == null) return;
        if (loadedServer == server && savedData != null) return;

        synchronized (this) {
            if (loadedServer == server && savedData != null) return;
            loadedServer = server;
            savedData = MonitorGraphicsSavedData.get(server);
            frames.clear();
            for (var frame : savedData.frames()) {
                frames.put(frame.key(), frame);
            }
        }
    }

    private static void clearTerminalLayer(MonitorBlockEntity monitor) {
        if (monitor == null) return;
        var serverMonitor = monitor.getCachedServerMonitor();
        if (serverMonitor == null) return;
        var terminal = serverMonitor.getTerminal();
        if (terminal == null) return;
        terminal.reset();
    }

    private static int opaque(int argb) {
        return (argb & 0xFF00_0000) == 0 ? (0xFF00_0000 | (argb & 0x00FF_FFFF)) : argb;
    }
}
