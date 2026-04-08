package dev.gfortes.ccpython.mixin;

import dan200.computercraft.core.util.StringUtil;
import dev.gfortes.ccpython.charset.CCTerminalCharset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StringUtil.class)
public final class StringUtilMixin {
    @Inject(method = "unicodeToTerminal", at = @At("HEAD"), cancellable = true)
    private static void ccpython$unicodeToTerminal(int codePoint, CallbackInfoReturnable<Integer> cir) {
        int mapped = CCTerminalCharset.unicodeToTerminal(codePoint);
        if (mapped >= 0) cir.setReturnValue(mapped);
    }

    @Inject(method = "normaliseLabel", at = @At("HEAD"), cancellable = true)
    private static void ccpython$normaliseLabel(String label, CallbackInfoReturnable<String> cir) {
        if (CCTerminalCharset.isEnabled()) cir.setReturnValue(CCTerminalCharset.normaliseLabel(label));
    }
}
