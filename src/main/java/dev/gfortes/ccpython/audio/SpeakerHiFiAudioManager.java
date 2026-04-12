package dev.gfortes.ccpython.audio;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaTable;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import dev.gfortes.ccpython.network.NetworkSyncManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

public final class SpeakerHiFiAudioManager {
    public static final String BUFFER_EVENT = "speaker_audio16_empty";
    public static final String DRAIN_EVENT = "speaker_audio16_done";
    public static final int SAMPLE_RATE = 48_000;
    public static final int MAX_CALL_SAMPLES = 131_072;

    private static final int MAX_BUFFER_SAMPLES = SAMPLE_RATE * 30;
    private static final int LOW_WATER_SAMPLES = SAMPLE_RATE * 10;
    private static final long STALE_NANOS = 5_000_000_000L;
    private static final Map<UUID, StreamState> STATES = new HashMap<>();

    private SpeakerHiFiAudioManager() {
    }

    public static short[] decodeSamples(LuaTable<?, ?> audio) throws LuaException {
        int size = audio.length();
        if (size <= 0) throw new LuaException("Cannot play empty hi-fi audio");
        if (size > MAX_CALL_SAMPLES) throw new LuaException("Hi-fi audio data is too large");

        short[] samples = new short[size];
        for (int index = 0; index < size; index++) {
            long value = audio.getLong(index + 1);
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw new LuaException("Hi-fi audio sample at index " + (index + 1) + " must be between -32768 and 32767");
            }
            samples[index] = (short) value;
        }
        return samples;
    }

    public static boolean play(ServerLevel level, SpeakerPosition position, UUID source, short[] samples, double volume) throws LuaException {
        if (samples.length == 0) throw new LuaException("Cannot play empty hi-fi audio");
        if (samples.length > MAX_CALL_SAMPLES) throw new LuaException("Hi-fi audio data is too large");
        double audibleVolume = normalizeAudibleVolume(volume);

        synchronized (SpeakerHiFiAudioManager.class) {
            long now = System.nanoTime();
            StreamState state = STATES.computeIfAbsent(source, ignored -> new StreamState(now));
            state.drain(now);

            if (state.bufferedSamples + samples.length > MAX_BUFFER_SAMPLES) {
                state.waitingForRoom = true;
                return false;
            }

            state.bufferedSamples += samples.length;
            state.lastActivityNanos = now;
        }

        NetworkSyncManager.broadcastHiFiAudioChunk(level, position, source, SAMPLE_RATE, (float) audibleVolume, samples);
        return true;
    }

    public static boolean awaitDrain(UUID source) {
        synchronized (SpeakerHiFiAudioManager.class) {
            StreamState state = STATES.get(source);
            if (state == null) return true;

            long now = System.nanoTime();
            state.drain(now);
            if (state.bufferedSamples <= 0L) {
                STATES.remove(source);
                return true;
            }

            state.waitingForDrain = true;
            return false;
        }
    }

    public static UpdateResult update(UUID source) {
        synchronized (SpeakerHiFiAudioManager.class) {
            StreamState state = STATES.get(source);
            if (state == null) return UpdateResult.NONE;

            long now = System.nanoTime();
            state.drain(now);

            boolean notifyRoom = false;
            if (state.waitingForRoom && state.bufferedSamples <= LOW_WATER_SAMPLES) {
                state.waitingForRoom = false;
                notifyRoom = true;
            }

            boolean notifyDrain = false;
            if (state.waitingForDrain && state.bufferedSamples <= 0L) {
                state.waitingForDrain = false;
                notifyDrain = true;
                STATES.remove(source);
                return new UpdateResult(notifyRoom, notifyDrain);
            }

            if (state.bufferedSamples <= 0 && now - state.lastActivityNanos > STALE_NANOS) {
                STATES.remove(source);
            }

            return new UpdateResult(notifyRoom, notifyDrain);
        }
    }

    public static void stop(ServerLevel level, UUID source) {
        synchronized (SpeakerHiFiAudioManager.class) {
            STATES.remove(source);
        }
        if (level != null) NetworkSyncManager.broadcastHiFiAudioStop(level, source);
    }

    public static void clearServer() {
        synchronized (SpeakerHiFiAudioManager.class) {
            STATES.clear();
        }
    }

    private static double normalizeAudibleVolume(double volume) throws LuaException {
        if (!Double.isFinite(volume)) throw new LuaException("volume must be finite");
        if (volume < 0.0) throw new LuaException("volume must be >= 0");
        return Math.min(3.0, volume);
    }

    private static final class StreamState {
        private long bufferedSamples;
        private long lastUpdateNanos;
        private long lastActivityNanos;
        private boolean waitingForRoom;
        private boolean waitingForDrain;

        private StreamState(long now) {
            lastUpdateNanos = now;
            lastActivityNanos = now;
        }

        private void drain(long now) {
            long elapsed = now - lastUpdateNanos;
            if (elapsed <= 0L) return;

            long consumed = (elapsed * SAMPLE_RATE) / 1_000_000_000L;
            if (consumed > 0L) bufferedSamples = Math.max(0L, bufferedSamples - consumed);
            lastUpdateNanos = now;
        }
    }

    public record UpdateResult(boolean notifyRoom, boolean notifyDrain) {
        private static final UpdateResult NONE = new UpdateResult(false, false);
    }
}
