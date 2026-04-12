package dev.gfortes.ccpython.network;

import dev.gfortes.ccpython.client.ClientHiFiAudioManager;
import dev.gfortes.ccpython.client.ClientPythonRuntimeState;
import dev.gfortes.ccpython.client.ClientMonitorGraphicsState;
import dev.gfortes.ccpython.network.payload.HiFiAudioChunkPayload;
import dev.gfortes.ccpython.network.payload.HiFiAudioStopPayload;
import dev.gfortes.ccpython.network.payload.MonitorGraphicsClearPayload;
import dev.gfortes.ccpython.network.payload.MonitorGraphicsFramePayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeClearPayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeErrorPayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeResetPayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeStatePayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class PythonPayloadHandler {
    private PythonPayloadHandler() {
    }

    public static void handleState(PythonRuntimeStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientPythonRuntimeState.apply(payload);
        });
    }

    public static void handleError(PythonRuntimeErrorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientPythonRuntimeState.apply(payload);
        });
    }

    public static void handleClear(PythonRuntimeClearPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientPythonRuntimeState.apply(payload);
        });
    }

    public static void handleReset(PythonRuntimeResetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientPythonRuntimeState.clearAll();
        });
    }

    public static void handleMonitorFrame(MonitorGraphicsFramePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientMonitorGraphicsState.apply(payload);
        });
    }

    public static void handleMonitorClear(MonitorGraphicsClearPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientMonitorGraphicsState.apply(payload);
        });
    }

    public static void handleHiFiAudioChunk(HiFiAudioChunkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientHiFiAudioManager.apply(payload);
        });
    }

    public static void handleHiFiAudioStop(HiFiAudioStopPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientHiFiAudioManager.stop(payload.source());
        });
    }
}
