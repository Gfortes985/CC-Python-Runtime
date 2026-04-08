package dev.gfortes.ccpython.runtime;

import dan200.computercraft.api.lua.Coerced;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaTask;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.lua.ObjectArguments;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.apis.OSAPI;
import dan200.computercraft.core.apis.RedstoneAPI;
import dan200.computercraft.core.apis.TermAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.core.ServerContext;
import dev.gfortes.ccpython.config.CCPythonConfig;
import dev.gfortes.ccpython.util.LuaValues;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

final class NativeHostDispatcher {
    private static final Field INNER_COMPUTER_FIELD = findInnerComputerField();

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
            case "write" -> {
                access.terminal().write(stringArg(arguments, 0, ""));
                yield success();
            }
            case "read" -> success(NativeLineReader.read(process, access.terminal()));
            case "sleep" -> {
                sleep(access, process, doubleArg(arguments, 0));
                yield success();
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
        var terminal = access.terminal();
        var api = new TermAPI(access.environment());
        return switch (method) {
            case "write" -> {
                api.write(new Coerced<>(stringArg(arguments, 0, "")));
                yield success();
            }
            case "scroll" -> {
                api.scroll(intArg(arguments, 0));
                yield success();
            }
            case "getCursorPos" -> success(api.getCursorPos());
            case "setCursorPos" -> {
                api.setCursorPos(intArg(arguments, 0), intArg(arguments, 1));
                yield success();
            }
            case "getCursorBlink" -> success(api.getCursorBlink());
            case "setCursorBlink" -> {
                api.setCursorBlink(boolArg(arguments, 0));
                yield success();
            }
            case "getSize" -> success(api.getSize());
            case "clear" -> {
                api.clear();
                yield success();
            }
            case "clearLine" -> {
                api.clearLine();
                yield success();
            }
            case "getTextColour", "getTextColor" -> success(api.getTextColour());
            case "setTextColour", "setTextColor" -> {
                api.setTextColour(intArg(arguments, 0));
                yield success();
            }
            case "getBackgroundColour", "getBackgroundColor" -> success(api.getBackgroundColour());
            case "setBackgroundColour", "setBackgroundColor" -> {
                api.setBackgroundColour(intArg(arguments, 0));
                yield success();
            }
            case "isColour", "isColor" -> success(api.getIsColour());
            case "blit" -> {
                terminal.blit(
                    toByteBuffer(stringArg(arguments, 0, "")),
                    toByteBuffer(stringArg(arguments, 1, "")),
                    toByteBuffer(stringArg(arguments, 2, ""))
                );
                yield success();
            }
            case "setPaletteColour", "setPaletteColor" -> {
                api.setPaletteColour(new ObjectArguments(arguments));
                yield success();
            }
            case "getPaletteColour", "getPaletteColor" -> success(api.getPaletteColour(intArg(arguments, 0)));
            case "nativePaletteColour", "nativePaletteColor" -> success(api.nativePaletteColour(intArg(arguments, 0)));
            default -> null;
        };
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

    private static Optional<String> optionalString(List<Object> arguments, int index) {
        if (index >= arguments.size() || arguments.get(index) == null) return Optional.empty();
        return Optional.of(arguments.get(index).toString());
    }

    private static Optional<Long> optionalLong(List<Object> arguments, int index) throws LuaException {
        if (index >= arguments.size() || arguments.get(index) == null) return Optional.empty();
        if (!(arguments.get(index) instanceof Number number)) throw new LuaException("Expected numeric argument at index " + index);
        return Optional.of(number.longValue());
    }

    private static ByteBuffer toByteBuffer(String value) {
        return ByteBuffer.wrap(value.getBytes(StandardCharsets.ISO_8859_1));
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

    private record NativeComputerAccess(
        IComputerAccess computerSystem,
        ServerComputer serverComputer,
        Computer computer,
        IAPIEnvironment environment,
        ServerContext serverContext
    ) {
        static NativeComputerAccess resolve(dan200.computercraft.api.lua.IComputerSystem computerSystem) throws ReflectiveOperationException {
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

        Terminal terminal() {
            return environment.getTerminal();
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
