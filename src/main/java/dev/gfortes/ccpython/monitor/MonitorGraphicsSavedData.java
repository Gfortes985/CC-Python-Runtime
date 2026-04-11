package dev.gfortes.ccpython.monitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class MonitorGraphicsSavedData extends SavedData {
    public static final String DATA_NAME = "ccpython_monitor_graphics";
    public static final Factory<MonitorGraphicsSavedData> FACTORY = new Factory<>(
        MonitorGraphicsSavedData::new,
        MonitorGraphicsSavedData::load
    );

    private final Map<MonitorGraphicsKey, MonitorGraphicsFrame> frames = new LinkedHashMap<>();

    public static MonitorGraphicsSavedData get(MinecraftServer server) {
        if (server == null || server.overworld() == null) return new MonitorGraphicsSavedData();
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static MonitorGraphicsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new MonitorGraphicsSavedData();
        ListTag framesTag = tag.getList("frames", Tag.TAG_COMPOUND);
        for (int index = 0; index < framesTag.size(); index++) {
            CompoundTag frameTag = framesTag.getCompound(index);
            var key = new MonitorGraphicsKey(
                frameTag.getString("dimension"),
                BlockPos.of(frameTag.getLong("origin"))
            );
            var frame = new MonitorGraphicsFrame(
                key,
                frameTag.getInt("block_width"),
                frameTag.getInt("block_height"),
                frameTag.getInt("pixel_width"),
                frameTag.getInt("pixel_height"),
                readPixels(frameTag)
            );
            data.frames.put(key, frame);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var framesTag = new ListTag();
        for (var frame : frames.values()) {
            var frameTag = new CompoundTag();
            frameTag.putString("dimension", frame.key().dimension());
            frameTag.putLong("origin", frame.key().origin().asLong());
            frameTag.putInt("block_width", frame.blockWidth());
            frameTag.putInt("block_height", frame.blockHeight());
            frameTag.putInt("pixel_width", frame.pixelWidth());
            frameTag.putInt("pixel_height", frame.pixelHeight());
            frameTag.putIntArray("pixels_argb", frame.pixels());
            framesTag.add(frameTag);
        }
        tag.put("frames", framesTag);
        return tag;
    }

    public Collection<MonitorGraphicsFrame> frames() {
        return new ArrayList<>(frames.values());
    }

    public void put(MonitorGraphicsFrame frame) {
        frames.put(frame.key(), frame);
        setDirty();
    }

    public void remove(MonitorGraphicsKey key) {
        if (frames.remove(key) != null) setDirty();
    }

    private static int[] readPixels(CompoundTag frameTag) {
        if (frameTag.contains("pixels_argb", Tag.TAG_INT_ARRAY)) {
            return frameTag.getIntArray("pixels_argb");
        }

        byte[] legacy = frameTag.getByteArray("pixels");
        int[] converted = new int[legacy.length];
        for (int index = 0; index < legacy.length; index++) {
            converted[index] = MonitorPalette.argb(Byte.toUnsignedInt(legacy[index]));
        }
        return converted;
    }
}
