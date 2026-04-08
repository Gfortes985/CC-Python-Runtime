package dev.gfortes.ccpython.network.payload;

import dev.gfortes.ccpython.CCPythonMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PythonRuntimeResetPayload() implements CustomPacketPayload {
    public static final Type<PythonRuntimeResetPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CCPythonMod.MOD_ID, "runtime_reset")
    );

    public static final StreamCodec<ByteBuf, PythonRuntimeResetPayload> STREAM_CODEC = StreamCodec.unit(new PythonRuntimeResetPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
