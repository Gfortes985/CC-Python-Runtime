package dev.gfortes.ccpython.network.payload;

import dev.gfortes.ccpython.CCPythonMod;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HiFiAudioStopPayload(UUID source) implements CustomPacketPayload {
    public static final Type<HiFiAudioStopPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CCPythonMod.MOD_ID, "hifi_audio_stop")
    );

    public static final StreamCodec<ByteBuf, HiFiAudioStopPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeLong(payload.source().getMostSignificantBits());
            buffer.writeLong(payload.source().getLeastSignificantBits());
        },
        buffer -> new HiFiAudioStopPayload(new UUID(buffer.readLong(), buffer.readLong()))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
