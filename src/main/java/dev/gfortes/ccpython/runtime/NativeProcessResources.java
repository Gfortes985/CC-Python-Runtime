package dev.gfortes.ccpython.runtime;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.core.filesystem.FileSystemWrapper;
import dev.gfortes.ccpython.monitor.ManagedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class NativeProcessResources {
    static final Set<OpenOption> WRITE_OPTIONS = Set.of(
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    );
    static final Set<OpenOption> APPEND_OPTIONS = Set.of(
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
    );

    private static final Set<OpenOption> READ_WRITE_OPTIONS = Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE);
    private static final Set<OpenOption> WRITE_EXTENDED_OPTIONS = Set.of(
        StandardOpenOption.READ,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    );
    private static final Set<OpenOption> APPEND_EXTENDED_OPTIONS = Set.of(
        StandardOpenOption.READ,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
    );

    private final Map<String, NativeFileHandle> fileHandles = new HashMap<>();
    private final Map<String, ManagedImage> imageHandles = new HashMap<>();
    private final Map<String, IPeripheral> attachedPeripherals = new HashMap<>();
    private int nextHandleId = 1;

    String openFile(FileSystem fileSystem, String path, String mode, int maxHandles) throws IOException, LuaException, FileSystemException {
        if (fileHandles.size() >= maxHandles) {
            throw new LuaException("Python process exceeded the configured open file handle limit (" + maxHandles + ")");
        }

        var normalizedMode = mode == null || mode.isBlank() ? "r" : mode;
        var handle = switch (normalizedMode) {
            case "r", "rb" -> new NativeFileHandle(fileSystem.openForRead(path));
            case "w", "wb" -> new NativeFileHandle(fileSystem.openForWrite(path, WRITE_OPTIONS));
            case "a", "ab" -> new NativeFileHandle(fileSystem.openForWrite(path, APPEND_OPTIONS));
            case "r+", "rb+", "r+b" -> new NativeFileHandle(fileSystem.openForWrite(path, READ_WRITE_OPTIONS));
            case "w+", "wb+", "w+b" -> new NativeFileHandle(fileSystem.openForWrite(path, WRITE_EXTENDED_OPTIONS));
            case "a+", "ab+", "a+b" -> new NativeFileHandle(fileSystem.openForWrite(path, APPEND_EXTENDED_OPTIONS));
            default -> throw new LuaException("Unsupported Python file mode '" + normalizedMode + "'");
        };

        var token = Integer.toString(nextHandleId++);
        fileHandles.put(token, handle);
        return token;
    }

    Object read(String token, Integer count) throws IOException, LuaException {
        return handle(token).read(count);
    }

    Object readLine(String token) throws IOException, LuaException {
        return handle(token).readLine();
    }

    Object readAll(String token) throws IOException, LuaException {
        return handle(token).readAll();
    }

    void write(String token, String value) throws IOException, LuaException {
        handle(token).write(value);
    }

    void writeLine(String token, String value) throws IOException, LuaException {
        handle(token).writeLine(value);
    }

    void flush(String token) throws IOException, LuaException {
        handle(token).flush();
    }

    long seek(String token, String whence, long offset) throws IOException, LuaException {
        return handle(token).seek(whence, offset);
    }

    void close(String token) throws IOException, LuaException {
        var handle = handle(token);
        fileHandles.remove(token);
        handle.close();
    }

    void ensurePeripheralAttached(String side, IPeripheral peripheral, IComputerAccess computer) {
        var existing = attachedPeripherals.get(side);
        if (existing == peripheral) return;

        if (existing != null) {
            try {
                existing.detach(computer);
            } catch (RuntimeException ignored) {
            }
        }

        peripheral.attach(computer);
        attachedPeripherals.put(side, peripheral);
    }

    String registerImage(ManagedImage image) {
        var token = Integer.toString(nextHandleId++);
        imageHandles.put(token, image);
        return token;
    }

    ManagedImage image(String token) throws LuaException {
        var image = imageHandles.get(token);
        if (image == null) throw new LuaException("Unknown Python image handle '" + token + "'");
        return image;
    }

    void closeImage(String token) throws LuaException {
        image(token);
        imageHandles.remove(token);
    }

    void closeAll(IComputerAccess computer) {
        for (var handle : fileHandles.values()) {
            try {
                handle.close();
            } catch (IOException ignored) {
            }
        }
        fileHandles.clear();
        imageHandles.clear();

        var detached = new HashSet<IPeripheral>(attachedPeripherals.values());
        attachedPeripherals.clear();
        for (var peripheral : detached) {
            try {
                peripheral.detach(computer);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private NativeFileHandle handle(String token) throws LuaException {
        var handle = fileHandles.get(token);
        if (handle == null) throw new LuaException("Unknown Python file handle '" + token + "'");
        return handle;
    }

    private static final class NativeFileHandle implements AutoCloseable {
        private final FileSystemWrapper<SeekableByteChannel> wrapper;
        private final SeekableByteChannel channel;

        private NativeFileHandle(FileSystemWrapper<SeekableByteChannel> wrapper) {
            this.wrapper = wrapper;
            this.channel = wrapper.get();
        }

        private Object read(Integer count) throws IOException {
            if (count == null) return readAll();
            if (count <= 0) return "";

            var buffer = ByteBuffer.allocate(count);
            int read = channel.read(buffer);
            if (read <= 0) return null;

            buffer.flip();
            return StandardCharsets.ISO_8859_1.decode(buffer).toString();
        }

        private Object readLine() throws IOException {
            var output = new ByteArrayOutputStream();
            boolean sawAny = false;

            while (true) {
                var buffer = ByteBuffer.allocate(1);
                int read = channel.read(buffer);
                if (read <= 0) break;

                sawAny = true;
                buffer.flip();
                byte value = buffer.get();
                if (value == '\n') break;
                if (value != '\r') output.write(value);
            }

            if (!sawAny && output.size() == 0) return null;
            return output.toString(StandardCharsets.ISO_8859_1);
        }

        private Object readAll() throws IOException {
            var output = new ByteArrayOutputStream();
            var buffer = ByteBuffer.allocate(4096);

            while (true) {
                int read = channel.read(buffer);
                if (read <= 0) break;
                buffer.flip();
                output.write(buffer.array(), 0, read);
                buffer.clear();
            }

            return output.toString(StandardCharsets.ISO_8859_1);
        }

        private void write(String value) throws IOException {
            channel.write(ByteBuffer.wrap((value == null ? "" : value).getBytes(StandardCharsets.ISO_8859_1)));
        }

        private void writeLine(String value) throws IOException {
            write((value == null ? "" : value) + "\n");
        }

        private void flush() {
        }

        private long seek(String whence, long offset) throws IOException, LuaException {
            long current = channel.position();
            long target = switch (whence == null ? "cur" : whence) {
                case "set" -> offset;
                case "cur" -> current + offset;
                case "end" -> channel.size() + offset;
                default -> throw new LuaException("Unsupported seek mode '" + whence + "'");
            };

            if (target < 0) throw new LuaException("Negative seek position is not allowed");
            channel.position(target);
            return target;
        }

        @Override
        public void close() throws IOException {
            wrapper.close();
        }
    }
}
