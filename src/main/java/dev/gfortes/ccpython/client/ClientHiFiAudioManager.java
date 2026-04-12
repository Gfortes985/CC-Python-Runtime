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
        private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        private final Thread worker;

        private volatile boolean closed;
        private volatile long lastReceiveMs;

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
            byte[] bytes = scaleSamples(payload);
            if (bytes.length == 0) return;

            queue.offer(bytes);
        }

        private void run() {
            try {
                while (!closed) {
                    byte[] next = queue.poll(250L, TimeUnit.MILLISECONDS);
                    if (next != null) {
                        line.write(next, 0, next.length);
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

        private static SourceDataLine openLine(int sampleRate) throws LineUnavailableException {
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            int bufferSize = Math.max(65_536, sampleRate * 4);
            line.open(format, bufferSize);
            line.start();
            return line;
        }

        private static byte[] scaleSamples(HiFiAudioChunkPayload payload) {
            var minecraft = Minecraft.getInstance();
            var player = minecraft.player;
            if (player == null) return new byte[0];

            double maxDistance = Math.max(1.0, payload.volume() * 16.0);
            double distance = player.position().distanceTo(new net.minecraft.world.phys.Vec3(payload.x(), payload.y(), payload.z()));
            if (distance >= maxDistance) return new byte[0];

            double attenuation = 1.0 - (distance / maxDistance);
            attenuation *= attenuation;
            if (attenuation <= 0.0001) return new byte[0];

            short[] samples = payload.samples();
            byte[] output = new byte[samples.length * 2];
            for (int index = 0; index < samples.length; index++) {
                int scaled = Mth.clamp((int) Math.round(samples[index] * attenuation), (int) Short.MIN_VALUE, (int) Short.MAX_VALUE);
                int byteIndex = index * 2;
                output[byteIndex] = (byte) (scaled & 0xFF);
                output[byteIndex + 1] = (byte) ((scaled >>> 8) & 0xFF);
            }
            return output;
        }
    }
}
