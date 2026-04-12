package dev.gfortes.ccpython.network;

import dev.gfortes.ccpython.network.payload.HiFiAudioChunkPayload;
import dev.gfortes.ccpython.network.payload.HiFiAudioStopPayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeClearPayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeErrorPayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeResetPayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeStatePayload;
import dev.gfortes.ccpython.network.payload.MonitorGraphicsClearPayload;
import dev.gfortes.ccpython.network.payload.MonitorGraphicsFramePayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PacketHandler {
    private static final String PROTOCOL = "1";

    private PacketHandler() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);

        registrar.playToClient(
            PythonRuntimeStatePayload.TYPE,
            PythonRuntimeStatePayload.STREAM_CODEC,
            PythonPayloadHandler::handleState
        );
        registrar.playToClient(
            PythonRuntimeErrorPayload.TYPE,
            PythonRuntimeErrorPayload.STREAM_CODEC,
            PythonPayloadHandler::handleError
        );
        registrar.playToClient(
            PythonRuntimeClearPayload.TYPE,
            PythonRuntimeClearPayload.STREAM_CODEC,
            PythonPayloadHandler::handleClear
        );
        registrar.playToClient(
            PythonRuntimeResetPayload.TYPE,
            PythonRuntimeResetPayload.STREAM_CODEC,
            PythonPayloadHandler::handleReset
        );
        registrar.playToClient(
            MonitorGraphicsFramePayload.TYPE,
            MonitorGraphicsFramePayload.STREAM_CODEC,
            PythonPayloadHandler::handleMonitorFrame
        );
        registrar.playToClient(
            MonitorGraphicsClearPayload.TYPE,
            MonitorGraphicsClearPayload.STREAM_CODEC,
            PythonPayloadHandler::handleMonitorClear
        );
        registrar.playToClient(
            HiFiAudioChunkPayload.TYPE,
            HiFiAudioChunkPayload.STREAM_CODEC,
            PythonPayloadHandler::handleHiFiAudioChunk
        );
        registrar.playToClient(
            HiFiAudioStopPayload.TYPE,
            HiFiAudioStopPayload.STREAM_CODEC,
            PythonPayloadHandler::handleHiFiAudioStop
        );
    }
}
