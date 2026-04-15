package dev.gfortes.ccpython.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.core.ServerContext;
import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.config.CCPythonConfig;
import dev.gfortes.ccpython.runtime.FileSystemAdapter;
import dev.gfortes.ccpython.runtime.PythonRuntimeManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;

public final class DevBridgeManager {
    private static final String API_ROOT = "/ccpython/bridge/v1";
    private static final Set<OpenOption> WRITE_OPTIONS = Set.of(
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    );
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private static final DevBridgeManager INSTANCE = new DevBridgeManager();

    private final Object lifecycleLock = new Object();

    private volatile MinecraftServer minecraftServer;
    private volatile HttpServer httpServer;
    private volatile ExecutorService executor;
    private volatile InetSocketAddress boundAddress;

    private DevBridgeManager() {
    }

    public static DevBridgeManager getInstance() {
        return INSTANCE;
    }

    public void start(MinecraftServer server) {
        synchronized (lifecycleLock) {
            stopUnlocked();

            if (!CCPythonConfig.devBridgeEnabled()) {
                CCPythonMod.LOGGER.info("CCPython dev bridge is disabled in config.");
                return;
            }

            try {
                InetSocketAddress address = resolveBindAddress();
                HttpServer bridge = HttpServer.create(address, 0);
                ExecutorService pool = Executors.newCachedThreadPool(runnable -> {
                    Thread thread = new Thread(runnable, "ccpython-dev-bridge-" + THREAD_COUNTER.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });

                bridge.createContext(API_ROOT, this::handle);
                bridge.createContext(API_ROOT + "/", this::handle);
                bridge.setExecutor(pool);
                bridge.start();

                minecraftServer = server;
                httpServer = bridge;
                executor = pool;
                boundAddress = bridge.getAddress();

                if (CCPythonConfig.devBridgeAllowRemote() && CCPythonConfig.devBridgeAuthToken().isBlank()) {
                    CCPythonMod.LOGGER.warn(
                        "CCPython dev bridge is listening on {}:{} with remote access enabled, but devBridge.authToken is empty. Only localhost requests will be accepted.",
                        boundAddress.getHostString(),
                        boundAddress.getPort()
                    );
                } else {
                    CCPythonMod.LOGGER.info(
                        "CCPython dev bridge listening on {}:{} (remote={}, auth={}).",
                        boundAddress.getHostString(),
                        boundAddress.getPort(),
                        CCPythonConfig.devBridgeAllowRemote(),
                        requiresRemoteAuth()
                    );
                }
            } catch (IOException exception) {
                CCPythonMod.LOGGER.error("Failed to start CCPython dev bridge.", exception);
                stopUnlocked();
            }
        }
    }

    public void stop() {
        synchronized (lifecycleLock) {
            stopUnlocked();
        }
    }

    private void stopUnlocked() {
        HttpServer bridge = httpServer;
        httpServer = null;
        if (bridge != null) bridge.stop(0);

        ExecutorService pool = executor;
        executor = null;
        if (pool != null) pool.shutdownNow();

        minecraftServer = null;
        boundAddress = null;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 204, new byte[0]);
                return;
            }

            MinecraftServer server = minecraftServer;
            if (server == null) {
                sendJson(exchange, 503, error("CCPython dev bridge is not attached to a running server."));
                return;
            }

            if (!authorize(exchange)) {
                sendJson(exchange, 401, error("Unauthorized. Localhost is trusted; remote requests require devBridge.allowRemote=true and a matching Bearer token."));
                return;
            }

            Object response = route(exchange, server);
            sendJson(exchange, 200, response);
        } catch (Throwable throwable) {
            Throwable cause = unwrap(throwable);
            if (cause instanceof BridgeException bridgeException) {
                sendJson(exchange, bridgeException.status(), error(bridgeException.getMessage()));
                return;
            }

            CCPythonMod.LOGGER.warn("CCPython dev bridge request failed.", cause);
            sendJson(exchange, 500, error(message(cause)));
        }
    }

    private Object route(HttpExchange exchange, MinecraftServer server) throws Exception {
        String method = exchange.getRequestMethod().toUpperCase();
        String route = routePath(exchange);
        Map<String, String> query = queryParameters(exchange);

        if ("/ping".equals(route) && "GET".equals(method)) {
            return ping(server);
        }

        if ("/computers".equals(route) && "GET".equals(method)) {
            return ok("computers", callOnServerThread(server, () -> listComputers(server)));
        }

        List<String> segments = pathSegments(route);
        if (segments.size() < 2 || !"computers".equals(segments.get(0))) {
            throw new BridgeException(404, "Unknown bridge endpoint '" + route + "'.");
        }

        int computerId = parseComputerId(segments.get(1));

        if (segments.size() == 2 && "GET".equals(method)) {
            return ok("computer", withComputer(server, computerId, this::describeComputer));
        }

        if (segments.size() == 3 && "GET".equals(method) && "files".equals(segments.get(2))) {
            String path = query.getOrDefault("path", "/");
            return withComputerAccess(server, computerId, (computer, access) -> listFiles(computer, access, path));
        }

        if (segments.size() == 3 && "GET".equals(method) && "file".equals(segments.get(2))) {
            String path = requireQuery(query, "path");
            String encoding = normalizeEncoding(query.getOrDefault("encoding", "utf8"));
            return withComputerAccess(server, computerId, (computer, access) -> readFile(computer, access, path, encoding));
        }

        if (segments.size() == 3 && "PUT".equals(method) && "file".equals(segments.get(2))) {
            String path = requireQuery(query, "path");
            String encoding = normalizeEncoding(query.getOrDefault("encoding", "raw"));
            byte[] body = exchange.getRequestBody().readAllBytes();
            return withComputerAccess(server, computerId, (computer, access) -> writeFile(computer, access, path, encoding, body));
        }

        if (segments.size() == 3 && "DELETE".equals(method) && "file".equals(segments.get(2))) {
            String path = requireQuery(query, "path");
            return withComputerAccess(server, computerId, (computer, access) -> deleteFile(computer, access, path));
        }

        if (segments.size() == 3 && "POST".equals(method) && "mkdir".equals(segments.get(2))) {
            String path = requireQuery(query, "path");
            return withComputerAccess(server, computerId, (computer, access) -> makeDir(computer, access, path));
        }

        if (segments.size() == 3 && "POST".equals(method) && "move".equals(segments.get(2))) {
            String from = requireQuery(query, "from");
            String to = requireQuery(query, "to");
            return withComputerAccess(server, computerId, (computer, access) -> move(computer, access, from, to));
        }

        if (segments.size() == 4 && "POST".equals(method) && "power".equals(segments.get(2))) {
            return withComputer(server, computerId, computer -> power(computer, segments.get(3)));
        }

        throw new BridgeException(404, "Unknown bridge endpoint '" + route + "'.");
    }

    private Map<String, Object> ping(MinecraftServer server) {
        var map = new LinkedHashMap<String, Object>();
        map.put("ok", true);
        map.put("bridge", "ccpython-dev-bridge");
        map.put("version", 1);
        map.put("server_type", server.isDedicatedServer() ? "dedicated" : "integrated");
        map.put("remote_enabled", CCPythonConfig.devBridgeAllowRemote());
        map.put("remote_auth_required", requiresRemoteAuth());
        map.put("host", boundAddress == null ? null : boundAddress.getHostString());
        map.put("port", boundAddress == null ? null : boundAddress.getPort());
        map.put("supports", List.of(
            "list-computers",
            "computer-info",
            "list-files",
            "read-file",
            "write-file",
            "delete-file",
            "make-dir",
            "move",
            "power"
        ));
        return map;
    }

    private List<Map<String, Object>> listComputers(MinecraftServer server) {
        Map<Integer, Long> pythonCounts = PythonRuntimeManager.getInstance().activeSnapshots().stream()
            .collect(Collectors.groupingBy(snapshot -> snapshot.computerId(), Collectors.counting()));

        var computers = new ArrayList<ServerComputer>();
        for (ServerComputer computer : ServerContext.get(server).registry().getComputers()) {
            computers.add(computer);
        }
        computers.sort(Comparator.comparingInt(ServerComputer::getID));

        var result = new ArrayList<Map<String, Object>>(computers.size());
        for (ServerComputer computer : computers) {
            result.add(describeComputer(computer, pythonCounts.getOrDefault(computer.getID(), 0L)));
        }
        return result;
    }

    private Map<String, Object> describeComputer(ServerComputer computer) {
        return describeComputer(computer, pythonCount(computer.getID()));
    }

    private long pythonCount(int computerId) {
        return PythonRuntimeManager.getInstance().activeSnapshots().stream()
            .filter(snapshot -> snapshot.computerId() == computerId)
            .count();
    }

    private Map<String, Object> describeComputer(ServerComputer computer, long pythonProcesses) {
        var position = new LinkedHashMap<String, Object>();
        position.put("x", computer.getPosition().getX());
        position.put("y", computer.getPosition().getY());
        position.put("z", computer.getPosition().getZ());

        var map = new LinkedHashMap<String, Object>();
        map.put("id", computer.getID());
        map.put("label", computer.getLabel());
        map.put("on", computer.isOn());
        map.put("dimension", computer.getLevel().dimension().location().toString());
        map.put("position", position);
        map.put("python_processes", pythonProcesses);
        return map;
    }

    private Map<String, Object> listFiles(ServerComputer computer, BridgeComputerAccess access, String rawPath) throws Exception {
        String path = normalizePath(rawPath);
        FileSystem fileSystem = access.fileSystem();

        if (!exists(fileSystem, path)) throw new BridgeException(404, "Path '" + path + "' does not exist.");
        if (!isDir(fileSystem, path)) throw new BridgeException(400, "Path '" + path + "' is not a directory.");

        List<String> names = new ArrayList<>(fileSystem.list(path));
        names.sort(String::compareToIgnoreCase);

        var entries = new ArrayList<Map<String, Object>>(names.size());
        for (String name : names) {
            String childPath = FileSystemAdapter.combine(path, name);
            entries.add(fileEntry(fileSystem, childPath, name));
        }

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("path", path);
        map.put("entries", entries);
        return map;
    }

    private Map<String, Object> readFile(ServerComputer computer, BridgeComputerAccess access, String rawPath, String encoding) throws Exception {
        String path = normalizePath(rawPath);
        FileSystem fileSystem = access.fileSystem();

        if (!exists(fileSystem, path)) throw new BridgeException(404, "Path '" + path + "' does not exist.");
        if (isDir(fileSystem, path)) throw new BridgeException(400, "Path '" + path + "' is a directory.");

        byte[] bytes = readAllBytes(fileSystem, path);
        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("path", path);
        map.put("encoding", encoding);
        map.put("size", bytes.length);
        map.put("content", encodeContent(bytes, encoding));
        return map;
    }

    private Map<String, Object> writeFile(ServerComputer computer, BridgeComputerAccess access, String rawPath, String encoding, byte[] body) throws Exception {
        String path = normalizePath(rawPath);
        FileSystem fileSystem = access.fileSystem();

        if ("/".equals(path)) throw new BridgeException(400, "Cannot overwrite the filesystem root.");
        if (exists(fileSystem, path) && isDir(fileSystem, path)) throw new BridgeException(400, "Path '" + path + "' is a directory.");

        byte[] bytes = decodeContent(body, encoding);
        writeAllBytes(fileSystem, path, bytes);

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("path", path);
        map.put("bytes_written", bytes.length);
        return map;
    }

    private Map<String, Object> deleteFile(ServerComputer computer, BridgeComputerAccess access, String rawPath) throws Exception {
        String path = normalizePath(rawPath);
        FileSystem fileSystem = access.fileSystem();

        if (!exists(fileSystem, path)) throw new BridgeException(404, "Path '" + path + "' does not exist.");
        if ("/".equals(path)) throw new BridgeException(400, "Cannot delete the filesystem root.");

        fileSystem.delete(path);

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("path", path);
        map.put("deleted", true);
        return map;
    }

    private Map<String, Object> makeDir(ServerComputer computer, BridgeComputerAccess access, String rawPath) throws Exception {
        String path = normalizePath(rawPath);
        access.fileSystem().makeDir(path);

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("path", path);
        map.put("created", true);
        return map;
    }

    private Map<String, Object> move(ServerComputer computer, BridgeComputerAccess access, String rawFrom, String rawTo) throws Exception {
        String from = normalizePath(rawFrom);
        String to = normalizePath(rawTo);
        FileSystem fileSystem = access.fileSystem();

        if (!exists(fileSystem, from)) throw new BridgeException(404, "Path '" + from + "' does not exist.");
        if ("/".equals(from) || "/".equals(to)) throw new BridgeException(400, "Cannot move the filesystem root.");

        fileSystem.move(from, to);

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("from", from);
        map.put("to", to);
        return map;
    }

    private Map<String, Object> power(ServerComputer computer, String action) {
        switch (action) {
            case "on" -> computer.turnOn();
            case "off" -> computer.shutdown();
            case "reboot" -> computer.reboot();
            default -> throw new BridgeException(404, "Unknown power action '" + action + "'.");
        }

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("action", action);
        return map;
    }

    private Map<String, Object> fileEntry(FileSystem fileSystem, String path, String name) throws FileSystemException {
        var attributes = fileSystem.getAttributes(path);
        var map = new LinkedHashMap<String, Object>();
        map.put("name", name);
        map.put("path", path);
        map.put("is_dir", attributes.isDirectory());
        map.put("size", attributes.isDirectory() ? 0L : attributes.size());
        map.put("modified", attributes.lastModifiedTime().toMillis());
        map.put("created", attributes.creationTime().toMillis());
        map.put("read_only", fileSystem.isReadOnly(path));
        return map;
    }

    private static String encodeContent(byte[] bytes, String encoding) {
        return switch (encoding) {
            case "base64" -> Base64.getEncoder().encodeToString(bytes);
            case "latin1" -> new String(bytes, StandardCharsets.ISO_8859_1);
            case "raw", "utf8" -> new String(bytes, StandardCharsets.UTF_8);
            default -> throw new BridgeException(400, "Unsupported encoding '" + encoding + "'.");
        };
    }

    private static byte[] decodeContent(byte[] body, String encoding) {
        return switch (encoding) {
            case "raw", "utf8", "latin1" -> body;
            case "base64" -> Base64.getDecoder().decode(new String(body, StandardCharsets.UTF_8).trim());
            default -> throw new BridgeException(400, "Unsupported encoding '" + encoding + "'.");
        };
    }

    private static String normalizeEncoding(String value) {
        return value == null || value.isBlank() ? "utf8" : value.trim().toLowerCase();
    }

    private static String normalizePath(String rawPath) {
        return FileSystemAdapter.sanitize(rawPath == null || rawPath.isBlank() ? "/" : rawPath);
    }

    private boolean authorize(HttpExchange exchange) {
        if (isLoopback(exchange)) return true;
        if (!CCPythonConfig.devBridgeAllowRemote()) return false;

        String token = CCPythonConfig.devBridgeAuthToken();
        if (token.isBlank()) return false;

        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return ("Bearer " + token).equals(header);
    }

    private boolean isLoopback(HttpExchange exchange) {
        InetAddress address = exchange.getRemoteAddress().getAddress();
        return address != null && (address.isLoopbackAddress() || address.isAnyLocalAddress());
    }

    private boolean requiresRemoteAuth() {
        return CCPythonConfig.devBridgeAllowRemote();
    }

    private InetSocketAddress resolveBindAddress() throws IOException {
        if (!CCPythonConfig.devBridgeAllowRemote()) {
            return new InetSocketAddress(InetAddress.getByName("127.0.0.1"), CCPythonConfig.devBridgePort());
        }

        String configuredHost = CCPythonConfig.devBridgeHost();
        String host = configuredHost.isBlank() ? "0.0.0.0" : configuredHost;
        return new InetSocketAddress(InetAddress.getByName(host), CCPythonConfig.devBridgePort());
    }

    private String routePath(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        if (path.equals(API_ROOT)) return "/";
        if (path.startsWith(API_ROOT + "/")) return path.substring(API_ROOT.length());
        throw new BridgeException(404, "Unknown bridge endpoint '" + path + "'.");
    }

    private Map<String, String> queryParameters(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) return Map.of();

        var result = new LinkedHashMap<String, String>();
        for (String part : raw.split("&")) {
            if (part.isBlank()) continue;
            int separator = part.indexOf('=');
            String key = separator >= 0 ? part.substring(0, separator) : part;
            String value = separator >= 0 ? part.substring(separator + 1) : "";
            result.put(
                URLDecoder.decode(key, StandardCharsets.UTF_8),
                URLDecoder.decode(value, StandardCharsets.UTF_8)
            );
        }
        return result;
    }

    private List<String> pathSegments(String route) {
        var result = new ArrayList<String>();
        for (String part : route.split("/")) {
            if (!part.isBlank()) result.add(part);
        }
        return result;
    }

    private int parseComputerId(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new BridgeException(400, "Invalid computer id '" + raw + "'.");
        }
    }

    private String requireQuery(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null || value.isBlank()) throw new BridgeException(400, "Missing required query parameter '" + key + "'.");
        return value;
    }

    private <T> T withComputer(MinecraftServer server, int computerId, ComputerOperation<T> operation) throws Exception {
        return callOnServerThread(server, () -> operation.run(requireComputer(server, computerId)));
    }

    private <T> T withComputerAccess(MinecraftServer server, int computerId, ComputerAccessOperation<T> operation) throws Exception {
        return callOnServerThread(server, () -> {
            ServerComputer computer = requireComputer(server, computerId);
            return operation.run(computer, BridgeComputerAccess.resolve(computer));
        });
    }

    private ServerComputer requireComputer(MinecraftServer server, int computerId) {
        for (ServerComputer computer : ServerContext.get(server).registry().getComputers()) {
            if (computer.getID() == computerId) return computer;
        }
        throw new BridgeException(404, "Computer " + computerId + " is not currently loaded.");
    }

    private <T> T callOnServerThread(MinecraftServer server, CheckedSupplier<T> supplier) throws Exception {
        var future = new CompletableFuture<T>();
        server.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });

        try {
            return future.get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception typed) throw typed;
            if (cause instanceof Error error) throw error;
            throw new RuntimeException(cause);
        }
    }

    private static byte[] readAllBytes(FileSystem fileSystem, String path) throws IOException, FileSystemException {
        try (var wrapper = fileSystem.openForRead(path)) {
            var input = java.nio.channels.Channels.newInputStream(wrapper.get());
            var output = new ByteArrayOutputStream();
            input.transferTo(output);
            return output.toByteArray();
        }
    }

    private static void writeAllBytes(FileSystem fileSystem, String path, byte[] bytes) throws IOException, FileSystemException {
        try (var wrapper = fileSystem.openForWrite(path, WRITE_OPTIONS)) {
            wrapper.get().write(ByteBuffer.wrap(bytes));
        }
    }

    private static boolean exists(FileSystem fileSystem, String path) {
        try {
            return fileSystem.exists(path);
        } catch (FileSystemException ignored) {
            return false;
        }
    }

    private static boolean isDir(FileSystem fileSystem, String path) {
        try {
            return fileSystem.isDir(path);
        } catch (FileSystemException ignored) {
            return false;
        }
    }

    private static Map<String, Object> ok() {
        var map = new LinkedHashMap<String, Object>();
        map.put("ok", true);
        return map;
    }

    private static Map<String, Object> ok(String key, Object value) {
        var map = ok();
        map.put(key, value);
        return map;
    }

    private static Map<String, Object> error(String message) {
        var map = new LinkedHashMap<String, Object>();
        map.put("ok", false);
        map.put("error", message);
        return map;
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        send(exchange, status, BridgeJson.encode(payload));
    }

    private void send(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(status, bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof ExecutionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String message(Throwable throwable) {
        if (throwable instanceof FileSystemException fileSystemException) {
            return fileSystemException.getMessage();
        }
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
            ? throwable.getClass().getSimpleName()
            : throwable.getMessage();
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface ComputerOperation<T> {
        T run(ServerComputer computer) throws Exception;
    }

    @FunctionalInterface
    private interface ComputerAccessOperation<T> {
        T run(ServerComputer computer, BridgeComputerAccess access) throws Exception;
    }

    private static final class BridgeException extends RuntimeException {
        private final int status;

        private BridgeException(int status, String message) {
            super(message);
            this.status = status;
        }

        private int status() {
            return status;
        }
    }
}
