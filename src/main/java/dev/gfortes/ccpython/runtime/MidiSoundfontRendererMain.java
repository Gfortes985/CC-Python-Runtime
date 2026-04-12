package dev.gfortes.ccpython.runtime;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

public final class MidiSoundfontRendererMain {
    private static final double HIFI_TARGET_PEAK = 0.28;
    private static final double HIFI_MAX_GAIN = 32.0;
    private static final double HIFI_HIGH_PASS_HZ = 160.0;
    private static final double HIFI_LOW_PASS_HZ = 3_600.0;

    private MidiSoundfontRendererMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.err.println("Usage: <midiPath> <soundfontPath> <outputPath> <tempoScale> <volume> <transpose> [pcm8|pcm16]");
            System.exit(2);
            return;
        }

        Path midiPath = Path.of(args[0]);
        Path soundfontPath = Path.of(args[1]);
        Path outputPath = Path.of(args[2]);
        double tempoScale = Double.parseDouble(args[3]);
        double volume = Double.parseDouble(args[4]);
        int transpose = Integer.parseInt(args[5]);
        String outputMode = args.length >= 7 ? args[6].trim().toLowerCase() : "pcm8";
        boolean hiFi = switch (outputMode) {
            case "pcm8" -> false;
            case "pcm16" -> true;
            default -> throw new IllegalArgumentException("Unsupported output mode: " + outputMode);
        };

        if (tempoScale <= 0.0) {
            System.err.println("tempoScale must be > 0");
            System.exit(2);
            return;
        }

        Sequence sequence = MidiSystem.getSequence(midiPath.toFile());
        var soundbank = MidiSystem.getSoundbank(soundfontPath.toFile());
        var format = new AudioFormat(48_000.0f, 16, 1, true, false);

        long totalMicros;
        long totalFrames;
        long writtenSamples = 0L;
        double peak = 0.0;
        double gain = 1.0;
        double filterState = 0.0;

        Synthesizer synth = createSoftSynthesizer();
        try {
            try (AudioInputStream stream = openSoftSynthStream(synth, format)) {
                var defaultSoundbank = synth.getDefaultSoundbank();
                if (defaultSoundbank != null) synth.unloadAllInstruments(defaultSoundbank);
                synth.loadAllInstruments(soundbank);

                try (Receiver receiver = synth.getReceiver()) {
                    totalMicros = sendSequence(receiver, sequence, tempoScale, transpose);
                }

                totalFrames = Math.max(1L, Math.round(((totalMicros / 1_000_000.0) + 1.5) * format.getFrameRate()));
                long bytesRemaining = totalFrames * format.getFrameSize();
                byte[] byteBuffer = new byte[8_192 * format.getFrameSize()];

                try (var output = new BufferedOutputStream(Files.newOutputStream(outputPath))) {
                    while (bytesRemaining > 0) {
                        int toRead = (int) Math.min(byteBuffer.length, bytesRemaining);
                        int read = readFully(stream, byteBuffer, toRead);
                        if (read <= 0) break;
                        bytesRemaining -= read;

                        for (int offset = 0; offset + 1 < read; offset += format.getFrameSize()) {
                            int sample = (short) ((byteBuffer[offset] & 0xFF) | (byteBuffer[offset + 1] << 8));
                            double normalized = (sample / 32768.0) * volume;
                            double clipped;
                            if (hiFi) {
                                clipped = midiSoftClip(normalized);
                            } else {
                                filterState += 0.35 * (normalized - filterState);
                                clipped = midiSoftClip(filterState);
                            }
                            peak = Math.max(peak, Math.abs(clipped));
                            if (hiFi) {
                                int pcm = (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(clipped * 32767.0)));
                                output.write(pcm & 0xFF);
                                output.write((pcm >>> 8) & 0xFF);
                            } else {
                                int pcm = (int) Math.max(-128, Math.min(127, Math.round(clipped * 127.0)));
                                output.write(pcm);
                            }
                            writtenSamples++;
                        }
                    }
                }
            }
        } finally {
            synth.close();
        }

        if (hiFi) {
            gain = computeHiFiMakeupGain(peak);
            if (gain > 1.0001) peak = normalizePcm16File(outputPath, gain);
        }

        try (PrintWriter writer = new PrintWriter(System.out, true)) {
            writer.println("SAMPLES=" + writtenSamples);
            writer.println("PEAK=" + peak);
            writer.println("GAIN=" + gain);
        }
    }

    private static Synthesizer createSoftSynthesizer() throws Exception {
        Class<?> synthClass = Class.forName("com.sun.media.sound.SoftSynthesizer");
        Object instance = synthClass.getConstructor().newInstance();
        if (instance instanceof Synthesizer synth) return synth;
        throw new IllegalStateException("SoftSynthesizer is not a Synthesizer");
    }

    private static AudioInputStream openSoftSynthStream(Synthesizer synth, AudioFormat format) throws Exception {
        Method method = synth.getClass().getMethod("openStream", AudioFormat.class, Map.class);
        Object stream = method.invoke(synth, format, null);
        if (stream instanceof AudioInputStream audioStream) return audioStream;
        throw new IllegalStateException("SoftSynthesizer did not return an AudioInputStream");
    }

    private static long sendSequence(Receiver receiver, Sequence sequence, double tempoScale, int transpose) throws InvalidMidiDataException {
        sendInitialChannelState(receiver);

        var events = new ArrayList<TimedSequenceEvent>();
        int order = 0;
        for (var track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                var event = track.get(i);
                events.add(new TimedSequenceEvent(event.getTick(), midiPriority(event.getMessage()), order++, event.getMessage()));
            }
        }

        events.sort(Comparator
            .comparingLong(TimedSequenceEvent::tick)
            .thenComparingInt(TimedSequenceEvent::priority)
            .thenComparingInt(TimedSequenceEvent::order));

        long currentTick = 0L;
        double currentMicros = 0.0;
        int division = Math.max(1, sequence.getResolution());
        int tempo = 500_000;
        long lastMicros = 0L;

        for (var event : events) {
            long tick = event.tick();
            long deltaTicks = tick - currentTick;
            if (deltaTicks > 0) {
                currentMicros += (deltaTicks * (double) tempo) / (division * tempoScale);
                currentTick = tick;
            }

            var message = event.message();
            if (message instanceof MetaMessage meta) {
                if (meta.getType() == 0x51) {
                    byte[] data = meta.getData();
                    if (data.length == 3) tempo = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                }
                continue;
            }

            MidiMessage output = transposeMessage(message, transpose);
            long timestamp = Math.max(0L, Math.round(currentMicros));
            receiver.send(output, timestamp);
            lastMicros = Math.max(lastMicros, timestamp);
        }

        return lastMicros;
    }

    private static void sendInitialChannelState(Receiver receiver) throws InvalidMidiDataException {
        for (int channel = 0; channel < 16; channel++) {
            sendControlChange(receiver, channel, 64, 0, 0L);
            sendControlChange(receiver, channel, 91, 0, 0L);
            sendControlChange(receiver, channel, 93, 0, 0L);
        }
    }

    private static void sendControlChange(Receiver receiver, int channel, int controller, int value, long timestamp) throws InvalidMidiDataException {
        var message = new ShortMessage();
        message.setMessage(ShortMessage.CONTROL_CHANGE, channel, controller, value);
        receiver.send(message, timestamp);
    }

    private static int midiPriority(MidiMessage message) {
        if (message instanceof MetaMessage meta && meta.getType() == 0x51) return 0;
        if (message instanceof ShortMessage shortMessage) {
            int command = shortMessage.getCommand();
            if (command == ShortMessage.PROGRAM_CHANGE) return 1;
            if (command == ShortMessage.NOTE_OFF) return 2;
            if (command == ShortMessage.NOTE_ON) return shortMessage.getData2() == 0 ? 2 : 3;
        }
        return 4;
    }

    private static MidiMessage transposeMessage(MidiMessage message, int transpose) throws InvalidMidiDataException {
        if (!(message instanceof ShortMessage shortMessage) || transpose == 0) return message;

        int command = shortMessage.getCommand();
        if ((command != ShortMessage.NOTE_ON && command != ShortMessage.NOTE_OFF) || shortMessage.getChannel() == 9) {
            return message;
        }

        int note = Math.max(0, Math.min(127, shortMessage.getData1() + transpose));
        var transposed = new ShortMessage();
        transposed.setMessage(command, shortMessage.getChannel(), note, shortMessage.getData2());
        return transposed;
    }

    private static int readFully(AudioInputStream stream, byte[] buffer, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = stream.read(buffer, total, length - total);
            if (read <= 0) break;
            total += read;
        }
        return total;
    }

    private static double midiSoftClip(double sample) {
        if (-1.0 <= sample && sample <= 1.0) return sample;
        if (sample > 0.0) return sample / (1.0 + sample);
        return sample / (1.0 - sample);
    }

    private static double computeHiFiMakeupGain(double peak) {
        if (!(peak > 0.0)) return 1.0;
        double gain = HIFI_TARGET_PEAK / peak;
        return Math.max(1.0, Math.min(HIFI_MAX_GAIN, gain));
    }

    private static double normalizePcm16File(Path outputPath, double gain) throws IOException {
        Path normalizedPath = Files.createTempFile("ccpython-midi-render-normalized-", ".pcm");
        double peak = 0.0;
        double sampleRate = 48_000.0;
        double highPassAlpha = highPassAlpha(HIFI_HIGH_PASS_HZ, sampleRate);
        double lowPassAlpha = lowPassAlpha(HIFI_LOW_PASS_HZ, sampleRate);
        FilterState filterState = new FilterState();

        try (InputStream input = Files.newInputStream(outputPath);
             var output = new BufferedOutputStream(Files.newOutputStream(normalizedPath))) {
            byte[] buffer = new byte[16_384];
            int carry = -1;

            while (true) {
                int read = input.read(buffer);
                if (read < 0) break;
                int offset = 0;

                if (carry >= 0) {
                    if (read == 0) break;
                    int sample = (short) ((carry & 0xFF) | (buffer[0] << 8));
                    int scaled = scaleHiFiSample(sample, gain, highPassAlpha, lowPassAlpha, filterState);
                    peak = Math.max(peak, Math.abs(scaled / 32768.0));
                    output.write(scaled & 0xFF);
                    output.write((scaled >>> 8) & 0xFF);
                    carry = -1;
                    offset = 1;
                }

                int limit = offset + (((read - offset) / 2) * 2);
                for (int index = offset; index < limit; index += 2) {
                    int sample = (short) ((buffer[index] & 0xFF) | (buffer[index + 1] << 8));
                    int scaled = scaleHiFiSample(sample, gain, highPassAlpha, lowPassAlpha, filterState);
                    peak = Math.max(peak, Math.abs(scaled / 32768.0));
                    output.write(scaled & 0xFF);
                    output.write((scaled >>> 8) & 0xFF);
                }

                if (((read - offset) & 1) == 1) carry = buffer[read - 1] & 0xFF;
            }
        }

        Files.move(normalizedPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
        return peak;
    }

    private static int scaleHiFiSample(int sample, double gain, double highPassAlpha, double lowPassAlpha, FilterState filterState) {
        double normalized = (sample / 32768.0) * gain;
        double highPassed = highPassAlpha * (filterState.highPassState + normalized - filterState.previousInput);
        filterState.previousInput = normalized;
        filterState.highPassState = highPassed;

        filterState.lowPassState += lowPassAlpha * (highPassed - filterState.lowPassState);
        double filtered = midiSoftClip(filterState.lowPassState);
        int scaled = (int) Math.round(filtered * 32767.0);
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
    }

    private static double lowPassAlpha(double cutoffHz, double sampleRate) {
        return 1.0 - Math.exp((-2.0 * Math.PI * cutoffHz) / sampleRate);
    }

    private static double highPassAlpha(double cutoffHz, double sampleRate) {
        double dt = 1.0 / sampleRate;
        double rc = 1.0 / (2.0 * Math.PI * cutoffHz);
        return rc / (rc + dt);
    }

    private static final class FilterState {
        private double previousInput;
        private double highPassState;
        private double lowPassState;
    }

    private record TimedSequenceEvent(long tick, int priority, int order, MidiMessage message) {
    }
}
