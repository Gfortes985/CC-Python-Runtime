package dev.gfortes.ccpython.client;

import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.network.payload.HiFiAudioChunkPayload;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class ClientHiFiAudioManager {
    private static final long IDLE_TIMEOUT_MS = 10_000L;
    private static final int ATTENUATION_BLOCK_SAMPLES = 1_024;
    private static final Map<UUID, StreamState> STREAMS = new ConcurrentHashMap<>();
    private static final Set<UUID> LOGGED_RECEIVED_STREAMS = ConcurrentHashMap.newKeySet();

    private ClientHiFiAudioManager() {
    }

    public static void apply(HiFiAudioChunkPayload payload) {
        if (LOGGED_RECEIVED_STREAMS.add(payload.source())) {
            CCPythonMod.LOGGER.info(
                "Received first hi-fi audio chunk for speaker stream {} at {} Hz with {} samples.",
                payload.source(),
                payload.sampleRate(),
                payload.samples().length
            );
        }
        StreamState state = STREAMS.compute(payload.source(), (source, current) -> {
            if (current != null && current.sampleRate == payload.sampleRate()) return current;
            if (current != null) current.close();
            try {
                return new StreamState(source, payload.sampleRate());
            } catch (LineUnavailableException exception) {
                CCPythonMod.LOGGER.warn("Failed to open hi-fi audio output for speaker stream {}.", source, exception);
                return null;
            }
        });

        if (state == null) return;
        state.enqueue(payload);
    }

    public static void stop(UUID source) {
        LOGGED_RECEIVED_STREAMS.remove(source);
        StreamState state = STREAMS.remove(source);
        if (state != null) state.close();
    }

    public static void clearAll() {
        LOGGED_RECEIVED_STREAMS.clear();
        var states = new ArrayList<>(STREAMS.values());
        STREAMS.clear();
        for (var state : states) state.close();
    }

    private static final class StreamState {
        private final UUID source;
        private final int sampleRate;
        private final SourceDataLine line;
        private final LinkedBlockingQueue<PendingChunk> queue = new LinkedBlockingQueue<>();
        private final Thread worker;

        private volatile boolean closed;
        private volatile long lastReceiveMs;
        private double currentGain;

        private StreamState(UUID source, int sampleRate) throws LineUnavailableException {
            this.source = source;
            this.sampleRate = sampleRate;
            this.line = openLine(sampleRate);
            this.lastReceiveMs = System.currentTimeMillis();
            CCPythonMod.LOGGER.info("Opened hi-fi audio output for speaker stream {} at {} Hz.", source, sampleRate);
            this.worker = new Thread(this::run, "ccpython-hifi-" + source);
            this.worker.setDaemon(true);
            this.worker.start();
        }

        private void enqueue(HiFiAudioChunkPayload payload) {
            if (closed) return;

            lastReceiveMs = System.currentTimeMillis();
            queue.offer(new PendingChunk(payload.x(), payload.y(), payload.z(), payload.volume(), payload.samples()));
        }

        private void run() {
            try {
                while (!closed) {
                    PendingChunk next = queue.poll(250L, TimeUnit.MILLISECONDS);
                    if (next != null) {
                        writeChunk(next);
                        continue;
                    }

                    if (queue.isEmpty() && System.currentTimeMillis() - lastReceiveMs > IDLE_TIMEOUT_MS) break;
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                CCPythonMod.LOGGER.warn("Hi-fi audio stream {} failed.", source, exception);
            } finally {
                closeLine();
                STREAMS.remove(source, this);
            }
        }

        private void close() {
            closed = true;
            worker.interrupt();
            closeLine();
        }

        private void closeLine() {
            synchronized (line) {
                try {
                    line.flush();
                } catch (RuntimeException ignored) {
                }
                try {
                    line.stop();
                } catch (RuntimeException ignored) {
                }
                try {
                    line.close();
                } catch (RuntimeException ignored) {
                }
            }
        }

        private void writeChunk(PendingChunk chunk) {
            short[] samples = chunk.samples();
            if (samples.length == 0) return;

            for (int offset = 0; offset < samples.length && !closed; offset += ATTENUATION_BLOCK_SAMPLES) {
                int length = Math.min(ATTENUATION_BLOCK_SAMPLES, samples.length - offset);
                double targetGain = computeAttenuation(chunk);
                byte[] bytes = scaleSamples(samples, offset, length, currentGain, targetGain);
                currentGain = targetGain;
                line.write(bytes, 0, bytes.length);
            }
        }

        private static SourceDataLine openLine(int sampleRate) throws LineUnavailableException {
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            int bufferSize = Math.max(65_536, sampleRate * 4);
            line.open(format, bufferSize);
            line.start();
            return line;
        }

        private static double computeAttenuation(PendingChunk chunk) {
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null) return 0.0;

            double maxDistance = Math.max(1.0, chunk.volume() * 16.0);
            double distance = player.position().distanceTo(new net.minecraft.world.phys.Vec3(chunk.x(), chunk.y(), chunk.z()));
            if (distance >= maxDistance) return 0.0;

            double normalized = Mth.clamp(1.0 - (distance / maxDistance), 0.0, 1.0);
            return normalized * normalized * (3.0 - (2.0 * normalized));
        }

        private static byte[] scaleSamples(short[] samples, int offset, int length, double fromGain, double toGain) {
            byte[] output = new byte[length * 2];
            if (length <= 0) return output;

            for (int index = 0; index < length; index++) {
                double progress = length <= 1 ? 1.0 : (double) index / (double) (length - 1);
                double gain = fromGain + ((toGain - fromGain) * progress);
                int scaled = Mth.clamp((int) Math.round(samples[offset + index] * gain), (int) Short.MIN_VALUE, (int) Short.MAX_VALUE);
                int byteIndex = index * 2;
                output[byteIndex] = (byte) (scaled & 0xFF);
                output[byteIndex + 1] = (byte) ((scaled >>> 8) & 0xFF);
            }
            return output;
        }
    }

    private record PendingChunk(double x, double y, double z, float volume, short[] samples) {
    }
}
