package dev.gfortes.ccpython.client;

import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.client.ClientMonitorGraphicsState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = CCPythonMod.MOD_ID, value = Dist.CLIENT)
public final class ClientNetworkLifecycle {
    private ClientNetworkLifecycle() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPythonRuntimeState.clearAll();
        ClientMonitorGraphicsState.clearAll();
        ClientHiFiAudioManager.clearAll();
    }
}
