package dev.gfortes.ccpython.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.shared.computer.terminal.TerminalState;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.core.ServerContext;
import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.config.CCPythonConfig;
import dev.gfortes.ccpython.runtime.FileSystemAdapter;
import dev.gfortes.ccpython.runtime.PythonLaunchSpec;
import dev.gfortes.ccpython.runtime.PythonProcess;
import dev.gfortes.ccpython.runtime.PythonProcessState;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
    private final DevBridgeAuthStore authStore = new DevBridgeAuthStore();
    private final DevBridgeAccessStore accessStore = new DevBridgeAccessStore();

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
                authStore.load();
                accessStore.load();
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

            String route = routePath(exchange);

            MinecraftServer server = minecraftServer;
            if (server == null) {
                sendJson(exchange, 503, error("CCPython dev bridge is not attached to a running server."));
                return;
            }

            AuthIdentity identity = authenticate(exchange, exchange.getRequestMethod(), route);
            if (identity == null) {
                sendJson(exchange, 401, error("Unauthorized. Localhost is trusted; remote requests require devBridge.allowRemote=true and a matching Bearer token."));
                return;
            }

            Object response = route(exchange, server, route, identity);
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

    private Object route(HttpExchange exchange, MinecraftServer server, String route, AuthIdentity identity) throws Exception {
        String method = exchange.getRequestMethod().toUpperCase();
        Map<String, String> query = queryParameters(exchange);

        if ("/ping".equals(route) && "GET".equals(method)) {
            return ping(server, identity);
        }

        if ("/auth/status".equals(route) && "GET".equals(method)) {
            return authStatus(exchange, server, identity);
        }

        if ("/auth/pair/start".equals(route) && "POST".equals(method)) {
            String label = query.getOrDefault("label", "VS Code");
            String player = query.get("player");
            return startPairing(server, identity, label, player);
        }

        if ("/auth/pair/complete".equals(route) && "POST".equals(method)) {
            String code = requireQuery(query, "code");
            String label = query.getOrDefault("label", "VS Code");
            return completePairing(server, code, label);
        }

        if ("/players".equals(route) && "GET".equals(method)) {
            return ok("players", callOnServerThread(server, () -> listPlayers(server, identity)));
        }

        if ("/computers".equals(route) && "GET".equals(method)) {
            return ok("computers", callOnServerThread(server, () -> listComputers(server, identity)));
        }

        List<String> segments = pathSegments(route);
        if (segments.size() < 2 || !"computers".equals(segments.get(0))) {
            throw new BridgeException(404, "Unknown bridge endpoint '" + route + "'.");
        }

        int computerId = parseComputerId(segments.get(1));

        if (segments.size() == 2 && "GET".equals(method)) {
            return ok("computer", withComputer(server, computerId, identity, this::describeComputer));
        }

        if (segments.size() == 3 && "GET".equals(method) && "files".equals(segments.get(2))) {
            String path = query.getOrDefault("path", "/");
            return withComputerAccess(server, computerId, identity, (computer, access) -> listFiles(computer, access, path));
        }

        if (segments.size() == 3 && "GET".equals(method) && "runtime".equals(segments.get(2))) {
            return withComputer(server, computerId, identity, this::runtimeState);
        }

        if (segments.size() == 3 && "GET".equals(method) && "terminal".equals(segments.get(2))) {
            return withComputer(server, computerId, identity, this::terminalState);
        }

        if (segments.size() == 4 && "POST".equals(method) && "terminal".equals(segments.get(2)) && "input".equals(segments.get(3))) {
            String kind = query.getOrDefault("kind", "paste");
            byte[] body = exchange.getRequestBody().readAllBytes();
            return withComputer(server, computerId, identity, computer -> terminalInput(computer, kind, query, body));
        }

        if (segments.size() == 3 && "GET".equals(method) && "search".equals(segments.get(2))) {
            String path = query.getOrDefault("path", "/");
            String searchQuery = requireQuery(query, "query");
            int limit = parseLimit(query.get("limit"));
            return withComputerAccess(server, computerId, identity, (computer, access) -> search(computer, access, path, searchQuery, limit));
        }

        if (segments.size() == 3 && "GET".equals(method) && "file".equals(segments.get(2))) {
            String path = requireQuery(query, "path");
            String encoding = normalizeEncoding(query.getOrDefault("encoding", "utf8"));
            return withComputerAccess(server, computerId, identity, (computer, access) -> readFile(computer, access, path, encoding));
        }

        if (segments.size() == 3 && "PUT".equals(method) && "file".equals(segments.get(2))) {
            String path = requireQuery(query, "path");
            String encoding = normalizeEncoding(query.getOrDefault("encoding", "raw"));
            byte[] body = exchange.getRequestBody().readAllBytes();
            return withComputerAccess(server, computerId, identity, (computer, access) -> writeFile(computer, access, path, encoding, body));
        }

        if (segments.size() == 3 && "DELETE".equals(method) && "file".equals(segments.get(2))) {
            String path = requireQuery(query, "path");
            return withComputerAccess(server, computerId, identity, (computer, access) -> deleteFile(computer, access, path));
        }

        if (segments.size() == 3 && "POST".equals(method) && "mkdir".equals(segments.get(2))) {
            String path = requireQuery(query, "path");
            return withComputerAccess(server, computerId, identity, (computer, access) -> makeDir(computer, access, path));
        }

        if (segments.size() == 3 && "POST".equals(method) && "move".equals(segments.get(2))) {
            String from = requireQuery(query, "from");
            String to = requireQuery(query, "to");
            return withComputerAccess(server, computerId, identity, (computer, access) -> move(computer, access, from, to));
        }

        if (segments.size() == 3 && "GET".equals(method) && "acl".equals(segments.get(2))) {
            return withComputer(server, computerId, identity, computer -> aclInfo(computer, identity));
        }

        if (segments.size() == 4 && "POST".equals(method) && "power".equals(segments.get(2))) {
            return withComputer(server, computerId, identity, computer -> power(computer, segments.get(3)));
        }

        if (segments.size() == 4 && "POST".equals(method) && "python".equals(segments.get(2)) && "run".equals(segments.get(3))) {
            String program = query.get("program");
            String cwd = query.getOrDefault("cwd", "/");
            boolean interactive = Boolean.parseBoolean(query.getOrDefault("interactive", "false"));
            return withComputerAccess(server, computerId, identity, (computer, access) -> runPython(computer, access, program, cwd, interactive));
        }

        if (segments.size() == 4 && "POST".equals(method) && "python".equals(segments.get(2)) && "stop".equals(segments.get(3))) {
            String processId = query.get("process_id");
            return withComputer(server, computerId, identity, computer -> stopPython(computer, processId));
        }

        if (segments.size() == 4 && "POST".equals(method) && "acl".equals(segments.get(2))) {
            String player = query.get("player");
            return switch (segments.get(3)) {
                case "claim" -> callOnServerThread(server, () -> claimAcl(server, requireLoadedComputer(server, computerId), identity, player));
                case "grant" -> withComputer(server, computerId, identity, computer -> grantAcl(server, computer, identity, requireQuery(query, "player")));
                case "revoke" -> withComputer(server, computerId, identity, computer -> revokeAcl(server, computer, identity, requireQuery(query, "player")));
                case "set-owner" -> withComputer(server, computerId, identity, computer -> setOwnerAcl(server, computer, identity, requireQuery(query, "player")));
                default -> throw new BridgeException(404, "Unknown ACL action '" + segments.get(3) + "'.");
            };
        }

        throw new BridgeException(404, "Unknown bridge endpoint '" + route + "'.");
    }

    public void assignOwnerIfAbsent(int computerId, UUID ownerUuid, String ownerName) {
        accessStore.assignOwnerIfAbsent(computerId, ownerUuid, ownerName);
    }

    public DevBridgeAccessStore.ComputerAcl acl(int computerId) {
        return accessStore.get(computerId);
    }

    public DevBridgeAccessStore.ComputerAcl acl(MinecraftServer server, int computerId) {
        requireLoadedComputer(server, computerId);
        return accessStore.get(computerId);
    }

    public DevBridgeAccessStore.ComputerAcl claimOwnership(MinecraftServer server, int computerId, UUID playerUuid, String playerName) {
        requireLoadedComputer(server, computerId);
        DevBridgeAccessStore.ComputerAcl acl = accessStore.claim(computerId, playerUuid, playerName);
        if (!acl.ownerUuid().equals(playerUuid)) {
            throw new BridgeException(403, "Computer " + computerId + " is already owned by " + acl.ownerName() + ".");
        }
        return acl;
    }

    public DevBridgeAccessStore.ComputerAcl grantAccess(MinecraftServer server, int computerId, UUID actorUuid, String actorName, boolean admin, UUID targetUuid, String targetName) {
        ServerComputer computer = requireLoadedComputer(server, computerId);
        AuthIdentity identity = admin ? AuthIdentity.admin("command", false) : AuthIdentity.player(actorUuid, actorName, "command");
        requireManagedAcl(computer, identity);
        return accessStore.grant(computerId, targetUuid, targetName);
    }

    public DevBridgeAccessStore.ComputerAcl revokeAccess(MinecraftServer server, int computerId, UUID actorUuid, String actorName, boolean admin, UUID targetUuid) {
        ServerComputer computer = requireLoadedComputer(server, computerId);
        AuthIdentity identity = admin ? AuthIdentity.admin("command", false) : AuthIdentity.player(actorUuid, actorName, "command");
        DevBridgeAccessStore.ComputerAcl acl = requireManagedAcl(computer, identity);
        if (acl.ownerUuid().equals(targetUuid)) {
            throw new BridgeException(400, "The owner cannot be revoked.");
        }
        return accessStore.revoke(computerId, targetUuid);
    }

    public DevBridgeAccessStore.ComputerAcl setOwner(MinecraftServer server, int computerId, UUID targetUuid, String targetName) {
        requireLoadedComputer(server, computerId);
        return accessStore.setOwner(computerId, targetUuid, targetName);
    }

    public DevBridgeAuthStore.PairingCode startPlayerPairing(String label, UUID playerUuid, String playerName) {
        if (!CCPythonConfig.devBridgeAllowRemote()) {
            throw new BridgeException(400, "Remote bridge access is disabled. Enable devBridge.allowRemote first.");
        }
        if (!CCPythonConfig.devBridgePairingEnabled()) {
            throw new BridgeException(403, "Bridge pairing is disabled in config.");
        }
        return authStore.startPairing(label, playerUuid, playerName, CCPythonConfig.devBridgePairingCodeTtlSeconds());
    }

    private Map<String, Object> ping(MinecraftServer server, AuthIdentity identity) {
        var map = new LinkedHashMap<String, Object>();
        map.put("ok", true);
        map.put("bridge", "ccpython-dev-bridge");
        map.put("version", 1);
        map.put("server_type", server.isDedicatedServer() ? "dedicated" : "integrated");
        map.put("remote_enabled", CCPythonConfig.devBridgeAllowRemote());
        map.put("remote_auth_required", requiresRemoteAuth());
        map.put("pairing_enabled", CCPythonConfig.devBridgePairingEnabled());
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
            "power",
            "runtime",
            "terminal",
            "search",
            "python-run",
            "python-stop",
            "pairing",
            "acl",
            "players"
        ));
        map.put("identity", identity.toMap());
        map.put("auth", authStore.describeAuthState());
        return map;
    }

    private List<Map<String, Object>> listComputers(MinecraftServer server, AuthIdentity identity) {
        Map<Integer, Long> pythonCounts = PythonRuntimeManager.getInstance().activeSnapshots().stream()
            .collect(Collectors.groupingBy(snapshot -> snapshot.computerId(), Collectors.counting()));

        var computers = new ArrayList<ServerComputer>();
        for (ServerComputer computer : ServerContext.get(server).registry().getComputers()) {
            if (!canAccessComputer(computer.getID(), identity)) continue;
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

        DevBridgeAccessStore.ComputerAcl acl = accessStore.get(computer.getID());
        Map<String, Object> owner = null;
        int whitelistCount = 0;
        if (acl != null && acl.ownerUuid() != null) {
            owner = new LinkedHashMap<>();
            owner.put("uuid", acl.ownerUuid().toString());
            owner.put("name", acl.ownerName());
            whitelistCount = acl.whitelist().size();
        }

        var map = new LinkedHashMap<String, Object>();
        map.put("id", computer.getID());
        map.put("label", computer.getLabel());
        map.put("on", computer.isOn());
        map.put("family", computer.getFamily().name().toLowerCase());
        map.put("dimension", computer.getLevel().dimension().location().toString());
        map.put("position", position);
        map.put("python_processes", pythonProcesses);
        map.put("owner", owner);
        map.put("whitelist_count", whitelistCount);
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

    private Map<String, Object> authStatus(HttpExchange exchange, MinecraftServer server, AuthIdentity identity) {
        var map = ok();
        map.put("server_type", server.isDedicatedServer() ? "dedicated" : "integrated");
        map.put("trusted_local", isLoopback(exchange));
        map.put("remote_enabled", CCPythonConfig.devBridgeAllowRemote());
        map.put("remote_auth_required", requiresRemoteAuth());
        map.put("pairing_enabled", CCPythonConfig.devBridgePairingEnabled());
        map.put("identity", identity.toMap());
        map.put("can_start_pairing", CCPythonConfig.devBridgeAllowRemote()
            && CCPythonConfig.devBridgePairingEnabled()
            && (identity.admin() || identity.isPlayer()));
        map.put("auth", authStore.describeAuthState());
        return map;
    }

    private Map<String, Object> startPairing(MinecraftServer server, AuthIdentity identity, String label, String playerRef) throws Exception {
        if (!CCPythonConfig.devBridgeAllowRemote()) {
            throw new BridgeException(400, "Remote bridge access is disabled. Enable devBridge.allowRemote first.");
        }
        if (!CCPythonConfig.devBridgePairingEnabled()) {
            throw new BridgeException(403, "Bridge pairing is disabled in config.");
        }

        PlayerIdentity player;
        if (identity.admin()) {
            if (playerRef == null || playerRef.isBlank()) {
                throw new BridgeException(400, "Admin pairing requires a target online player. Pass ?player=<name>.");
            }
            player = callOnServerThread(server, () -> resolvePlayerIdentity(server, playerRef));
        } else if (identity.isPlayer()) {
            player = new PlayerIdentity(identity.playerUuid(), identity.playerName() == null || identity.playerName().isBlank()
                ? identity.playerUuid().toString()
                : identity.playerName());
        } else {
            throw new BridgeException(403, "Pairing codes can only be created from a trusted admin session or an authenticated player session.");
        }

        var pairing = authStore.startPairing(label, player.uuid(), player.name(), CCPythonConfig.devBridgePairingCodeTtlSeconds());
        CCPythonMod.LOGGER.info(
            "Created dev bridge pairing code {} for '{}' as player '{}' (expires {}).",
            pairing.code(),
            pairing.label(),
            player.name(),
            Instant.ofEpochMilli(pairing.expiresAt())
        );

        var map = ok();
        map.put("pairing", pairing.toMap());
        map.put("player", player.toMap());
        map.put("message", "Share this code with the remote VS Code client before it expires.");
        return map;
    }

    private Map<String, Object> completePairing(MinecraftServer server, String code, String label) {
        if (!CCPythonConfig.devBridgeAllowRemote()) {
            throw new BridgeException(400, "Remote bridge access is disabled. Enable devBridge.allowRemote first.");
        }
        if (!CCPythonConfig.devBridgePairingEnabled()) {
            throw new BridgeException(403, "Bridge pairing is disabled in config.");
        }

        var issued = authStore.completePairing(code.trim().toUpperCase(), label);
        if (issued == null) {
            throw new BridgeException(401, "Invalid or expired pairing code.");
        }

        var map = ok();
        map.put("server_type", server.isDedicatedServer() ? "dedicated" : "integrated");
        map.put("token", issued.token());
        map.put("session", issued.toPublicMap());
        map.put("message", "Store this bearer token in the VS Code bridge settings.");
        return map;
    }

    private List<Map<String, Object>> listPlayers(MinecraftServer server, AuthIdentity identity) {
        return server.getPlayerList().getPlayers().stream()
            .sorted(Comparator.comparing(player -> player.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER))
            .map(player -> Map.<String, Object>of(
                "uuid", player.getUUID().toString(),
                "name", player.getGameProfile().getName()
            ))
            .toList();
    }

    private Map<String, Object> aclInfo(ServerComputer computer, AuthIdentity identity) {
        DevBridgeAccessStore.ComputerAcl acl = accessStore.get(computer.getID());
        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("acl", acl == null
            ? new DevBridgeAccessStore.ComputerAcl(computer.getID(), null, "", Map.of()).toMap()
            : acl.toMap());
        map.put("can_manage", canManageAcl(acl, identity));
        map.put("has_access", canAccessComputer(computer.getID(), identity));
        return map;
    }

    private Map<String, Object> claimAcl(MinecraftServer server, ServerComputer computer, AuthIdentity identity, String playerRef) {
        PlayerIdentity player = targetPlayerForClaim(server, computer, identity, playerRef);
        DevBridgeAccessStore.ComputerAcl acl = accessStore.claim(computer.getID(), player.uuid(), player.name());
        if (!acl.ownerUuid().equals(player.uuid())) {
            throw new BridgeException(403, "Computer " + computer.getID() + " is already owned by " + acl.ownerName() + ".");
        }

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("acl", acl.toMap());
        map.put("player", player.toMap());
        map.put("message", "Ownership claimed.");
        return map;
    }

    private Map<String, Object> grantAcl(MinecraftServer server, ServerComputer computer, AuthIdentity identity, String playerRef) {
        DevBridgeAccessStore.ComputerAcl current = requireManagedAcl(computer, identity);
        PlayerIdentity player = resolvePlayerIdentity(server, playerRef);
        if (current.ownerUuid().equals(player.uuid())) {
            throw new BridgeException(400, "The owner already has access.");
        }

        DevBridgeAccessStore.ComputerAcl acl = accessStore.grant(computer.getID(), player.uuid(), player.name());
        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("acl", acl.toMap());
        map.put("player", player.toMap());
        map.put("message", "Player access granted.");
        return map;
    }

    private Map<String, Object> revokeAcl(MinecraftServer server, ServerComputer computer, AuthIdentity identity, String playerRef) {
        DevBridgeAccessStore.ComputerAcl current = requireManagedAcl(computer, identity);
        PlayerIdentity player = resolvePlayerIdentity(server, playerRef);
        if (current.ownerUuid().equals(player.uuid())) {
            throw new BridgeException(400, "Use set-owner if you need to transfer ownership. The owner cannot be revoked.");
        }

        DevBridgeAccessStore.ComputerAcl acl = accessStore.revoke(computer.getID(), player.uuid());
        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("acl", acl.toMap());
        map.put("player", player.toMap());
        map.put("message", "Player access revoked.");
        return map;
    }

    private Map<String, Object> setOwnerAcl(MinecraftServer server, ServerComputer computer, AuthIdentity identity, String playerRef) {
        if (!identity.admin()) {
            throw new BridgeException(403, "Only bridge admins can transfer ownership.");
        }

        PlayerIdentity player = resolvePlayerIdentity(server, playerRef);
        DevBridgeAccessStore.ComputerAcl acl = accessStore.setOwner(computer.getID(), player.uuid(), player.name());

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("acl", acl.toMap());
        map.put("player", player.toMap());
        map.put("message", "Owner updated.");
        return map;
    }

    private Map<String, Object> runtimeState(ServerComputer computer) {
        var runtimeManager = PythonRuntimeManager.getInstance();
        var processes = new ArrayList<>(runtimeManager.processes(computer.getID()));
        processes.sort(Comparator.comparingLong(process -> process.snapshot().startedAt()));

        var processMaps = new ArrayList<Map<String, Object>>(processes.size());
        for (var process : processes) {
            processMaps.add(describeProcess(process));
        }

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("status", aggregateRuntimeStatus(processes));
        map.put("processes", processMaps);

        var lastSnapshot = runtimeManager.lastSnapshot(computer.getID());
        if (lastSnapshot != null) {
            map.put("last_process", describeSnapshot(lastSnapshot, runtimeManager.lastTraceback(computer.getID())));
        } else {
            map.put("last_process", null);
        }
        return map;
    }

    private Map<String, Object> terminalState(ServerComputer computer) {
        var map = ok();
        map.put("computer", describeComputer(computer));

        TerminalState state = computer.getTerminalState();
        if (state == null) {
            map.put("available", false);
            map.put("width", 0);
            map.put("height", 0);
            map.put("cursor_x", 0);
            map.put("cursor_y", 0);
            map.put("lines", List.of());
            return map;
        }

        var terminal = state.create();
        var lines = new ArrayList<Map<String, Object>>(terminal.getHeight());
        for (int index = 0; index < terminal.getHeight(); index++) {
            var line = new LinkedHashMap<String, Object>();
            line.put("index", index + 1);
            line.put("text", terminal.getLine(index).toString());
            line.put("text_color", terminal.getTextColourLine(index).toString());
            line.put("background_color", terminal.getBackgroundColourLine(index).toString());
            lines.add(line);
        }

        map.put("available", true);
        map.put("width", terminal.getWidth());
        map.put("height", terminal.getHeight());
        map.put("cursor_x", terminal.getCursorX());
        map.put("cursor_y", terminal.getCursorY());
        map.put("colour", terminal.isColour());
        map.put("lines", lines);
        return map;
    }

    private Map<String, Object> terminalInput(ServerComputer computer, String kind, Map<String, String> query, byte[] body) {
        switch (kind.toLowerCase()) {
            case "char" -> {
                String value = decodeTerminalInput(query.get("text"), body);
                if (value.isEmpty()) throw new BridgeException(400, "Character input cannot be empty.");
                for (int index = 0; index < value.length(); index++) {
                    computer.queueEvent("char", new Object[] { String.valueOf(value.charAt(index)) });
                }
            }
            case "paste" -> {
                String value = decodeTerminalInput(query.get("text"), body);
                if (value.isEmpty()) throw new BridgeException(400, "Paste input cannot be empty.");
                computer.queueEvent("paste", new Object[] { value });
            }
            case "key" -> {
                String rawKey = requireQuery(query, "key");
                int key;
                try {
                    key = Integer.parseInt(rawKey);
                } catch (NumberFormatException exception) {
                    throw new BridgeException(400, "Invalid key code '" + rawKey + "'.");
                }
                computer.queueEvent("key", new Object[] { key, false });
                computer.queueEvent("key_up", new Object[] { key });
            }
            case "terminate" -> computer.queueEvent("terminate");
            default -> throw new BridgeException(400, "Unsupported terminal input kind '" + kind + "'.");
        }

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("kind", kind.toLowerCase());
        return map;
    }

    private Map<String, Object> runPython(ServerComputer computer, BridgeComputerAccess access, String rawProgram, String rawCwd, boolean interactive) throws Exception {
        String cwd = FileSystemAdapter.normalizeWorkingDir(rawCwd == null || rawCwd.isBlank() ? "/" : rawCwd);
        String program = interactive ? null : FileSystemAdapter.normalizeProgramPath(rawProgram, cwd);
        if (!interactive && (program == null || program.isBlank())) {
            throw new BridgeException(400, "Python run requires a program path unless interactive=true.");
        }

        var process = PythonRuntimeManager.getInstance().launch(
            access.computerSystem(),
            new PythonLaunchSpec(program, cwd, List.of(), interactive)
        );

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("process", describeProcess(process));
        return map;
    }

    private Map<String, Object> stopPython(ServerComputer computer, String processId) {
        int stopped;
        if (processId != null && !processId.isBlank()) {
            boolean found = PythonRuntimeManager.getInstance().stopProcess(
                computer.getID(),
                processId,
                "Stopped from VS Code bridge."
            );
            if (!found) {
                throw new BridgeException(404, "Python process '" + processId + "' is not active on computer " + computer.getID() + ".");
            }
            stopped = 1;
        } else {
            stopped = PythonRuntimeManager.getInstance().stopAllProcesses(computer.getID(), "Stopped from VS Code bridge.");
        }

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("stopped", stopped);
        return map;
    }

    private Map<String, Object> search(ServerComputer computer, BridgeComputerAccess access, String rawPath, String rawQuery, int limit) throws Exception {
        String path = normalizePath(rawPath);
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isBlank()) throw new BridgeException(400, "Search query cannot be empty.");

        var results = new ArrayList<Map<String, Object>>();
        searchRecursive(access.fileSystem(), path, query.toLowerCase(), limit, results);

        var map = ok();
        map.put("computer", describeComputer(computer));
        map.put("path", path);
        map.put("query", rawQuery);
        map.put("results", results);
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

    private Map<String, Object> describeProcess(PythonProcess process) {
        return describeSnapshot(process.snapshot(), process.traceback());
    }

    private Map<String, Object> describeSnapshot(dev.gfortes.ccpython.runtime.PythonStatusSnapshot snapshot, String traceback) {
        var map = new LinkedHashMap<String, Object>();
        map.put("computer_id", snapshot.computerId());
        map.put("process_id", snapshot.processId());
        map.put("state", snapshot.state().name().toLowerCase());
        map.put("program", snapshot.program());
        map.put("interactive", snapshot.interactive());
        map.put("started_at", snapshot.startedAt());
        map.put("detail", snapshot.detail());
        map.put("traceback", traceback == null || traceback.isBlank() ? null : traceback);
        return map;
    }

    private String aggregateRuntimeStatus(List<PythonProcess> processes) {
        if (processes.isEmpty()) return "idle";
        if (processes.stream().anyMatch(process -> process.state() == PythonProcessState.RUNNING)) return "running";
        if (processes.stream().anyMatch(process -> process.state() == PythonProcessState.STARTING)) return "starting";
        if (processes.stream().anyMatch(process -> process.state() == PythonProcessState.WAITING_EVENT)) return "waiting_event";
        if (processes.stream().anyMatch(process -> process.state() == PythonProcessState.WAITING_HOST)) return "waiting_host";
        if (processes.stream().anyMatch(process -> process.state() == PythonProcessState.FAILED)) return "failed";
        if (processes.stream().anyMatch(process -> process.state() == PythonProcessState.KILLED)) return "killed";
        return "active";
    }

    private void searchRecursive(FileSystem fileSystem, String path, String queryLower, int limit, List<Map<String, Object>> results) throws Exception {
        if (results.size() >= limit) return;
        if (!exists(fileSystem, path)) throw new BridgeException(404, "Path '" + path + "' does not exist.");

        if (!isDir(fileSystem, path)) {
            maybeSearchFile(fileSystem, path, queryLower, limit, results);
            return;
        }

        var children = new ArrayList<>(fileSystem.list(path));
        children.sort(String::compareToIgnoreCase);
        for (String child : children) {
            if (results.size() >= limit) return;
            searchRecursive(fileSystem, FileSystemAdapter.combine(path, child), queryLower, limit, results);
        }
    }

    private void maybeSearchFile(FileSystem fileSystem, String path, String queryLower, int limit, List<Map<String, Object>> results) throws Exception {
        if (results.size() >= limit) return;

        String fileName = path.substring(path.lastIndexOf('/') + 1);
        if (fileName.toLowerCase().contains(queryLower)) {
            var match = new LinkedHashMap<String, Object>();
            match.put("path", path);
            match.put("kind", "path");
            match.put("line", null);
            match.put("snippet", path);
            results.add(match);
            if (results.size() >= limit) return;
        }

        long size = fileSystem.getAttributes(path).size();
        if (size > 512L * 1024L) return;

        byte[] bytes = readAllBytes(fileSystem, path);
        if (!isProbablyText(bytes)) return;

        String text = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length && results.size() < limit; index++) {
            String line = lines[index];
            if (!line.toLowerCase().contains(queryLower)) continue;

            var match = new LinkedHashMap<String, Object>();
            match.put("path", path);
            match.put("kind", "content");
            match.put("line", index + 1);
            match.put("snippet", line.strip());
            results.add(match);
        }
    }

    private boolean isProbablyText(byte[] bytes) {
        int suspicious = 0;
        for (byte raw : bytes) {
            int value = raw & 0xFF;
            if (value == 0) return false;
            if (value < 0x09 || (value > 0x0D && value < 0x20)) suspicious++;
            if (suspicious > 8) return false;
        }
        return true;
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

    private String decodeTerminalInput(String queryText, byte[] body) {
        if (body != null && body.length > 0) {
            return new String(body, StandardCharsets.UTF_8);
        }
        return queryText == null ? "" : queryText;
    }

    private AuthIdentity authenticate(HttpExchange exchange, String method, String route) {
        if (isLoopback(exchange)) return AuthIdentity.admin("localhost", true);
        if (!CCPythonConfig.devBridgeAllowRemote()) return null;

        if (isPairCompletionRoute(method, route)) {
            return CCPythonConfig.devBridgePairingEnabled() ? AuthIdentity.pairing() : null;
        }

        String header = bearerToken(exchange);
        if (header.isBlank()) return null;

        String staticToken = CCPythonConfig.devBridgeAuthToken();
        if (!staticToken.isBlank() && staticToken.equals(header)) {
            return AuthIdentity.admin("static-token", false);
        }

        DevBridgeAuthStore.IssuedToken issued = authStore.validate(header);
        if (issued == null) return null;
        if (issued.kind() == DevBridgeAuthStore.TokenKind.ADMIN || issued.playerUuid() == null) {
            return AuthIdentity.admin("token:" + issued.tokenId(), false);
        }
        return AuthIdentity.player(issued.playerUuid(), issued.playerName(), "token:" + issued.tokenId());
    }

    private boolean isLoopback(HttpExchange exchange) {
        InetAddress address = exchange.getRemoteAddress().getAddress();
        return address != null && (address.isLoopbackAddress() || address.isAnyLocalAddress());
    }

    private boolean requiresRemoteAuth() {
        return CCPythonConfig.devBridgeAllowRemote();
    }

    private String bearerToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || header.isBlank()) return "";
        if (header.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return header.substring("Bearer ".length()).trim();
        }
        return "";
    }

    private boolean isPairCompletionRoute(String method, String route) {
        return "POST".equalsIgnoreCase(method) && "/auth/pair/complete".equals(route);
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

    private int parseLimit(String raw) {
        if (raw == null || raw.isBlank()) return 100;
        try {
            return Math.clamp(Integer.parseInt(raw), 1, 500);
        } catch (NumberFormatException exception) {
            throw new BridgeException(400, "Invalid limit '" + raw + "'.");
        }
    }

    private String requireQuery(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null || value.isBlank()) throw new BridgeException(400, "Missing required query parameter '" + key + "'.");
        return value;
    }

    private <T> T withComputer(MinecraftServer server, int computerId, AuthIdentity identity, ComputerOperation<T> operation) throws Exception {
        return callOnServerThread(server, () -> operation.run(requireComputer(server, computerId, identity)));
    }

    private <T> T withComputerAccess(MinecraftServer server, int computerId, AuthIdentity identity, ComputerAccessOperation<T> operation) throws Exception {
        return callOnServerThread(server, () -> {
            ServerComputer computer = requireComputer(server, computerId, identity);
            return operation.run(computer, BridgeComputerAccess.resolve(computer));
        });
    }

    private ServerComputer requireLoadedComputer(MinecraftServer server, int computerId) {
        for (ServerComputer computer : ServerContext.get(server).registry().getComputers()) {
            if (computer.getID() == computerId) return computer;
        }
        throw new BridgeException(404, "Computer " + computerId + " is not currently loaded.");
    }

    private ServerComputer requireComputer(MinecraftServer server, int computerId, AuthIdentity identity) {
        ServerComputer computer = requireLoadedComputer(server, computerId);
        if (canAccessComputer(computer.getID(), identity)) {
            return computer;
        }
        throw new BridgeException(403, "Access denied to computer " + computerId + ".");
    }

    private boolean canAccessComputer(int computerId, AuthIdentity identity) {
        if (identity.admin()) return true;
        return identity.isPlayer() && accessStore.canAccess(computerId, identity.playerUuid());
    }

    private boolean canManageAcl(DevBridgeAccessStore.ComputerAcl acl, AuthIdentity identity) {
        if (identity.admin()) return true;
        return identity.isPlayer()
            && acl != null
            && acl.ownerUuid() != null
            && acl.ownerUuid().equals(identity.playerUuid());
    }

    private DevBridgeAccessStore.ComputerAcl requireManagedAcl(ServerComputer computer, AuthIdentity identity) {
        DevBridgeAccessStore.ComputerAcl acl = accessStore.get(computer.getID());
        if (acl == null || acl.ownerUuid() == null) {
            throw new BridgeException(409, "Computer " + computer.getID() + " has no owner yet. Claim it first.");
        }
        if (!canManageAcl(acl, identity)) {
            throw new BridgeException(403, "Only the owner or a bridge admin can change access for computer " + computer.getID() + ".");
        }
        return acl;
    }

    private PlayerIdentity targetPlayerForClaim(MinecraftServer server, ServerComputer computer, AuthIdentity identity, String playerRef) {
        DevBridgeAccessStore.ComputerAcl acl = accessStore.get(computer.getID());
        if (identity.admin()) {
            if (playerRef == null || playerRef.isBlank()) {
                throw new BridgeException(400, "Admin claim requires ?player=<name>.");
            }
            return resolvePlayerIdentity(server, playerRef);
        }

        if (!identity.isPlayer()) {
            throw new BridgeException(403, "Only authenticated players can claim ownership.");
        }

        if (acl != null && acl.ownerUuid() != null && !acl.ownerUuid().equals(identity.playerUuid())) {
            throw new BridgeException(403, "Computer " + computer.getID() + " is already owned by " + acl.ownerName() + ".");
        }

        return new PlayerIdentity(identity.playerUuid(), identity.playerName() == null || identity.playerName().isBlank()
            ? identity.playerUuid().toString()
            : identity.playerName());
    }

    private PlayerIdentity resolvePlayerIdentity(MinecraftServer server, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BridgeException(400, "Player reference cannot be empty.");
        }

        String candidate = raw.trim();
        try {
            UUID uuid = UUID.fromString(candidate);
            ServerPlayer byUuid = server.getPlayerList().getPlayer(uuid);
            if (byUuid != null) {
                return new PlayerIdentity(byUuid.getUUID(), byUuid.getGameProfile().getName());
            }
        } catch (IllegalArgumentException ignored) {
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(candidate)) {
                return new PlayerIdentity(player.getUUID(), player.getGameProfile().getName());
            }
        }

        throw new BridgeException(404, "Online player '" + candidate + "' was not found.");
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

    private record AuthIdentity(boolean authenticated, boolean admin, boolean loopback, UUID playerUuid, String playerName, String source) {
        static AuthIdentity admin(String source, boolean loopback) {
            return new AuthIdentity(true, true, loopback, null, null, source);
        }

        static AuthIdentity player(UUID playerUuid, String playerName, String source) {
            return new AuthIdentity(true, false, false, playerUuid, playerName, source);
        }

        static AuthIdentity pairing() {
            return new AuthIdentity(true, false, false, null, null, "pairing");
        }

        boolean isPlayer() {
            return playerUuid != null;
        }

        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("authenticated", authenticated);
            map.put("admin", admin);
            map.put("loopback", loopback);
            map.put("player_uuid", playerUuid == null ? null : playerUuid.toString());
            map.put("player_name", playerName);
            map.put("source", source);
            return map;
        }
    }

    private record PlayerIdentity(UUID uuid, String name) {
        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("uuid", uuid.toString());
            map.put("name", name);
            return map;
        }
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
