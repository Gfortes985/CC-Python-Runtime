package dev.gfortes.ccpython.mixin;

import dan200.computercraft.core.computer.Computer;
import dev.gfortes.ccpython.runtime.PythonRuntimeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Computer.class)
public abstract class ComputerMixin {
    @Shadow
    public abstract int getID();

    @Inject(method = "queueEvent", at = @At("TAIL"))
    private void ccpython$queueEvent(String event, Object[] arguments, CallbackInfo ci) {
        PythonRuntimeManager.getInstance().queueEvent(getID(), event, arguments);
    }
}
