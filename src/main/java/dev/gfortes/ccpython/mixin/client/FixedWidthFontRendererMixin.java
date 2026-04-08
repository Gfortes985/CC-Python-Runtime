package dev.gfortes.ccpython.mixin.client;

import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(FixedWidthFontRenderer.class)
public final class FixedWidthFontRendererMixin {
    @ModifyConstant(method = "drawChar", constant = @Constant(intValue = 1, ordinal = 0))
    private static int ccpython$drawCharXOffset(int value) {
        return 0;
    }

    @ModifyConstant(method = "drawChar", constant = @Constant(intValue = 1, ordinal = 1))
    private static int ccpython$drawCharYOffset(int value) {
        return 0;
    }

    @ModifyConstant(method = "drawChar", constant = @Constant(intValue = 11))
    private static int ccpython$drawCharCellHeight(int value) {
        return 8;
    }

    @ModifyConstant(method = "drawChar", constant = @Constant(floatValue = 6.0F))
    private static float ccpython$drawCharWidth(float value) {
        return 8.0F;
    }

    @ModifyConstant(method = "drawChar", constant = @Constant(floatValue = 9.0F))
    private static float ccpython$drawCharHeight(float value) {
        return 8.0F;
    }

    @ModifyConstant(method = "drawChar", constant = @Constant(intValue = 6))
    private static int ccpython$drawCharUWidth(int value) {
        return 8;
    }

    @ModifyConstant(method = "drawChar", constant = @Constant(intValue = 9))
    private static int ccpython$drawCharVHeight(int value) {
        return 8;
    }

    @ModifyConstant(method = "drawBackground", constant = @Constant(intValue = 6), require = 5, allow = 5)
    private static int ccpython$drawBackgroundCellWidth(int value) {
        return 8;
    }

    @ModifyConstant(method = "drawString", constant = @Constant(intValue = 6))
    private static int ccpython$drawStringCellWidth(int value) {
        return 8;
    }

    @ModifyConstant(method = "drawTerminalForeground", constant = @Constant(intValue = 9))
    private static int ccpython$drawForegroundCellHeight(int value) {
        return 8;
    }

    @ModifyConstant(method = "drawTerminalBackground", constant = @Constant(intValue = 9), require = 2, allow = 2)
    private static int ccpython$drawTerminalBackgroundCellHeight(int value) {
        return 8;
    }

    @ModifyConstant(method = "drawTerminalBackground", constant = @Constant(floatValue = 9.0F))
    private static float ccpython$drawTerminalBackgroundRowHeight(float value) {
        return 8.0F;
    }

    @ModifyConstant(method = "drawCursor", constant = @Constant(intValue = 6))
    private static int ccpython$drawCursorCellWidth(int value) {
        return 8;
    }

    @ModifyConstant(method = "drawCursor", constant = @Constant(intValue = 9))
    private static int ccpython$drawCursorCellHeight(int value) {
        return 8;
    }
}
