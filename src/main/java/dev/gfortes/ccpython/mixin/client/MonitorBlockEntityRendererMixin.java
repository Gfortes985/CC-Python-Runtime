package dev.gfortes.ccpython.mixin.client;

import dan200.computercraft.client.render.monitor.MonitorBlockEntityRenderer;
import dan200.computercraft.shared.peripheral.monitor.MonitorBlockEntity;
import dan200.computercraft.shared.peripheral.monitor.MonitorRenderer;
import dev.gfortes.ccpython.client.ClientMonitorGraphicsState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MonitorBlockEntityRenderer.class)
public final class MonitorBlockEntityRendererMixin {
    @ModifyConstant(method = "render", constant = @Constant(intValue = 6))
    private static int ccpython$renderCellWidth(int value) {
        return 8;
    }

    @ModifyConstant(method = "render", constant = @Constant(intValue = 9))
    private static int ccpython$renderCellHeight(int value) {
        return 8;
    }

    @ModifyConstant(method = "renderTerminal", constant = @Constant(intValue = 6))
    private static int ccpython$renderTerminalCellWidth(int value) {
        return 8;
    }

    @ModifyConstant(method = "renderTerminal", constant = @Constant(intValue = 9))
    private static int ccpython$renderTerminalCellHeight(int value) {
        return 8;
    }

    @Inject(method = "currentRenderer", at = @At("HEAD"), cancellable = true)
    private static void ccpython$forceVboRenderer(CallbackInfoReturnable<MonitorRenderer> cir) {
        cir.setReturnValue(MonitorRenderer.VBO);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ccpython$renderHiResOverlay(
        MonitorBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay,
        CallbackInfo ci
    ) {
        ClientMonitorGraphicsState.renderOverlay(blockEntity, poseStack, bufferSource, packedLight);
    }
}
