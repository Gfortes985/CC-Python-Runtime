package dev.gfortes.ccpython.network;

import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.monitor.MonitorGraphicsFrame;
import dev.gfortes.ccpython.monitor.MonitorGraphicsKey;
import dev.gfortes.ccpython.network.payload.MonitorGraphicsClearPayload;
import dev.gfortes.ccpython.network.payload.MonitorGraphicsFramePayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeClearPayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeErrorPayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeResetPayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeStatePayload;
import java.util.Collection;
import dev.gfortes.ccpython.runtime.PythonStatusSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NetworkSyncManager {
    private static final PythonRuntimeResetPayload RESET_PAYLOAD = new PythonRuntimeResetPayload();

    private NetworkSyncManager() {
    }

    public static void broadcastState(ServerLevel level, PythonStatusSnapshot snapshot) {
        var payload = new PythonRuntimeStatePayload(
            snapshot.computerId(),
            snapshot.processId(),
            snapshot.state().name().toLowerCase(),
            snapshot.program(),
            snapshot.interactive(),
            snapshot.startedAt(),
            snapshot.detail() == null ? "" : snapshot.detail()
        );

        enqueue(level, () -> broadcast(level, payload));
    }

    public static void broadcastError(ServerLevel level, int computerId, String processId, String traceback) {
        var payload = new PythonRuntimeErrorPayload(computerId, processId, traceback);
        enqueue(level, () -> broadcast(level, payload));
    }

    public static void broadcastClear(ServerLevel level, int computerId, String processId) {
        var payload = new PythonRuntimeClearPayload(computerId, processId);
        enqueue(level, () -> broadcast(level, payload));
    }

    public static void broadcastMonitorFrame(MonitorGraphicsFrame frame) {
        var payload = MonitorGraphicsFramePayload.fromFrame(frame);
        enqueue(frame.key().dimension(), payload);
    }

    public static void broadcastMonitorClear(ServerLevel level, MonitorGraphicsKey key) {
        enqueue(level, () -> broadcast(level, new MonitorGraphicsClearPayload(key)));
    }

    public static void syncPlayer(ServerPlayer player, Collection<PythonStatusSnapshot> snapshots) {
        if (player == null || player.server == null) return;

        player.server.execute(() -> {
            try {
                PacketDistributor.sendToPlayer(player, RESET_PAYLOAD);
                for (var snapshot : snapshots) {
                    sendState(player, snapshot);
                }
            } catch (RuntimeException exception) {
                CCPythonMod.LOGGER.warn("Failed to synchronize Python runtime state to player {}.", player.getGameProfile().getName(), exception);
            }
        });
    }

    public static void syncMonitorFrames(ServerPlayer player, Collection<MonitorGraphicsFrame> frames) {
        if (player == null || player.server == null) return;

        player.server.execute(() -> {
            try {
                for (var frame : frames) {
                    PacketDistributor.sendToPlayer(player, MonitorGraphicsFramePayload.fromFrame(frame));
                }
            } catch (RuntimeException exception) {
                CCPythonMod.LOGGER.warn("Failed to synchronize monitor graphics state to player {}.", player.getGameProfile().getName(), exception);
            }
        });
    }

    private static void enqueue(ServerLevel level, Runnable action) {
        if (level == null || level.getServer() == null) return;

        level.getServer().execute(() -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                CCPythonMod.LOGGER.warn("Failed to synchronize Python runtime state to clients.", exception);
            }
        });
    }

    private static void enqueue(String dimensionId, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        server.execute(() -> {
            try {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.level().dimension().location().toString().equals(dimensionId)) {
                        PacketDistributor.sendToPlayer(player, payload);
                    }
                }
            } catch (RuntimeException exception) {
                CCPythonMod.LOGGER.warn("Failed to synchronize monitor graphics state to clients.", exception);
            }
        });
    }

    private static void sendState(ServerPlayer player, PythonStatusSnapshot snapshot) {
        var payload = new PythonRuntimeStatePayload(
            snapshot.computerId(),
            snapshot.processId(),
            snapshot.state().name().toLowerCase(),
            snapshot.program(),
            snapshot.interactive(),
            snapshot.startedAt(),
            snapshot.detail() == null ? "" : snapshot.detail()
        );
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void broadcast(ServerLevel level, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        var server = level.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
