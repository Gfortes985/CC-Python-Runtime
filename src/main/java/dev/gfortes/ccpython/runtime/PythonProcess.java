package dev.gfortes.ccpython.runtime;

import dan200.computercraft.api.lua.LuaException;
import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.config.CCPythonConfig;
import dev.gfortes.ccpython.network.NetworkSyncManager;
import dev.gfortes.ccpython.util.LuaValues;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;

public final class PythonProcess {
    public static final String WAKE_EVENT = "ccpython_wakeup";

    private final PythonComputerContext owner;
    private final PythonLaunchSpec spec;
    private final String id;
    private final long startedAt;
    private final AtomicReference<PythonProcessState> state = new AtomicReference<>(PythonProcessState.STARTING);
    private final AtomicReference<PythonAction> deliverableAction = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<PythonActionResponse>> pendingResponse = new AtomicReference<>();
    private final AtomicLong lastProgressMillis = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong accountedBytes = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final NativeProcessResources nativeResources = new NativeProcessResources();
    private final BlockingQueue<List<Object>> pendingEvents = new LinkedBlockingQueue<>();

    private final PythonEventLoop eventLoop = new PythonEventLoop(this);

    private volatile Future<?> future;
    private volatile String detail = "";
    private volatile String traceback = "";
    private volatile Thread runtimeThread;

    public PythonProcess(PythonComputerContext owner, PythonLaunchSpec spec) {
        this.owner = owner;
        this.spec = spec;
        this.id = Integer.toHexString(System.identityHashCode(this)) + "-" + Long.toUnsignedString(System.nanoTime(), 36);
        this.startedAt = System.currentTimeMillis();
    }

    public void start() {
        CCPythonMod.LOGGER.debug("Python process {} start() invoked on thread {}.", id, Thread.currentThread().getName());
        future = PythonExecutionService.getInstance().submit(this);
        CCPythonMod.LOGGER.debug("Python process {} obtained future {}.", id, future);
    }

    public void run() {
        CCPythonMod.LOGGER.debug("Python process {} entered run() on thread {}.", id, Thread.currentThread().getName());
        if (closed.get()) {
            owner.detach(this);
            return;
        }

        runtimeThread = Thread.currentThread();
        try {
            pushState(PythonProcessState.STARTING, "Preparing persistent GraalPy runtime.");
        } catch (Throwable throwable) {
            fail(throwable);
            return;
        }

        try {
            pushState(PythonProcessState.RUNNING, "Executing Python program.");
            Value value = owner.runtime().execute(this, spec);
            CCPythonMod.LOGGER.debug("Python process {} completed guest execution.", id);
            complete(convertResult(value));
        } catch (Throwable throwable) {
            fail(throwable);
        } finally {
            runtimeThread = null;
            nativeResources.closeAll(owner.computer());
            owner.detach(this);
        }
    }

    public String id() {
        return id;
    }

    public PythonEventLoop eventLoop() {
        return eventLoop;
    }

    public PythonProcessState state() {
        return state.get();
    }

    public String detail() {
        return detail;
    }

    public String traceback() {
        return traceback;
    }

    public PythonLaunchSpec spec() {
        return spec;
    }

    public MinecraftServer server() {
        return owner.computer().getLevel().getServer();
    }

    NativeProcessResources nativeResources() {
        return nativeResources;
    }

    public void queueComputerEvent(String eventName, Object[] arguments) {
        if (closed.get() || eventName == null || eventName.isBlank()) return;

        var event = new ArrayList<Object>(1 + (arguments == null ? 0 : arguments.length));
        event.add(eventName);
        if (arguments != null) {
            for (var argument : arguments) event.add(LuaValues.normalize(argument));
        }
        pendingEvents.offer(event);
    }

    public List<Object> awaitComputerEvent(String filterName, boolean raw) throws LuaException {
        return awaitComputerEvent(
            filterName,
            raw,
            null,
            filterName == null ? "Waiting for CC event." : "Waiting for CC event: " + filterName
        );
    }

    public List<Object> awaitComputerEvent(
        String filterName,
        boolean raw,
        Predicate<List<Object>> matcher,
        String waitingDetail
    ) throws LuaException {
        pushState(PythonProcessState.WAITING_EVENT, waitingDetail, false);
        try {
            while (true) {
                ensureAlive();
                var event = takeNextEvent();
                var eventName = eventName(event);
                if (eventName == null || WAKE_EVENT.equals(eventName)) continue;

                if (!raw && "terminate".equals(eventName)) {
                    markKilled("Terminated by Ctrl+T.");
                    throw new LuaException("Terminated");
                }

                if (filterName != null && !filterName.equals(eventName)) continue;
                if (matcher != null && !matcher.test(event)) continue;
                return event;
            }
        } finally {
            if (state.get() == PythonProcessState.WAITING_EVENT) {
                pushState(PythonProcessState.RUNNING, "Resuming Python execution.", false);
            }
        }
    }

    public void beginHostWait(PythonAction action, CompletableFuture<PythonActionResponse> future) {
        pendingResponse.set(future);
        deliverableAction.set(action);
        CCPythonMod.LOGGER.debug(
            "Python process {} requested host call {}.{}",
            id,
            action.module(),
            action.method()
        );
        pushState(PythonProcessState.WAITING_HOST, "Waiting for Lua host call: " + action.module() + "." + action.method(), false);
        wake();
    }

    public void finishHostWait(PythonActionResponse response) {
        deliverableAction.set(null);
        var future = pendingResponse.getAndSet(null);
        if (future == null) return;
        CCPythonMod.LOGGER.debug(
            "Python process {} received host response (ok={})",
            id,
            response.ok()
        );
        pushState(PythonProcessState.RUNNING, "Resuming Python execution.", false);
        future.complete(response);
    }

    public void complete(List<Object> results) {
        deliverableAction.set(PythonAction.done(results));
        pushState(PythonProcessState.COMPLETED, "Python process completed.");
        wake();
    }

    public void fail(Throwable throwable) {
        if (closed.get()) return;

        if (shouldDiscardRuntime(throwable)) owner.runtime().discardContext();
        traceback = formatThrowable(throwable);
        deliverableAction.set(PythonAction.error(messageFromThrowable(throwable), traceback));
        CCPythonMod.LOGGER.error("Python process {} failed: {}", id, messageFromThrowable(throwable), throwable);
        pushState(PythonProcessState.FAILED, messageFromThrowable(throwable));
        NetworkSyncManager.broadcastError(owner.computer().getLevel(), owner.computer().getID(), id, traceback);
        wake();
    }

    public void markKilled(String reason) {
        if (!closed.compareAndSet(false, true)) return;

        traceback = reason;
        deliverableAction.set(PythonAction.error(reason, reason));
        pushState(PythonProcessState.KILLED, reason);
        wake();

        var pending = pendingResponse.getAndSet(null);
        if (pending != null) pending.complete(PythonActionResponse.failure(reason));

        var runner = runtimeThread;
        if (runner == null || runner != Thread.currentThread()) {
            owner.runtime().interrupt(this);
            if (future != null) future.cancel(true);
        }
    }

    public void requestKill(String reason) {
        markKilled(reason);
    }

    public void ensureAlive() {
        if (state.get() == PythonProcessState.KILLED) {
            throw new IllegalStateException(detail.isBlank() ? "Python process was killed." : detail);
        }
    }

    public PythonAction peekDeliverableAction() {
        return deliverableAction.get();
    }

    public PythonStatusSnapshot snapshot() {
        return new PythonStatusSnapshot(
            owner.computer().getID(),
            id,
            state.get(),
            spec.program() == null ? "<repl>" : spec.program(),
            spec.interactive(),
            startedAt,
            detail
        );
    }

    public void recordPayload(Object payload) {
        long size = LuaValues.approximateSize(payload);
        var server = owner.computer().getLevel().getServer();
        if (accountedBytes.addAndGet(size) > CCPythonConfig.softMemoryBudgetBytes(server)) {
            markKilled("Soft Python memory budget exceeded.");
        }
        if (size > CCPythonConfig.maxBridgePayloadBytes(server)) {
            markKilled("Lua/Python bridge payload exceeded the configured limit.");
        }
    }

    public void checkWatchdog() {
        var currentState = state.get();
        if (currentState != PythonProcessState.RUNNING && currentState != PythonProcessState.STARTING) return;

        if (System.currentTimeMillis() - lastProgressMillis.get() > CCPythonConfig.watchdogTimeoutMillis(owner.computer().getLevel().getServer())) {
            markKilled("Python watchdog timeout exceeded.");
        }
    }

    private void pushState(PythonProcessState newState, String detail) {
        pushState(newState, detail, true);
    }

    private void pushState(PythonProcessState newState, String detail, boolean broadcast) {
        CCPythonMod.LOGGER.debug(
            "Python process {} pushing state {} with detail '{}'.",
            id,
            newState,
            detail
        );
        state.set(newState);
        this.detail = detail == null ? "" : detail;
        lastProgressMillis.set(System.currentTimeMillis());
        if (broadcast) NetworkSyncManager.broadcastState(owner.computer().getLevel(), snapshot());
    }

    private void wake() {
        var computer = owner.computer();
        var server = computer.getLevel().getServer();
        if (server == null) return;

        server.execute(() -> {
            try {
                computer.queueEvent(WAKE_EVENT, id);
            } catch (RuntimeException exception) {
                CCPythonMod.LOGGER.warn("Failed to wake Python process {}.", id, exception);
            }
        });
    }

    private List<Object> takeNextEvent() throws LuaException {
        try {
            return pendingEvents.take();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (state.get() == PythonProcessState.KILLED) {
                throw new LuaException(detail.isBlank() ? "Python process was killed." : detail);
            }
            throw new LuaException("Interrupted while waiting for a CC event.");
        }
    }

    private static String eventName(List<Object> event) {
        if (event.isEmpty() || event.getFirst() == null) return null;
        return event.getFirst().toString();
    }

    private static List<Object> convertResult(Value value) {
        if (value == null || value.isNull()) return List.of();
        if (value.hasArrayElements()) return LuaValues.toList(value);
        return List.of(LuaValues.toJava(value));
    }

    private static String messageFromThrowable(Throwable throwable) {
        if (throwable instanceof PolyglotException polyglotException && polyglotException.isCancelled()) {
            return "Python process cancelled.";
        }
        if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) return throwable.getMessage();
        return Objects.toString(throwable.getClass().getSimpleName(), "Python execution failed.");
    }

    private static String formatThrowable(Throwable throwable) {
        if (throwable instanceof PolyglotException polyglotException && polyglotException.isGuestException()) {
            return formatGuestTraceback(polyglotException);
        }

        var writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static boolean shouldDiscardRuntime(Throwable throwable) {
        if (throwable instanceof PolyglotException polyglotException) {
            return !polyglotException.isGuestException();
        }
        return true;
    }

    private static String formatGuestTraceback(PolyglotException exception) {
        var frames = new ArrayList<PolyglotException.StackFrame>();
        for (var frame : exception.getPolyglotStackTrace()) {
            if (!frame.isGuestFrame()) continue;
            if (isInternalBootstrapFrame(frame)) continue;
            frames.add(frame);
        }

        var builder = new StringBuilder();
        if (!frames.isEmpty()) {
            builder.append("Traceback (most recent call last):\n");
            for (int i = frames.size() - 1; i >= 0; i--) {
                appendGuestFrame(builder, frames.get(i));
            }
        }

        var message = exception.getMessage();
        if (message != null && !message.isBlank()) {
            builder.append(message);
            if (!message.endsWith("\n")) builder.append('\n');
        } else {
            builder.append("Python execution failed.\n");
        }

        return builder.toString();
    }

    private static boolean isInternalBootstrapFrame(PolyglotException.StackFrame frame) {
        var location = frame.getSourceLocation();
        if (location == null || !location.isAvailable()) return false;

        var source = location.getSource();
        String sourceName = source == null ? null : (source.getPath() != null ? source.getPath() : source.getName());
        if (sourceName == null || !sourceName.endsWith("ccpython_bootstrap.py")) return false;

        return switch (frame.getRootName()) {
            case "_unwrap", "_call", "_invoke", "_run_script", "_run_repl", "__ccpython_run", "exec_module" -> true;
            default -> false;
        };
    }

    private static void appendGuestFrame(StringBuilder builder, PolyglotException.StackFrame frame) {
        builder.append("  File \"");
        var location = frame.getSourceLocation();
        if (location != null && location.isAvailable()) {
            appendSourceLocation(builder, location);
        } else {
            builder.append("<unknown>");
        }
        builder.append('"');

        if (location != null && location.isAvailable() && location.getStartLine() > 0) {
            builder.append(", line ").append(location.getStartLine());
        }

        String rootName = frame.getRootName();
        if (rootName != null && !rootName.isBlank()) {
            builder.append(", in ").append(rootName);
        }
        builder.append('\n');

        if (location != null && location.isAvailable()) {
            var code = location.getCharacters();
            if (code != null) {
                String snippet = code.toString().strip();
                if (!snippet.isEmpty()) builder.append("    ").append(snippet).append('\n');
            }
        }
    }

    private static void appendSourceLocation(StringBuilder builder, SourceSection location) {
        var source = location.getSource();
        if (source == null) {
            builder.append("<unknown>");
            return;
        }

        if (source.getPath() != null && !source.getPath().isBlank()) {
            builder.append(source.getPath());
            return;
        }

        if (source.getName() != null && !source.getName().isBlank()) {
            builder.append(source.getName());
            return;
        }

        builder.append("<unknown>");
    }
}
