package dev.gfortes.ccpython.network;

import dev.gfortes.ccpython.client.ClientPythonRuntimeState;
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
}
