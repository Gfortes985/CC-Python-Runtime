package dev.gfortes.ccpython.mixin.client;

import dan200.computercraft.client.gui.widgets.TerminalWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(TerminalWidget.class)
public final class TerminalWidgetMixin {
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 6), require = 2, allow = 2)
    private static int ccpython$constructorCellWidth(int value) {
        return 8;
    }

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 9), require = 2, allow = 2)
    private static int ccpython$constructorCellHeight(int value) {
        return 8;
    }

    @ModifyConstant(
        method = { "mouseClicked", "mouseReleased", "mouseDragged", "mouseScrolled" },
        constant = @Constant(doubleValue = 6.0D),
        require = 4,
        allow = 4
    )
    private double ccpython$mouseCellWidth(double value) {
        return 8.0D;
    }

    @ModifyConstant(
        method = { "mouseClicked", "mouseReleased", "mouseDragged", "mouseScrolled" },
        constant = @Constant(doubleValue = 9.0D),
        require = 4,
        allow = 4
    )
    private double ccpython$mouseCellHeight(double value) {
        return 8.0D;
    }

    @ModifyConstant(method = "getWidth", constant = @Constant(intValue = 6))
    private static int ccpython$getWidthCellWidth(int value) {
        return 8;
    }

    @ModifyConstant(method = "getHeight", constant = @Constant(intValue = 9))
    private static int ccpython$getHeightCellHeight(int value) {
        return 8;
    }
}
