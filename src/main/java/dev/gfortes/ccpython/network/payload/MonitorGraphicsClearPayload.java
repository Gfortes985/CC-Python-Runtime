package dev.gfortes.ccpython.network.payload;

import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.monitor.MonitorGraphicsKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MonitorGraphicsClearPayload(String dimension, BlockPos origin) implements CustomPacketPayload {
    public static final Type<MonitorGraphicsClearPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CCPythonMod.MOD_ID, "monitor_graphics_clear")
    );

    public static final StreamCodec<ByteBuf, MonitorGraphicsClearPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            writeUtf8(buffer, payload.dimension());
            buffer.writeInt(payload.origin().getX());
            buffer.writeInt(payload.origin().getY());
            buffer.writeInt(payload.origin().getZ());
        },
        buffer -> new MonitorGraphicsClearPayload(
            readUtf8(buffer),
            new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt())
        )
    );

    public MonitorGraphicsClearPayload(MonitorGraphicsKey key) {
        this(key.dimension(), key.origin());
    }

    public MonitorGraphicsKey key() {
        return new MonitorGraphicsKey(dimension, origin);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void writeUtf8(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    private static String readUtf8(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readInt()];
        buffer.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
