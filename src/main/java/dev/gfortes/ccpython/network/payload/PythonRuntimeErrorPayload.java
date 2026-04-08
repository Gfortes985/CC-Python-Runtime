package dev.gfortes.ccpython.network.payload;

import dev.gfortes.ccpython.CCPythonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PythonRuntimeErrorPayload(int computerId, String processId, String traceback)
    implements CustomPacketPayload {
    public static final Type<PythonRuntimeErrorPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CCPythonMod.MOD_ID, "runtime_error")
    );

    public static final StreamCodec<ByteBuf, PythonRuntimeErrorPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        PythonRuntimeErrorPayload::computerId,
        ByteBufCodecs.STRING_UTF8,
        PythonRuntimeErrorPayload::processId,
        ByteBufCodecs.STRING_UTF8,
        PythonRuntimeErrorPayload::traceback,
        PythonRuntimeErrorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
