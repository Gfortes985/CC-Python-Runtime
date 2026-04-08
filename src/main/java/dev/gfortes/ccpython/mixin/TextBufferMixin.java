package dev.gfortes.ccpython.mixin;

import dan200.computercraft.core.terminal.TextBuffer;
import dev.gfortes.ccpython.charset.CCTerminalCharset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextBuffer.class)
public final class TextBufferMixin {
    @ModifyVariable(method = "<init>(Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static String ccpython$mapConstructedString(String value) {
        return CCTerminalCharset.mapTerminalString(value);
    }

    @ModifyVariable(method = "write(Ljava/lang/String;I)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String ccpython$mapWrittenString(String value) {
        return CCTerminalCharset.mapTerminalString(value);
    }
}
