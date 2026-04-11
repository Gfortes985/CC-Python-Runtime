package dev.gfortes.ccpython.mixin;

import dan200.computercraft.shared.peripheral.monitor.MonitorBlockEntity;
import dan200.computercraft.shared.peripheral.monitor.ServerMonitor;
import dev.gfortes.ccpython.monitor.MonitorGraphicsManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerMonitor.class)
public abstract class ServerMonitorMixin {
    @Shadow @Final private MonitorBlockEntity origin;

    @Inject(method = "markChanged", at = @At("HEAD"))
    private void ccpython$clearHiResOverlayWhenTextChanges(CallbackInfo ci) {
        var terminal = ((ServerMonitor) (Object) this).getTerminal();
        if (terminal == null) return;

        for (int row = 0; row < terminal.getHeight(); row++) {
            if (terminal.getLine(row).toString().trim().isEmpty()) continue;
            MonitorGraphicsManager.getInstance().disableIfPresent(origin);
            return;
        }
    }
}
