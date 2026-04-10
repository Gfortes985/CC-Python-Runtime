package dev.gfortes.ccpython.network.payload;

import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.monitor.MonitorGraphicsFrame;
import dev.gfortes.ccpython.monitor.MonitorGraphicsKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MonitorGraphicsFramePayload(
    String dimension,
    BlockPos origin,
    int blockWidth,
    int blockHeight,
    int pixelWidth,
    int pixelHeight,
    byte[] pixels
) implements CustomPacketPayload {
    public static final Type<MonitorGraphicsFramePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CCPythonMod.MOD_ID, "monitor_graphics_frame")
    );

    public static final StreamCodec<ByteBuf, MonitorGraphicsFramePayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            writeUtf8(buffer, payload.dimension());
            buffer.writeInt(payload.origin().getX());
            buffer.writeInt(payload.origin().getY());
            buffer.writeInt(payload.origin().getZ());
            buffer.writeInt(payload.blockWidth());
            buffer.writeInt(payload.blockHeight());
            buffer.writeInt(payload.pixelWidth());
            buffer.writeInt(payload.pixelHeight());
            buffer.writeInt(payload.pixels().length);
            buffer.writeBytes(payload.pixels());
        },
        buffer -> {
            String dimension = readUtf8(buffer);
            BlockPos origin = new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt());
            int blockWidth = buffer.readInt();
            int blockHeight = buffer.readInt();
            int pixelWidth = buffer.readInt();
            int pixelHeight = buffer.readInt();
            byte[] pixels = new byte[buffer.readInt()];
            buffer.readBytes(pixels);
            return new MonitorGraphicsFramePayload(dimension, origin, blockWidth, blockHeight, pixelWidth, pixelHeight, pixels);
        }
    );

    public static MonitorGraphicsFramePayload fromFrame(MonitorGraphicsFrame frame) {
        return new MonitorGraphicsFramePayload(
            frame.key().dimension(),
            frame.key().origin(),
            frame.blockWidth(),
            frame.blockHeight(),
            frame.pixelWidth(),
            frame.pixelHeight(),
            frame.pixels()
        );
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
