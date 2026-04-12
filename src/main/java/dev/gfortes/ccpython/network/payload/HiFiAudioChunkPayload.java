package dev.gfortes.ccpython.network.payload;

import dev.gfortes.ccpython.CCPythonMod;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HiFiAudioChunkPayload(
    UUID source,
    double x,
    double y,
    double z,
    float volume,
    int sampleRate,
    short[] samples
) implements CustomPacketPayload {
    public static final Type<HiFiAudioChunkPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(CCPythonMod.MOD_ID, "hifi_audio_chunk")
    );

    public static final StreamCodec<ByteBuf, HiFiAudioChunkPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeLong(payload.source().getMostSignificantBits());
            buffer.writeLong(payload.source().getLeastSignificantBits());
            buffer.writeDouble(payload.x());
            buffer.writeDouble(payload.y());
            buffer.writeDouble(payload.z());
            buffer.writeFloat(payload.volume());
            buffer.writeInt(payload.sampleRate());
            buffer.writeInt(payload.samples().length);
            for (short sample : payload.samples()) buffer.writeShort(sample);
        },
        buffer -> {
            UUID source = new UUID(buffer.readLong(), buffer.readLong());
            double x = buffer.readDouble();
            double y = buffer.readDouble();
            double z = buffer.readDouble();
            float volume = buffer.readFloat();
            int sampleRate = buffer.readInt();
            short[] samples = new short[buffer.readInt()];
            for (int index = 0; index < samples.length; index++) samples[index] = buffer.readShort();
            return new HiFiAudioChunkPayload(source, x, y, z, volume, sampleRate, samples);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
