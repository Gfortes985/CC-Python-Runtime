package dev.gfortes.ccpython.runtime;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaTable;
import dan200.computercraft.api.lua.LuaTask;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.lua.ObjectArguments;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.apis.OSAPI;
import dan200.computercraft.core.apis.RedstoneAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.core.ServerContext;
import dan200.computercraft.shared.peripheral.monitor.MonitorBlockEntity;
import dan200.computercraft.shared.peripheral.monitor.MonitorPeripheral;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.audio.SpeakerHiFiAudioManager;
import dev.gfortes.ccpython.config.CCPythonConfig;
import dev.gfortes.ccpython.monitor.ManagedImage;
import dev.gfortes.ccpython.monitor.MonitorGraphicsManager;
import dev.gfortes.ccpython.monitor.MonitorPalette;
import dev.gfortes.ccpython.util.LuaValues;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import net.minecraft.server.level.ServerLevel;

final class NativeHostDispatcher {
    private static final int MIDI_AUDIO_CHUNK_SAMPLES = 48_000;
    private static final int MIDI_HIFI_CHUNK_SAMPLES = 24_000;
    private static final Field INNER_COMPUTER_FIELD = findInnerComputerField();
    private static final Field COMPUTER_SYSTEM_SERVER_COMPUTER_FIELD = findComputerSystemField("computer");
    private static final Field COMPUTER_SYSTEM_ENVIRONMENT_FIELD = findComputerSystemField("environment");
    private static final Method SPEAKER_GET_LEVEL_METHOD = findSpeakerMethod("getLevel");
    private static final Method SPEAKER_GET_POSITION_METHOD = findSpeakerMethod("getPosition");

    private NativeHostDispatcher() {
    }

    static PythonActionResponse dispatch(PythonComputerRuntime runtime, PythonProcess process, String module, String method, List<Object> arguments) {
        NativeComputerAccess access;
        try {
            access = NativeComputerAccess.resolve(runtime.owner().computer());
        } catch (Throwable throwable) {
            return PythonActionResponse.failure(message(throwable));
        }
        if (access == null) return null;

        try {
            return switch (module) {
                case "__fs" -> dispatchSpecialFs(access, method, arguments);
                case "__fs_handle" -> dispatchFileHandle(access, process, method, arguments);
                case "__image" -> dispatchImage(access, process, method, arguments);
                case "__midi" -> dispatchMidi(access, process, method, arguments);
                case "__monitor_gfx" -> dispatchMonitorGraphics(access, process, method, arguments);
                case "__global" -> dispatchGlobals(access, process, method, arguments);
                case "fs" -> dispatchFs(access, method, arguments);
                case "term" -> dispatchTerm(access, method, arguments);
                case "os" -> dispatchOs(access, process, method, arguments);
                case "redstone" -> dispatchRedstone(access, method, arguments);
                case "peripheral" -> dispatchPeripheral(access, process, method, arguments);
                default -> null;
            };
        } catch (Throwable throwable) {
            return PythonActionResponse.failure(message(throwable));
        }
    }

    private static PythonActionResponse dispatchSpecialFs(NativeComputerAccess access, String method, List<Object> arguments) throws Exception {
        return switch (method) {
            case "readAll" -> success(readAll(access.fileSystem(), stringArg(arguments, 0)));
            case "writeAll" -> {
                writeAll(access.fileSystem(), stringArg(arguments, 0), stringArg(arguments, 1), false);
                yield success();
            }
            case "appendAll" -> {
                writeAll(access.fileSystem(), stringArg(arguments, 0), stringArg(arguments, 1), true);
                yield success();
            }
            case "resolveImport" -> success(resolveImport(access.fileSystem(), stringArg(arguments, 0), searchPaths(arguments, 1)));
            default -> null;
        };
    }

    private static PythonActionResponse dispatchFileHandle(NativeComputerAccess access, PythonProcess process, String method, List<Object> arguments) throws Exception {
        var resources = process.nativeResources();
        return switch (method) {
            case "open" -> success(resources.openFile(
                access.fileSystem(),
                stringArg(arguments, 0),
                stringArg(arguments, 1, "r"),
                CCPythonConfig.maxOpenFileHandlesPerProcess(access.serverComputer().getLevel().getServer())
            ));
            case "close" -> {
                resources.close(stringArg(arguments, 0));
                yield success();
            }
            case "seek" -> success(resources.seek(stringArg(arguments, 0), stringArg(arguments, 1, "cur"), longArg(arguments, 2, 0L)));
            case "read" -> success(resources.read(stringArg(arguments, 0), intArgOrNull(arguments, 1)));
            case "readLine" -> success(resources.readLine(stringArg(arguments, 0)));
            case "readAll" -> success(resources.readAll(stringArg(arguments, 0)));
            case "write" -> {
                resources.write(stringArg(arguments, 0), stringArg(arguments, 1, ""));
                yield success();
            }
            case "writeLine" -> {
                resources.writeLine(stringArg(arguments, 0), stringArg(arguments, 1, ""));
                yield success();
            }
            case "flush" -> {
                resources.flush(stringArg(arguments, 0));
                yield success();
            }
            default -> null;
        };
    }

    private static PythonActionResponse dispatchGlobals(NativeComputerAccess access, PythonProcess process, String method, List<Object> arguments) throws Exception {
        return switch (method) {
            case "sleep" -> {
                sleep(access, process, doubleArg(arguments, 0));
                yield success();
            }
            default -> null;
        };
    }

    private static PythonActionResponse dispatchImage(NativeComputerAccess access, PythonProcess process, String method, List<Object> arguments) throws Exception {
        var resources = process.nativeResources();
        return switch (method) {
            case "open" -> {
                var path = stringArg(arguments, 0);
                CCPythonMod.LOGGER.info("Opening Python image from '{}'.", path);
                var bytes = readAllBytes(access.fileSystem(), path);
                CCPythonMod.LOGGER.info("Read {} bytes for Python image '{}'.", bytes.length, path);
                var token = resources.registerImage(ManagedImage.fromBytes(bytes));
                CCPythonMod.LOGGER.info("Registered Python image '{}' as handle {}.", path, token);
                yield success(token);
            }
            case "loadUrl", "loadURL" -> {
                var image = ManagedImage.fromUrl(
                    stringArg(arguments, 0),
                    stringMapArg(arguments, 1),
                    intArg(arguments, 2)
                );
                yield success(resources.registerImage(image));
            }
            case "info" -> {
                var token = stringArg(arguments, 0);
                CCPythonMod.LOGGER.info("Querying Python image info for handle {}.", token);
                var image = resources.image(token);
                var info = new LinkedHashMap<String, Object>();
                info.put("width", image.width());
                info.put("height", image.height());
                CCPythonMod.LOGGER.info("Python image handle {} is {}x{}.", token, image.width(), image.height());
                yield success(info);
            }
            case "resize" -> {
                var image = resources.image(stringArg(arguments, 0));
                var resized = image.resize(intArg(arguments, 1), intArg(arguments, 2), stringArg(arguments, 3, "bilinear"));
                yield success(resources.registerImage(resized));
            }
            case "quantizeMonitor" -> {
                var image = resources.image(stringArg(arguments, 0));
                var quantized = image.quantizeToMonitorPalette(boolArg(arguments, 1, true));
                yield success(resources.registerImage(quantized));
            }
            case "close" -> {
                resources.closeImage(stringArg(arguments, 0));
                yield success();
            }
            default -> null;
        };
    }

    private static PythonActionResponse dispatchMonitorGraphics(NativeComputerAccess access, PythonProcess process, String method, List<Object> arguments) throws Exception {
        var monitor = requireMonitor(access, process, stringArg(arguments, 0));
        var manager = MonitorGraphicsManager.getInstance();
        return switch (method) {
            case "size" -> success(manager.size(monitor));
            case "disable" -> {
                manager.disable(monitor);
                yield success();
            }
            case "clear" -> {
                manager.clear(monitor, colorArg(arguments, 1, MonitorPalette.argb(15)));
                yield success();
            }
            case "setPixel" -> {
                manager.setPixel(monitor, intArg(arguments, 1), intArg(arguments, 2), colorArg(arguments, 3, MonitorPalette.argb(15)));
                yield success();
            }
            case "drawImage" -> {
                var image = process.nativeResources().image(stringArg(arguments, 1));
                manager.drawImage(
                    monitor,
                    image,
                    intArg(arguments, 2),
                    intArg(arguments, 3),
                    boolArg(arguments, 4, false)
                );
                yield success();
            }
            default -> null;
        };
    }

    private static PythonActionResponse dispatchMidi(NativeComputerAccess access, PythonProcess process, String method, List<Object> arguments) throws Exception {
        return switch (method) {
            case "playAudioSong" -> success(playMidiAudioSong(access, process, arguments));
            case "listSoundfonts" -> success(listMidiSoundfonts());
            case "playHifiSoundfontSong" -> {
                try {
                    yield success(playMidiHiFiSoundfontSong(access, process, arguments));
                } catch (Exception exception) {
                    CCPythonMod.LOGGER.warn("Hi-fi soundfont MIDI playback failed.", exception);
                    throw exception;
                }
            }
            case "playSoundfontSong" -> {
                try {
                    yield success(playMidiSoundfontSong(access, process, arguments));
                } catch (Exception exception) {
                    CCPythonMod.LOGGER.warn("Soundfont MIDI playback failed.", exception);
                    throw exception;
                }
            }
            default -> null;
        };
    }

    private static PythonActionResponse dispatchFs(NativeComputerAccess access, String method, List<Object> arguments) throws Exception {
        var fileSystem = access.fileSystem();
        return switch (method) {
            case "combine" -> success(combine(fileSystem, arguments));
            case "getName" -> success(FileSystem.getName(stringArg(arguments, 0)));
            case "getDir" -> success(FileSystem.getDirectory(stringArg(arguments, 0)));
            case "getSize" -> success(fileSystem.getSize(stringArg(arguments, 0)));
            case "exists" -> success(fileExists(fileSystem, stringArg(arguments, 0)));
            case "isDir" -> success(fileIsDir(fileSystem, stringArg(arguments, 0)));
            case "isReadOnly" -> success(fileSystem.isReadOnly(stringArg(arguments, 0)));
            case "list" -> success(fileSystem.list(stringArg(arguments, 0)));
            case "makeDir" -> {
                fileSystem.makeDir(stringArg(arguments, 0));
                yield success();
            }
            case "move" -> {
                fileSystem.move(stringArg(arguments, 0), stringArg(arguments, 1));
                yield success();
            }
            case "copy" -> {
                fileSystem.copy(stringArg(arguments, 0), stringArg(arguments, 1));
                yield success();
            }
            case "delete" -> {
                fileSystem.delete(stringArg(arguments, 0));
                yield success();
            }
            case "getFreeSpace" -> success(normalizeFreeSpace(fileSystem.getFreeSpace(stringArg(arguments, 0))));
            case "getCapacity" -> success(normalizeCapacity(fileSystem.getCapacity(stringArg(arguments, 0))));
            case "getDrive" -> maybeOne(fileExists(fileSystem, stringArg(arguments, 0)) ? fileSystem.getMountLabel(stringArg(arguments, 0)) : null);
            case "attributes" -> success(attributes(fileSystem.getAttributes(stringArg(arguments, 0)), fileSystem, stringArg(arguments, 0)));
            default -> null;
        };
    }

    private static PythonActionResponse dispatchTerm(NativeComputerAccess access, String method, List<Object> arguments) throws Exception {
        return null;
    }

    private static PythonActionResponse dispatchOs(NativeComputerAccess access, PythonProcess process, String method, List<Object> arguments) throws Exception {
        var api = new OSAPI(access.environment());
        return switch (method) {
            case "queueEvent" -> {
                access.environment().queueEvent(stringArg(arguments, 0), tail(arguments, 1).toArray(Object[]::new));
                yield success();
            }
            case "pullEvent" -> successValues(process.awaitComputerEvent(optionalString(arguments, 0).orElse(null), false));
            case "pullEventRaw" -> successValues(process.awaitComputerEvent(optionalString(arguments, 0).orElse(null), true));
            case "startTimer" -> success(api.startTimer(doubleArg(arguments, 0)));
            case "cancelTimer" -> {
                api.cancelTimer(intArg(arguments, 0));
                yield success();
            }
            case "doShutdown", "shutdown" -> {
                api.doShutdown();
                yield success();
            }
            case "doReboot", "reboot" -> {
                api.doReboot();
                yield success();
            }
            case "getComputerID" -> success(api.getComputerID());
            case "getComputerLabel" -> success(api.getComputerLabel());
            case "setComputerLabel" -> {
                api.setComputerLabel(optionalString(arguments, 0));
                yield success();
            }
            case "clock" -> success(api.clock());
            case "time" -> success(api.time(new ObjectArguments(arguments)));
            case "day" -> success(api.day(optionalString(arguments, 0)));
            case "epoch" -> success(api.epoch(optionalString(arguments, 0)));
            case "date" -> success(api.date(optionalString(arguments, 0), optionalLong(arguments, 1)));
            default -> null;
        };
    }

    private static PythonActionResponse dispatchRedstone(NativeComputerAccess access, String method, List<Object> arguments) throws Exception {
        var api = new RedstoneAPI(access.computer().getRedstone());
        return switch (method) {
            case "getSides" -> success(api.getSides());
            case "setOutput" -> {
                api.setOutput(side(arguments, 0), boolArg(arguments, 1));
                yield success();
            }
            case "getOutput" -> success(api.getOutput(side(arguments, 0)));
            case "getInput" -> success(api.getInput(side(arguments, 0)));
            case "setAnalogOutput" -> {
                api.setAnalogOutput(side(arguments, 0), intArg(arguments, 1));
                yield success();
            }
            case "getAnalogOutput" -> success(api.getAnalogOutput(side(arguments, 0)));
            case "getAnalogInput" -> success(api.getAnalogInput(side(arguments, 0)));
            case "setBundledOutput" -> {
                api.setBundledOutput(side(arguments, 0), intArg(arguments, 1));
                yield success();
            }
            case "getBundledOutput" -> success(api.getBundledOutput(side(arguments, 0)));
            case "getBundledInput" -> success(api.getBundledInput(side(arguments, 0)));
            case "testBundledInput" -> success(api.testBundledInput(side(arguments, 0), intArg(arguments, 1)));
            default -> null;
        };
    }

    private static PythonActionResponse dispatchPeripheral(NativeComputerAccess access, PythonProcess process, String method, List<Object> arguments) throws Exception {
        return switch (method) {
            case "getNames" -> success(listPeripheralNames(access));
            case "isPresent" -> success(getPeripheral(access, stringArg(arguments, 0)) != null);
            case "getType" -> peripheralTypes(access, stringArg(arguments, 0));
            case "hasType" -> peripheralHasType(access, stringArg(arguments, 0), stringArg(arguments, 1));
            case "getMethods" -> peripheralMethods(access, stringArg(arguments, 0));
            case "call" -> peripheralCall(access, process, arguments);
            default -> null;
        };
    }

    private static PythonActionResponse peripheralTypes(NativeComputerAccess access, String side) {
        var peripheral = getPeripheral(access, side);
        if (peripheral == null) return success();

        var results = new ArrayList<Object>();
        results.add(peripheral.getType());
        results.addAll(peripheral.getAdditionalTypes());
        return PythonActionResponse.success(results);
    }

    private static PythonActionResponse peripheralHasType(NativeComputerAccess access, String side, String type) {
        var peripheral = getPeripheral(access, side);
        if (peripheral == null) return success();
        boolean matches = peripheral.getType().equals(type) || peripheral.getAdditionalTypes().contains(type);
        return success(matches);
    }

    private static PythonActionResponse peripheralMethods(NativeComputerAccess access, String side) {
        var peripheral = getPeripheral(access, side);
        if (peripheral == null) return success();
        return success(new ArrayList<>(access.serverContext().peripheralMethods().getSelfMethods(peripheral).keySet()));
    }

    private static PythonActionResponse peripheralCall(NativeComputerAccess access, PythonProcess process, List<Object> arguments) throws Exception {
        String side = stringArg(arguments, 0);
        String method = stringArg(arguments, 1);
        var peripheral = getPeripheral(access, side);
        if (peripheral == null) return PythonActionResponse.failure("No peripheral attached");

        process.nativeResources().ensurePeripheralAttached(side, peripheral, access.computerSystem());

        if (peripheral instanceof SpeakerPeripheral speakerPeripheral && ("playAudio".equals(method) || "play_audio".equals(method))) {
            var volume = arguments.size() > 3 && arguments.get(3) != null
                ? Optional.of(doubleArg(arguments, 3))
                : Optional.<Double>empty();
            boolean accepted = speakerPeripheral.playAudio(
                new BlockingLuaContext(access.computer()),
                audioTableArg(arguments, 2),
                volume
            );
            return success(accepted);
        }

        var methods = access.serverContext().peripheralMethods().getSelfMethods(peripheral);
        var peripheralMethod = methods.get(method);
        if (peripheralMethod == null) peripheralMethod = methods.get(camelCase(method));
        if (peripheralMethod == null) {
            return PythonActionResponse.failure("No such peripheral method '" + method + "'");
        }

        var context = new BlockingLuaContext(access.computer());
        MethodResult result = peripheralMethod.apply(
            peripheral,
            context,
            access.computerSystem(),
            new ObjectArguments(tail(arguments, 2))
        );
        if (result.getCallback() != null) {
            return PythonActionResponse.failure("Asynchronous peripheral methods are not yet supported in the native Python backend.");
        }
        return PythonActionResponse.success(LuaValues.toList(result.getResult()));
    }

    private static String camelCase(String value) {
        if (value == null || value.indexOf('_') < 0) return value;

        var builder = new StringBuilder(value.length());
        boolean upper = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '_') {
                upper = true;
                continue;
            }

            if (upper) {
                builder.append(Character.toUpperCase(character));
                upper = false;
            } else {
                builder.append(character);
            }
        }

        return builder
            .toString()
            .replace("Id", "ID")
            .replace("Gps", "GPS")
            .replace("Http", "HTTP")
            .replace("Url", "URL");
    }

    private static void sleep(NativeComputerAccess access, PythonProcess process, double seconds) throws Exception {
        int timerId = new OSAPI(access.environment()).startTimer(seconds);
        process.awaitComputerEvent(
            "timer",
            false,
            event -> matchesTimer(event, timerId),
            "Sleeping for " + seconds + " seconds."
        );
    }

    private static boolean matchesTimer(List<Object> event, long timerId) {
        if (event.size() <= 1) return false;

        var value = event.get(1);
        if (value instanceof Number number) return number.longValue() == timerId;
        try {
            return Long.parseLong(String.valueOf(value)) == timerId;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static List<String> listPeripheralNames(NativeComputerAccess access) {
        var names = new ArrayList<String>();
        for (var side : ComputerSide.values()) {
            if (access.environment().getPeripheral(side) != null) names.add(side.getName());
        }
        return names;
    }

    private static IPeripheral getPeripheral(NativeComputerAccess access, String sideName) {
        var side = ComputerSide.valueOfInsensitive(sideName);
        if (side == null) return null;
        return access.environment().getPeripheral(side);
    }

    private static MonitorBlockEntity requireMonitor(NativeComputerAccess access, PythonProcess process, String sideName) throws LuaException {
        var peripheral = getPeripheral(access, sideName);
        if (!(peripheral instanceof MonitorPeripheral monitorPeripheral)) {
            throw new LuaException("No monitor attached on side '" + sideName + "'");
        }
        process.nativeResources().ensurePeripheralAttached(sideName, peripheral, access.computerSystem());
        Object target = monitorPeripheral.getTarget();
        if (!(target instanceof MonitorBlockEntity monitor)) {
            throw new LuaException("Failed to resolve the monitor target for side '" + sideName + "'");
        }
        return monitor;
    }

    private static Map<String, Object> resolveImport(FileSystem fileSystem, String moduleName, List<String> searchPaths) throws FileSystemException {
        var relative = moduleName.replace('.', '/');

        for (var base : searchPaths) {
            String modulePath = fileSystem.combine(base, relative + ".py");
            if (fileSystem.exists(modulePath) && !fileSystem.isDir(modulePath)) {
                return Map.of(
                    "path", modulePath,
                    "package", false,
                    "package_path", parent(modulePath)
                );
            }

            String packagePath = fileSystem.combine(base, relative);
            String initPath = fileSystem.combine(packagePath, "__init__.py");
            if (fileSystem.exists(initPath) && !fileSystem.isDir(initPath)) {
                return Map.of(
                    "path", initPath,
                    "package", true,
                    "namespace", false,
                    "package_path", packagePath
                );
            }

            if (fileSystem.exists(packagePath) && fileSystem.isDir(packagePath)) {
                return Map.of(
                    "path", packagePath,
                    "package", true,
                    "namespace", true,
                    "package_path", packagePath
                );
            }
        }

        return null;
    }

    private static byte[] readAllBytes(FileSystem fileSystem, String path) throws IOException, FileSystemException {
        try (var wrapper = fileSystem.openForRead(path)) {
            InputStream stream = java.nio.channels.Channels.newInputStream(wrapper.get());
            var output = new ByteArrayOutputStream();
            stream.transferTo(output);
            return output.toByteArray();
        }
    }

    private static Map<String, String> stringMapArg(List<Object> arguments, int index) {
        if (index >= arguments.size() || !(arguments.get(index) instanceof Map<?, ?> map)) return Map.of();
        var result = new LinkedHashMap<String, String>();
        for (var entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            result.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return result;
    }

    private static Object readAll(FileSystem fileSystem, String path) throws Exception {
        try (var wrapper = fileSystem.openForRead(path)) {
            var channel = wrapper.get();
            var buffer = ByteBuffer.allocate(4096);
            var output = new ByteArrayOutputStream();
            while (true) {
                int read = channel.read(buffer);
                if (read <= 0) break;
                buffer.flip();
                output.write(buffer.array(), 0, read);
                buffer.clear();
            }
            return output.toString(StandardCharsets.ISO_8859_1);
        }
    }

    private static void writeAll(FileSystem fileSystem, String path, String value, boolean append) throws Exception {
        var options = append ? NativeProcessResources.APPEND_OPTIONS : NativeProcessResources.WRITE_OPTIONS;
        try (var wrapper = fileSystem.openForWrite(path, options)) {
            wrapper.get().write(ByteBuffer.wrap(value.getBytes(StandardCharsets.ISO_8859_1)));
        }
    }

    private static Map<String, Object> attributes(BasicFileAttributes attributes, FileSystem fileSystem, String path) throws FileSystemException {
        var map = new LinkedHashMap<String, Object>();
        map.put("modification", attributes.lastModifiedTime().toMillis());
        map.put("modified", attributes.lastModifiedTime().toMillis());
        map.put("created", attributes.creationTime().toMillis());
        map.put("size", attributes.isDirectory() ? 0L : attributes.size());
        map.put("isDir", attributes.isDirectory());
        map.put("isReadOnly", fileSystem.isReadOnly(path));
        return map;
    }

    private static boolean fileExists(FileSystem fileSystem, String path) {
        try {
            return fileSystem.exists(path);
        } catch (FileSystemException ignored) {
            return false;
        }
    }

    private static boolean fileIsDir(FileSystem fileSystem, String path) {
        try {
            return fileSystem.isDir(path);
        } catch (FileSystemException ignored) {
            return false;
        }
    }

    private static Object normalizeFreeSpace(long freeSpace) {
        return freeSpace >= 0 ? freeSpace : "unlimited";
    }

    private static Object normalizeCapacity(OptionalLong capacity) {
        return capacity.isPresent() ? capacity.getAsLong() : null;
    }

    private static String combine(FileSystem fileSystem, List<Object> arguments) throws LuaException {
        if (arguments.isEmpty()) return "";
        String current = stringArg(arguments, 0);
        for (int i = 1; i < arguments.size(); i++) current = fileSystem.combine(current, stringArg(arguments, i));
        return current;
    }

    private static List<String> searchPaths(List<Object> arguments, int index) {
        if (index >= arguments.size() || !(arguments.get(index) instanceof List<?> list)) return List.of();
        var paths = new ArrayList<String>(list.size());
        for (var entry : list) {
            if (entry != null) paths.add(entry.toString());
        }
        return paths;
    }

    private static List<Object> tail(List<Object> arguments, int fromIndex) {
        if (fromIndex >= arguments.size()) return List.of();
        return arguments.subList(fromIndex, arguments.size());
    }

    private static String parent(String path) {
        int index = path.lastIndexOf('/');
        if (index <= 0) return "/";
        return path.substring(0, index);
    }

    private static ComputerSide side(List<Object> arguments, int index) throws LuaException {
        String name = stringArg(arguments, index);
        var side = ComputerSide.valueOfInsensitive(name);
        if (side == null) throw new LuaException("Invalid side '" + name + "'");
        return side;
    }

    private static String stringArg(List<Object> arguments, int index) throws LuaException {
        if (index >= arguments.size() || arguments.get(index) == null) throw new LuaException("Missing argument at index " + index);
        return arguments.get(index).toString();
    }

    private static String stringArg(List<Object> arguments, int index, String fallback) {
        return index >= arguments.size() || arguments.get(index) == null ? fallback : arguments.get(index).toString();
    }

    private static int intArg(List<Object> arguments, int index) throws LuaException {
        if (index >= arguments.size() || !(arguments.get(index) instanceof Number number)) {
            throw new LuaException("Expected integer argument at index " + index);
        }
        return number.intValue();
    }

    private static Integer intArgOrNull(List<Object> arguments, int index) throws LuaException {
        if (index >= arguments.size() || arguments.get(index) == null) return null;
        if (!(arguments.get(index) instanceof Number number)) {
            throw new LuaException("Expected integer argument at index " + index);
        }
        return number.intValue();
    }

    private static long longArg(List<Object> arguments, int index, long fallback) throws LuaException {
        if (index >= arguments.size() || arguments.get(index) == null) return fallback;
        if (!(arguments.get(index) instanceof Number number)) throw new LuaException("Expected numeric argument at index " + index);
        return number.longValue();
    }

    private static double doubleArg(List<Object> arguments, int index) throws LuaException {
        if (index >= arguments.size() || !(arguments.get(index) instanceof Number number)) {
            throw new LuaException("Expected numeric argument at index " + index);
        }
        return number.doubleValue();
    }

    private static boolean boolArg(List<Object> arguments, int index) throws LuaException {
        if (index >= arguments.size() || !(arguments.get(index) instanceof Boolean bool)) {
            throw new LuaException("Expected boolean argument at index " + index);
        }
        return bool;
    }

    private static boolean boolArg(List<Object> arguments, int index, boolean fallback) throws LuaException {
        if (index >= arguments.size() || arguments.get(index) == null) return fallback;
        if (!(arguments.get(index) instanceof Boolean bool)) {
            throw new LuaException("Expected boolean argument at index " + index);
        }
        return bool;
    }

    private static int colorArg(List<Object> arguments, int index, int fallback) throws LuaException {
        if (index >= arguments.size() || arguments.get(index) == null) return fallback;
        if (!(arguments.get(index) instanceof Number number)) {
            throw new LuaException("Expected numeric colour argument at index " + index);
        }
        return MonitorPalette.coerceArgb(number.longValue());
    }

    private static Optional<String> optionalString(List<Object> arguments, int index) {
        if (index >= arguments.size() || arguments.get(index) == null) return Optional.empty();
        return Optional.of(arguments.get(index).toString());
    }

    private static Optional<Long> optionalLong(List<Object> arguments, int index) throws LuaException {
        if (index >= arguments.size() || arguments.get(index) == null) return Optional.empty();
        if (!(arguments.get(index) instanceof Number number)) throw new LuaException("Expected numeric argument at index " + index);
        return Optional.of(number.longValue());
    }

    private static LuaTable<Object, Object> audioTableArg(List<Object> arguments, int index) throws LuaException {
        if (index >= arguments.size()) throw new LuaException("Missing audio buffer argument.");

        Object normalized = LuaValues.normalize(arguments.get(index));
        if (normalized instanceof List<?> list) return new SimpleLuaTable(list);
        if (normalized instanceof Map<?, ?> map) return new SimpleLuaTable(map);

        throw new LuaException("Audio buffer must be a list of PCM samples.");
    }

    private static Map<String, Object> playMidiAudioSong(NativeComputerAccess access, PythonProcess process, List<Object> arguments) throws Exception {
        String side = stringArg(arguments, 0);
        var peripheral = getPeripheral(access, side);
        if (!(peripheral instanceof SpeakerPeripheral speakerPeripheral)) {
            throw new LuaException("No speaker attached on side '" + side + "'");
        }

        process.nativeResources().ensurePeripheralAttached(side, peripheral, access.computerSystem());

        var notes = midiNotesArg(arguments, 1);
        double totalDuration = doubleArg(arguments, 2);
        int sampleRate = 48_000;
        int chunkSamples = MIDI_AUDIO_CHUNK_SAMPLES;
        int totalSamples = Math.max(1, (int) Math.ceil(Math.max(0.0, totalDuration) * sampleRate));
        int chunks = 0;
        int nextNote = 0;
        double peak = 0.0;
        var active = new ArrayList<MidiAudioNote>();
        var context = new BlockingLuaContext(access.computer());

        for (int chunkStart = 0; chunkStart < totalSamples; chunkStart += chunkSamples) {
            int size = Math.min(chunkSamples, totalSamples - chunkStart);
            int chunkEnd = chunkStart + size;
            double chunkStartTime = chunkStart / (double) sampleRate;
            double chunkEndTime = chunkEnd / (double) sampleRate;

            while (nextNote < notes.size() && notes.get(nextNote).start < chunkEndTime) {
                active.add(notes.get(nextNote));
                nextNote++;
            }

            active.removeIf(note -> note.end <= chunkStartTime);
            var pcm = new ArrayList<Object>(size);

            for (int i = 0; i < size; i++) {
                int absoluteSample = chunkStart + i;
                double sampleTime = absoluteSample / (double) sampleRate;
                double mixed = 0.0;

                for (var note : active) mixed += midiWaveSample(note, sampleTime, absoluteSample);

                double clipped = midiSoftClip(mixed);
                peak = Math.max(peak, Math.abs(clipped));
                pcm.add((int) Math.max(-128, Math.min(127, Math.round(clipped * 127.0))));
            }

            var table = new SimpleLuaTable(pcm);
            while (!speakerPeripheral.playAudio(context, table, Optional.empty())) {
                process.awaitComputerEvent("speaker_audio_empty", true);
            }
            chunks++;
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("mode", "audio");
        result.put("chunks", chunks);
        result.put("samples", totalSamples);
        result.put("peak", peak);
        return result;
    }

    private static Map<String, Object> playMidiSoundfontSong(NativeComputerAccess access, PythonProcess process, List<Object> arguments) throws Exception {
        String side = stringArg(arguments, 0);
        String midiPath = stringArg(arguments, 1);
        double tempoScale = doubleArg(arguments, 2);
        double volume = doubleArg(arguments, 3);
        int transpose = intArg(arguments, 4);
        String requestedSoundfont = stringArg(arguments, 5, "");

        if (tempoScale <= 0.0) throw new LuaException("tempo_scale must be > 0");

        var peripheral = getPeripheral(access, side);
        if (!(peripheral instanceof SpeakerPeripheral speakerPeripheral)) {
            throw new LuaException("No speaker attached on side '" + side + "'");
        }

        process.nativeResources().ensurePeripheralAttached(side, peripheral, access.computerSystem());

        byte[] midiBytes = readAllBytes(access.fileSystem(), midiPath);
        var soundfont = materializeSoundfont(requestedSoundfont);
        CCPythonMod.LOGGER.info(
            "Rendering MIDI '{}' through soundfont '{}' for speaker side '{}'.",
            midiPath,
            soundfont.source(),
            side
        );
        Path midiTemp = Files.createTempFile("ccpython-midi-", ".mid");
        try {
            Files.write(midiTemp, midiBytes);
            var rendered = renderSoundfontMidi(midiTemp, soundfont.path(), tempoScale, volume, transpose, false);
            try {
                byte[] audioBytes = Files.readAllBytes(rendered.pcmPath());
                int chunks = 0;
                var context = new BlockingLuaContext(access.computer());
                int chunkSamples = MIDI_AUDIO_CHUNK_SAMPLES;

                for (int offset = 0; offset < audioBytes.length; offset += chunkSamples) {
                    int end = Math.min(audioBytes.length, offset + chunkSamples);
                    var pcm = new ArrayList<Object>(end - offset);
                    for (int i = offset; i < end; i++) pcm.add((int) audioBytes[i]);
                    if (pcm.isEmpty()) continue;

                    var table = new SimpleLuaTable(pcm);
                    while (!speakerPeripheral.playAudio(context, table, Optional.empty())) {
                        process.awaitComputerEvent("speaker_audio_empty", true);
                    }
                    chunks++;
                }

                var result = new LinkedHashMap<String, Object>();
                result.put("mode", "audio");
                result.put("chunks", chunks);
                result.put("samples", rendered.samples());
                result.put("peak", rendered.peak());
                result.put("requested_soundfont", soundfont.source());
                result.put("soundfont", soundfont.source());
                CCPythonMod.LOGGER.info(
                    "Finished soundfont MIDI '{}' with {} chunks and peak {}.",
                    midiPath,
                    chunks,
                    rendered.peak()
                );
                return result;
            } finally {
                Files.deleteIfExists(rendered.pcmPath());
            }
        } finally {
            Files.deleteIfExists(midiTemp);
            if (soundfont.temporary()) Files.deleteIfExists(soundfont.path());
        }
    }

    private static Map<String, Object> playMidiHiFiSoundfontSong(NativeComputerAccess access, PythonProcess process, List<Object> arguments) throws Exception {
        String side = stringArg(arguments, 0);
        String midiPath = stringArg(arguments, 1);
        double tempoScale = doubleArg(arguments, 2);
        double volume = doubleArg(arguments, 3);
        int transpose = intArg(arguments, 4);
        String requestedSoundfont = stringArg(arguments, 5, "");

        if (tempoScale <= 0.0) throw new LuaException("tempo_scale must be > 0");

        var peripheral = getPeripheral(access, side);
        if (!(peripheral instanceof SpeakerPeripheral speakerPeripheral)) {
            throw new LuaException("No speaker attached on side '" + side + "'");
        }

        process.nativeResources().ensurePeripheralAttached(side, peripheral, access.computerSystem());

        byte[] midiBytes = readAllBytes(access.fileSystem(), midiPath);
        var soundfont = materializeSoundfont(requestedSoundfont);
        var level = invokeSpeakerLevel(speakerPeripheral);
        var position = invokeSpeakerPosition(speakerPeripheral);
        var source = speakerPeripheral.getSource();
        CCPythonMod.LOGGER.info(
            "Rendering hi-fi MIDI '{}' through soundfont '{}' for speaker side '{}'.",
            midiPath,
            soundfont.source(),
            side
        );

        Path midiTemp = Files.createTempFile("ccpython-midi-", ".mid");
        boolean completedPlayback = false;
        try {
            Files.write(midiTemp, midiBytes);
            var rendered = renderSoundfontMidi(midiTemp, soundfont.path(), tempoScale, volume, transpose, true);
            try (InputStream stream = Files.newInputStream(rendered.pcmPath())) {
                int chunks = 0;
                byte[] byteBuffer = new byte[Math.max(2, MIDI_HIFI_CHUNK_SAMPLES * 2)];

                while (true) {
                    int read = stream.readNBytes(byteBuffer, 0, byteBuffer.length);
                    if (read <= 0) break;
                    if ((read & 1) == 1) read--;
                    if (read <= 0) break;

                    int samplesCount = read / 2;
                    short[] samples = new short[samplesCount];
                    for (int index = 0; index < samplesCount; index++) {
                        int byteIndex = index * 2;
                        samples[index] = (short) ((byteBuffer[byteIndex] & 0xFF) | (byteBuffer[byteIndex + 1] << 8));
                    }

                    while (!SpeakerHiFiAudioManager.play(level, position, source, samples, volume)) {
                        process.awaitComputerEvent(SpeakerHiFiAudioManager.BUFFER_EVENT, true);
                    }
                    chunks++;
                }

                while (!SpeakerHiFiAudioManager.awaitDrain(source)) {
                    process.awaitComputerEvent(SpeakerHiFiAudioManager.DRAIN_EVENT, true);
                }

                var result = new LinkedHashMap<String, Object>();
                result.put("mode", "hifi");
                result.put("chunks", chunks);
                result.put("samples", rendered.samples());
                result.put("peak", rendered.peak());
                result.put("gain", rendered.gain());
                result.put("requested_soundfont", soundfont.source());
                result.put("soundfont", soundfont.source());
                result.put("api", "speaker.playAudio16");
                CCPythonMod.LOGGER.info(
                    "Finished hi-fi MIDI '{}' with {} chunks, peak {} and gain {}.",
                    midiPath,
                    chunks,
                    rendered.peak(),
                    rendered.gain()
                );
                completedPlayback = true;
                return result;
            } finally {
                Files.deleteIfExists(rendered.pcmPath());
            }
        } finally {
            if (!completedPlayback) {
                SpeakerHiFiAudioManager.stop(level, source);
            }
            Files.deleteIfExists(midiTemp);
            if (soundfont.temporary()) Files.deleteIfExists(soundfont.path());
        }
    }

    private static RenderedSoundfontAudio renderSoundfontMidi(
        Path midiPath,
        Path soundfontPath,
        double tempoScale,
        double volume,
        int transpose,
        boolean hiFi
    ) throws Exception {
        Path outputPath = Files.createTempFile("ccpython-midi-render-", ".pcm");
        try {
            String javaExecutable = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
            String classPath = System.getProperty("java.class.path", "");
            if (classPath.isBlank()) {
                throw new LuaException("Current Java classpath is empty; cannot launch MIDI renderer.");
            }
            var command = new ArrayList<String>();
            command.add(javaExecutable);
            command.add("--add-exports=java.desktop/com.sun.media.sound=ALL-UNNAMED");
            command.add("--add-opens=java.desktop/com.sun.media.sound=ALL-UNNAMED");
            command.add("-cp");
            command.add(classPath);
            command.add(MidiSoundfontRendererMain.class.getName());
            command.add(midiPath.toString());
            command.add(soundfontPath.toString());
            command.add(outputPath.toString());
            command.add(Double.toString(tempoScale));
            command.add(Double.toString(volume));
            command.add(Integer.toString(transpose));
            command.add(hiFi ? "pcm16" : "pcm8");

            CCPythonMod.LOGGER.info("Launching soundfont MIDI helper with main {}.", MidiSoundfontRendererMain.class.getName());

            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

            String output;
            try (InputStream stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new LuaException("Soundfont renderer failed: " + output.strip());
            }

            long samples = Files.size(outputPath);
            double peak = 0.0;
            double gain = 1.0;
            for (String line : output.lines().toList()) {
                if (line.startsWith("SAMPLES=")) {
                    samples = Long.parseLong(line.substring("SAMPLES=".length()).trim());
                } else if (line.startsWith("PEAK=")) {
                    peak = Double.parseDouble(line.substring("PEAK=".length()).trim());
                } else if (line.startsWith("GAIN=")) {
                    gain = Double.parseDouble(line.substring("GAIN=".length()).trim());
                }
            }

            return new RenderedSoundfontAudio(outputPath, samples, peak, gain);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LuaException("Interrupted while rendering MIDI audio.");
        } catch (Exception exception) {
            Files.deleteIfExists(outputPath);
            throw exception;
        }
    }

    private static ResolvedSoundfont materializeSoundfont(String requestedPath) throws Exception {
        if (requestedPath != null && !requestedPath.isBlank()) {
            var resolved = resolveRequestedSoundfont(requestedPath);
            if (resolved != null) return resolved;
            throw missingSoundfontError("Soundfont not found: " + requestedPath);
        }

        String override = System.getProperty("ccpython.midi.soundfont");
        if (override != null && !override.isBlank()) {
            Path explicit = resolveConfigRelativePath(override);
            if (!Files.isRegularFile(explicit)) {
                throw new LuaException("Configured soundfont file not found: " + override);
            }
            return new ResolvedSoundfont(explicit, explicit.toString(), false);
        }

        String configured = CCPythonConfig.defaultMidiSoundfont();
        if (!configured.isBlank()) {
            Path explicit = resolveConfigRelativePath(configured);
            if (!Files.isRegularFile(explicit)) {
                throw new LuaException("Configured default soundfont file not found: " + explicit);
            }
            return new ResolvedSoundfont(explicit, "config:" + explicit.getFileName(), false);
        }

        var discovered = discoverConfiguredSoundfonts();
        if (!discovered.isEmpty()) return discovered.getFirst();

        throw missingSoundfontError(
            "No MIDI soundfont found. Put one or more .sf2 files into "
                + CCPythonConfig.soundfontConfigDirectory()
                + ", set midi.defaultSoundfont in ccpython-common.toml, or pass soundfont='name.sf2'."
        );
    }

    private static Path parseSoundfontPath(String pathText) throws LuaException {
        try {
            return Path.of(pathText);
        } catch (InvalidPathException exception) {
            throw new LuaException("Invalid soundfont path: " + pathText);
        }
    }

    private static Path resolveConfigRelativePath(String pathText) throws LuaException {
        Path configured = parseSoundfontPath(pathText);
        if (configured.isAbsolute()) return configured;
        return CCPythonConfig.configDirectory().resolve(configured).normalize();
    }

    private static List<Map<String, Object>> listMidiSoundfonts() throws Exception {
        var soundfonts = discoverConfiguredSoundfonts();
        var results = new ArrayList<Map<String, Object>>(soundfonts.size());
        for (int index = 0; index < soundfonts.size(); index++) {
            var soundfont = soundfonts.get(index);
            var entry = new LinkedHashMap<String, Object>();
            entry.put("name", soundfont.path().getFileName().toString());
            entry.put("path", soundfont.path().toString());
            entry.put("source", soundfont.source());
            entry.put("default", index == 0);
            results.add(entry);
        }
        return results;
    }

    private static List<ResolvedSoundfont> discoverConfiguredSoundfonts() throws Exception {
        var discovered = new LinkedHashMap<String, ResolvedSoundfont>();

        String configured = CCPythonConfig.defaultMidiSoundfont();
        if (!configured.isBlank()) {
            Path explicit = resolveConfigRelativePath(configured);
            if (Files.isRegularFile(explicit)) {
                discovered.put(explicit.normalize().toString(), new ResolvedSoundfont(explicit, "config:" + explicit.getFileName(), false));
            }
        }

        Path configDirectory = CCPythonConfig.soundfontConfigDirectory();
        if (Files.isDirectory(configDirectory)) {
            try (var files = Files.list(configDirectory)) {
                files
                    .filter(Files::isRegularFile)
                    .filter(NativeHostDispatcher::isSoundfontFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .forEach(path -> discovered.putIfAbsent(
                        path.normalize().toString(),
                        new ResolvedSoundfont(path, "config:" + path.getFileName(), false)
                    ));
            }
        }

        return new ArrayList<>(discovered.values());
    }

    private static ResolvedSoundfont resolveRequestedSoundfont(String requestedPath) throws Exception {
        Path parsed = parseSoundfontPath(requestedPath);
        if (parsed.isAbsolute() && Files.isRegularFile(parsed)) {
            return new ResolvedSoundfont(parsed, parsed.toString(), false);
        }

        Path relativeToConfig = resolveConfigRelativePath(requestedPath);
        if (Files.isRegularFile(relativeToConfig)) {
            return new ResolvedSoundfont(relativeToConfig, "config:" + relativeToConfig.getFileName(), false);
        }

        String requestedLower = requestedPath.trim().toLowerCase();
        String requestedBase = requestedLower.endsWith(".sf2") ? requestedLower.substring(0, requestedLower.length() - 4) : requestedLower;
        for (var soundfont : discoverConfiguredSoundfonts()) {
            String fileName = soundfont.path().getFileName().toString();
            String fileLower = fileName.toLowerCase();
            String fileBase = fileLower.endsWith(".sf2") ? fileLower.substring(0, fileLower.length() - 4) : fileLower;
            if (fileLower.equals(requestedLower) || fileBase.equals(requestedBase)) {
                return soundfont;
            }
        }

        return null;
    }

    private static LuaException missingSoundfontError(String message) throws Exception {
        var available = discoverConfiguredSoundfonts();
        if (available.isEmpty()) return new LuaException(message);

        String names = available.stream()
            .map(soundfont -> soundfont.path().getFileName().toString())
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
        return new LuaException(message + " Available soundfonts: " + names);
    }

    private static boolean isSoundfontFile(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".sf2");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static ServerLevel invokeSpeakerLevel(SpeakerPeripheral speaker) {
        try {
            return (ServerLevel) SPEAKER_GET_LEVEL_METHOD.invoke(speaker);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to resolve speaker level.", exception);
        }
    }

    private static SpeakerPosition invokeSpeakerPosition(SpeakerPeripheral speaker) {
        try {
            return (SpeakerPosition) SPEAKER_GET_POSITION_METHOD.invoke(speaker);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to resolve speaker position.", exception);
        }
    }

    private static List<MidiAudioNote> midiNotesArg(List<Object> arguments, int index) throws LuaException {
        if (index >= arguments.size() || !(arguments.get(index) instanceof List<?> list)) {
            throw new LuaException("Expected MIDI note list argument at index " + index);
        }

        var notes = new ArrayList<MidiAudioNote>(list.size());
        for (var entry : list) {
            if (!(entry instanceof Map<?, ?> map)) throw new LuaException("MIDI note entries must be tables.");
            notes.add(new MidiAudioNote(
                mapDouble(map, "start"),
                mapDouble(map, "end"),
                mapDouble(map, "frequency"),
                mapDouble(map, "gain"),
                mapString(map, "voice", "triangle"),
                mapInt(map, "note", 0)
            ));
        }
        return notes;
    }

    private static double midiWaveSample(MidiAudioNote note, double sampleTime, int sampleIndex) {
        double rel = sampleTime - note.start;
        if (rel < 0.0) return 0.0;

        double duration = Math.max(0.02, note.end - note.start);
        if (rel > duration) return 0.0;

        double attack = midiAttack(note.voice, duration);
        double release = midiRelease(note.voice, duration);
        if (release >= duration) release = duration * 0.5;

        double envelope;
        if (attack > 0.0 && rel < attack) {
            envelope = rel / attack;
        } else if (rel > duration - release) {
            envelope = Math.max(0.0, (duration - rel) / Math.max(release, 1e-6));
        } else {
            envelope = midiSustain(note.voice, rel, duration);
        }

        double phase = note.frequency * rel;
        double frac = phase - Math.floor(phase);
        double wave;
        double vibrato = Math.sin(2.0 * Math.PI * rel * 5.0) * 0.003;
        double phaseVibrato = note.frequency * rel * (1.0 + vibrato);
        double fracVibrato = phaseVibrato - Math.floor(phaseVibrato);
        double subPhase = note.frequency * 0.5 * rel;
        double fracSub = subPhase - Math.floor(subPhase);

        switch (note.voice) {
            case "sine" -> wave = Math.sin(2.0 * Math.PI * phase);
            case "square" -> wave = frac < 0.5 ? 1.0 : -1.0;
            case "saw" -> wave = (2.0 * frac) - 1.0;
            case "triangle" -> wave = 1.0 - (4.0 * Math.abs(frac - 0.5));
            case "piano" -> {
                double fade = Math.exp(-1.8 * rel);
                wave = (
                    0.9 * Math.sin(2.0 * Math.PI * phase)
                        + 0.35 * Math.sin(4.0 * Math.PI * phase)
                        + 0.15 * Math.sin(6.0 * Math.PI * phase)
                    ) * fade;
            }
            case "mallet" -> {
                double fade = Math.exp(-3.2 * rel);
                wave = (
                    Math.sin(2.0 * Math.PI * phase)
                        + 0.55 * Math.sin(4.0 * Math.PI * phase)
                        + 0.25 * Math.sin(6.0 * Math.PI * phase)
                    ) * fade;
            }
            case "organ" -> wave =
                0.75 * Math.sin(2.0 * Math.PI * phase)
                    + 0.45 * Math.sin(4.0 * Math.PI * phase)
                    + 0.2 * Math.sin(6.0 * Math.PI * phase);
            case "guitar" -> {
                double fade = Math.exp(-2.6 * rel);
                wave = (0.55 * (1.0 - (4.0 * Math.abs(frac - 0.5))) + 0.45 * ((2.0 * frac) - 1.0)) * fade;
            }
            case "bass" -> wave =
                0.65 * Math.sin(2.0 * Math.PI * phase)
                    + 0.3 * (frac < 0.5 ? 1.0 : -1.0)
                    + 0.18 * Math.sin(2.0 * Math.PI * subPhase)
                    + 0.08 * (fracSub < 0.5 ? 1.0 : -1.0);
            case "strings" -> {
                double frac1 = (phase * 0.997) - Math.floor(phase * 0.997);
                double frac2 = (phase * 1.003) - Math.floor(phase * 1.003);
                wave = 0.5 * (((2.0 * frac1) - 1.0) + ((2.0 * frac2) - 1.0)) + 0.2 * Math.sin(2.0 * Math.PI * phase);
            }
            case "choir" -> wave =
                0.75 * Math.sin(2.0 * Math.PI * phaseVibrato)
                    + 0.2 * Math.sin(4.0 * Math.PI * phaseVibrato)
                    + 0.12 * Math.sin(2.0 * Math.PI * (phaseVibrato * 0.5));
            case "brass" -> wave =
                0.55 * ((2.0 * frac) - 1.0)
                    + 0.3 * (frac < 0.5 ? 1.0 : -1.0)
                    + 0.18 * Math.sin(4.0 * Math.PI * phase);
            case "reed" -> wave =
                0.6 * (fracVibrato < 0.5 ? 1.0 : -1.0)
                    + 0.25 * Math.sin(2.0 * Math.PI * phaseVibrato)
                    + 0.1 * Math.sin(4.0 * Math.PI * phaseVibrato);
            case "flute" -> wave =
                0.92 * Math.sin(2.0 * Math.PI * phaseVibrato)
                    + 0.12 * Math.sin(4.0 * Math.PI * phaseVibrato)
                    + 0.04 * Math.sin(6.0 * Math.PI * phaseVibrato);
            case "lead" -> wave =
                0.55 * ((2.0 * fracVibrato) - 1.0)
                    + 0.35 * (fracVibrato < 0.5 ? 1.0 : -1.0)
                    + 0.1 * Math.sin(2.0 * Math.PI * phaseVibrato);
            case "pad" -> wave =
                0.55 * Math.sin(2.0 * Math.PI * phaseVibrato)
                    + 0.3 * (1.0 - (4.0 * Math.abs(fracVibrato - 0.5)))
                    + 0.15 * Math.sin(2.0 * Math.PI * (phaseVibrato * 0.5));
            case "synthfx" -> {
                double wobble = Math.sin(2.0 * Math.PI * rel * 2.7) * 0.2;
                wave =
                    0.55 * Math.sin(2.0 * Math.PI * (phase + wobble))
                        + 0.3 * ((2.0 * fracVibrato) - 1.0)
                        + 0.15 * Math.sin(4.0 * Math.PI * (phase + wobble));
            }
            case "pluck" -> {
                double fade = Math.exp(-3.6 * rel);
                wave =
                    (0.6 * (1.0 - (4.0 * Math.abs(frac - 0.5)))
                        + 0.25 * Math.sin(2.0 * Math.PI * phase)
                        + 0.15 * ((2.0 * frac) - 1.0)) * fade;
            }
            case "bit" -> wave = frac < 0.5 ? 0.95 : -0.95;
            case "bell" -> {
                double fade = Math.exp(-3.5 * rel);
                wave = (
                    Math.sin(2.0 * Math.PI * phase)
                        + 0.5 * Math.sin(4.0 * Math.PI * phase)
                        + 0.2 * Math.sin(6.0 * Math.PI * phase)
                    ) * fade;
            }
            case "drum_kick" -> {
                double kickPhase = phase * Math.max(0.2, 1.0 - (rel * 4.0));
                wave = Math.sin(2.0 * Math.PI * kickPhase) * Math.exp(-9.0 * rel);
            }
            case "drum_snare" -> {
                double seed = (sampleIndex + 1.0) * (note.note + 29.0) * 17.913;
                double noise = Math.sin(seed) * 31337.1337;
                double white = ((noise - Math.floor(noise)) * 2.0) - 1.0;
                wave = (0.8 * white + 0.2 * Math.sin(2.0 * Math.PI * phase)) * Math.exp(-14.0 * rel);
            }
            case "drum_hat" -> {
                double seed = (sampleIndex + 1.0) * (note.note + 11.0) * 27.117;
                double noise = Math.sin(seed) * 19642.349;
                wave = (((noise - Math.floor(noise)) * 2.0) - 1.0) * Math.exp(-24.0 * rel);
            }
            case "drum_cymbal" -> {
                double seed = (sampleIndex + 1.0) * (note.note + 47.0) * 9.271;
                double noise = Math.sin(seed) * 12345.678;
                wave = (((noise - Math.floor(noise)) * 2.0) - 1.0) * Math.exp(-8.0 * rel);
            }
            case "drum_tom" -> {
                double tomPhase = phase * Math.max(0.5, 1.0 - (rel * 1.5));
                wave = (
                    0.7 * Math.sin(2.0 * Math.PI * tomPhase)
                        + 0.2 * Math.sin(4.0 * Math.PI * tomPhase)
                    ) * Math.exp(-7.0 * rel);
            }
            case "noise" -> {
                double seed = (sampleIndex + 1.0) * (note.note + 17.0) * 12.9898;
                double noise = Math.sin(seed) * 43758.5453;
                wave = ((noise - Math.floor(noise)) * 2.0) - 1.0;
                envelope *= Math.exp(-10.0 * rel);
            }
            default -> wave = Math.sin(2.0 * Math.PI * phase);
        }

        return wave * note.gain * envelope;
    }

    private static double midiAttack(String voice, double duration) {
        return switch (voice) {
            case "pad", "strings", "choir" -> Math.min(0.05, Math.max(0.01, duration * 0.25));
            case "organ" -> Math.min(0.015, duration * 0.12);
            case "brass", "reed", "flute" -> Math.min(0.02, duration * 0.15);
            case "drum_kick", "drum_snare", "drum_hat", "drum_cymbal", "drum_tom", "mallet", "pluck", "guitar", "piano", "bell" ->
                Math.min(0.004, duration * 0.08);
            default -> Math.min(0.008, duration * 0.2);
        };
    }

    private static double midiRelease(String voice, double duration) {
        return switch (voice) {
            case "pad", "strings", "choir" -> Math.min(0.22, duration * 0.55);
            case "organ" -> Math.min(0.08, duration * 0.35);
            case "flute", "brass", "reed" -> Math.min(0.12, duration * 0.4);
            case "drum_kick", "drum_snare", "drum_hat", "drum_cymbal", "drum_tom" -> Math.min(0.04, duration * 0.3);
            case "piano", "mallet", "pluck", "guitar", "bell" -> Math.min(0.09, duration * 0.35);
            default -> Math.min(0.08, duration * 0.45);
        };
    }

    private static double midiSustain(String voice, double rel, double duration) {
        return switch (voice) {
            case "piano" -> Math.exp(-1.2 * rel);
            case "mallet", "bell" -> Math.exp(-2.8 * rel);
            case "guitar", "pluck" -> Math.exp(-2.2 * rel);
            case "bass" -> 0.92 - Math.min(0.35, rel / Math.max(0.25, duration));
            case "strings", "pad", "choir" -> 0.82;
            case "organ" -> 0.95;
            case "flute", "reed" -> 0.88;
            case "brass", "lead", "synthfx" -> 0.9;
            case "drum_kick", "drum_snare", "drum_hat", "drum_cymbal", "drum_tom" -> Math.exp(-5.0 * rel);
            default -> 1.0;
        };
    }

    private static double midiSoftClip(double sample) {
        if (sample >= -1.0 && sample <= 1.0) return sample;
        if (sample > 0.0) return sample / (1.0 + sample);
        return sample / (1.0 - sample);
    }

    private static double mapDouble(Map<?, ?> map, String key) throws LuaException {
        Object value = map.get(key);
        if (value instanceof Number number) return number.doubleValue();
        throw new LuaException("Expected numeric MIDI note field '" + key + "'");
    }

    private static int mapInt(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String mapString(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value.toString();
    }

    private static PythonActionResponse success() {
        return PythonActionResponse.success(List.of());
    }

    private static PythonActionResponse success(Object value) {
        if (value == null) return success();
        if (value instanceof Object[] array) return PythonActionResponse.success(LuaValues.toList(array));
        return PythonActionResponse.success(List.of(LuaValues.normalize(value)));
    }

    private static PythonActionResponse maybeOne(Object value) {
        return value == null ? success() : success(value);
    }

    private static PythonActionResponse successValues(List<Object> values) {
        return PythonActionResponse.success(values == null ? List.of() : values);
    }

    private static final class SimpleLuaTable extends LinkedHashMap<Object, Object> implements LuaTable<Object, Object> {
        private SimpleLuaTable(List<?> values) {
            super(values.size());
            for (int i = 0; i < values.size(); i++) put((double) (i + 1), LuaValues.normalize(values.get(i)));
        }

        private SimpleLuaTable(Map<?, ?> values) {
            super(values.size());
            for (var entry : values.entrySet()) put(normalizeLuaKey(entry.getKey()), LuaValues.normalize(entry.getValue()));
        }

        private static Object normalizeLuaKey(Object key) {
            if (key instanceof Number number) return number.doubleValue();
            return key;
        }
    }

    private record MidiAudioNote(double start, double end, double frequency, double gain, String voice, int note) {
    }

    private record TimedSequenceEvent(long tick, int priority, int order, MidiMessage message) {
    }

    private record ResolvedSoundfont(Path path, String source, boolean temporary) {
    }

    private record RenderedSoundfontAudio(Path pcmPath, long samples, double peak, double gain) {
    }

    private static String message(Throwable throwable) {
        if (throwable instanceof FileSystemException exception) return exception.getMessage();
        if (throwable instanceof LuaException exception) return exception.getMessage();
        if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) return throwable.getMessage();
        return throwable.getClass().getSimpleName();
    }

    private static Field findInnerComputerField() {
        try {
            Field field = ServerComputer.class.getDeclaredField("computer");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access ServerComputer internals.", exception);
        }
    }

    private static Field findComputerSystemField(String name) {
        try {
            Class<?> klass = Class.forName("dan200.computercraft.shared.computer.core.ComputerSystem");
            Field field = klass.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Method findSpeakerMethod(String name) {
        try {
            Method method = SpeakerPeripheral.class.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access SpeakerPeripheral." + name + "().", exception);
        }
    }

    private record NativeComputerAccess(
        IComputerAccess computerSystem,
        ServerComputer serverComputer,
        Computer computer,
        IAPIEnvironment environment,
        ServerContext serverContext
    ) {
        static NativeComputerAccess resolve(dan200.computercraft.api.lua.IComputerSystem computerSystem) throws ReflectiveOperationException {
            if (COMPUTER_SYSTEM_SERVER_COMPUTER_FIELD != null
                && COMPUTER_SYSTEM_ENVIRONMENT_FIELD != null
                && COMPUTER_SYSTEM_SERVER_COMPUTER_FIELD.getDeclaringClass().isInstance(computerSystem)) {
                var serverComputer = (ServerComputer) COMPUTER_SYSTEM_SERVER_COMPUTER_FIELD.get(computerSystem);
                var environment = (IAPIEnvironment) COMPUTER_SYSTEM_ENVIRONMENT_FIELD.get(computerSystem);
                if (serverComputer != null && environment != null) {
                    var computer = (Computer) INNER_COMPUTER_FIELD.get(serverComputer);
                    return new NativeComputerAccess(
                        (IComputerAccess) computerSystem,
                        serverComputer,
                        computer,
                        environment,
                        ServerContext.get(computerSystem.getLevel().getServer())
                    );
                }
            }

            var level = computerSystem.getLevel();
            var blockEntity = level.getBlockEntity(computerSystem.getPosition());
            if (!(blockEntity instanceof AbstractComputerBlockEntity computerBlockEntity)) return null;

            var serverComputer = computerBlockEntity.getServerComputer();
            if (serverComputer == null) serverComputer = computerBlockEntity.createServerComputer();
            if (serverComputer == null) return null;

            var computer = (Computer) INNER_COMPUTER_FIELD.get(serverComputer);
            return new NativeComputerAccess(
                computerSystem,
                serverComputer,
                computer,
                computer.getAPIEnvironment(),
                ServerContext.get(level.getServer())
            );
        }

        FileSystem fileSystem() {
            return environment.getFileSystem();
        }
    }

    private static final class BlockingLuaContext implements ILuaContext {
        private final Computer computer;

        private BlockingLuaContext(Computer computer) {
            this.computer = computer;
        }

        @Override
        public long issueMainThreadTask(LuaTask task) throws LuaException {
            throw new LuaException("Asynchronous peripheral tasks are not yet supported in the native Python backend.");
        }

        @Override
        public MethodResult executeMainThreadTask(LuaTask task) throws LuaException {
            var future = new CompletableFuture<Object[]>();
            if (!computer.queueMainThread(() -> {
                try {
                    future.complete(task.execute());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            })) {
                throw new LuaException("Failed to queue peripheral task on the server main thread.");
            }

            try {
                var result = future.get();
                return result == null ? MethodResult.of() : MethodResult.of(result);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new LuaException("Interrupted while waiting for a peripheral main-thread task.");
            } catch (ExecutionException exception) {
                var cause = exception.getCause();
                if (cause instanceof LuaException luaException) throw luaException;
                throw new LuaException(message(cause == null ? exception : cause));
            }
        }
    }
}
