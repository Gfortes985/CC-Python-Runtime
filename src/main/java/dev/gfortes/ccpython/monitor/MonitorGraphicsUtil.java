package dev.gfortes.ccpython.monitor;

import dan200.computercraft.shared.peripheral.monitor.MonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class MonitorGraphicsUtil {
    private MonitorGraphicsUtil() {
    }

    public static MonitorGraphicsKey key(MonitorBlockEntity monitor) {
        return new MonitorGraphicsKey(dimensionId(monitor.getLevel()), originPos(monitor));
    }

    public static String dimensionId(Level level) {
        return level == null ? "minecraft:overworld" : level.dimension().location().toString();
    }

    public static BlockPos originPos(MonitorBlockEntity monitor) {
        BlockPos position = monitor.getBlockPos();
        Direction right = monitor.getRight();
        Direction down = monitor.getDown();
        if (monitor.getXIndex() != 0) position = position.relative(right, -monitor.getXIndex());
        if (monitor.getYIndex() != 0) position = position.relative(down, -monitor.getYIndex());
        return position;
    }

    public static int pixelWidth(MonitorBlockEntity monitor) {
        return monitor.getWidth() * MonitorPalette.PIXELS_PER_BLOCK;
    }

    public static int pixelHeight(MonitorBlockEntity monitor) {
        return monitor.getHeight() * MonitorPalette.PIXELS_PER_BLOCK;
    }
}
