package dev.gfortes.ccpython.network.payload;

import dev.gfortes.ccpython.CCPythonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PythonRuntimeStatePayload(
    int computerId,
    String processId,
    String state,
    String program,
    boolean interactive,
    long startedAt,
    String detail
) implements CustomPacketPayload {
    public static final Type<PythonRuntimeStatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CCPythonMod.MOD_ID, "runtime_state")
    );

    public static final StreamCodec<ByteBuf, PythonRuntimeStatePayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            ByteBufCodecs.VAR_INT.encode(buffer, payload.computerId());
            ByteBufCodecs.STRING_UTF8.encode(buffer, payload.processId());
            ByteBufCodecs.STRING_UTF8.encode(buffer, payload.state());
            ByteBufCodecs.STRING_UTF8.encode(buffer, payload.program());
            ByteBufCodecs.BOOL.encode(buffer, payload.interactive());
            ByteBufCodecs.VAR_LONG.encode(buffer, payload.startedAt());
            ByteBufCodecs.STRING_UTF8.encode(buffer, payload.detail());
        },
        buffer -> new PythonRuntimeStatePayload(
            ByteBufCodecs.VAR_INT.decode(buffer),
            ByteBufCodecs.STRING_UTF8.decode(buffer),
            ByteBufCodecs.STRING_UTF8.decode(buffer),
            ByteBufCodecs.STRING_UTF8.decode(buffer),
            ByteBufCodecs.BOOL.decode(buffer),
            ByteBufCodecs.VAR_LONG.decode(buffer),
            ByteBufCodecs.STRING_UTF8.decode(buffer)
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
