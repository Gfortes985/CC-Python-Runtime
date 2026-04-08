package dev.gfortes.ccpython.network.payload;

import dev.gfortes.ccpython.CCPythonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PythonRuntimeClearPayload(int computerId, String processId) implements CustomPacketPayload {
    public static final Type<PythonRuntimeClearPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CCPythonMod.MOD_ID, "runtime_clear")
    );

    public static final StreamCodec<ByteBuf, PythonRuntimeClearPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        PythonRuntimeClearPayload::computerId,
        ByteBufCodecs.STRING_UTF8,
        PythonRuntimeClearPayload::processId,
        PythonRuntimeClearPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
