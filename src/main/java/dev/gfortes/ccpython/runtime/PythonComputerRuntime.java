package dev.gfortes.ccpython.runtime;

import dev.gfortes.ccpython.CCPythonMod;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public final class PythonComputerRuntime implements AutoCloseable {
    private final PythonComputerContext owner;
    private final Object lock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean recreateContext = new AtomicBoolean(false);

    private volatile Context context;
    private volatile PythonProcess activeProcess;

    public PythonComputerRuntime(PythonComputerContext owner) {
        this.owner = owner;
    }

    public PythonComputerContext owner() {
        return owner;
    }

    public Value execute(PythonProcess process, PythonLaunchSpec spec) {
        process.ensureAlive();

        synchronized (lock) {
            ensureOpen();
            closeIfRequested();

            var current = ensureContext();
            activeProcess = process;

            try {
                process.ensureAlive();
                current.resetLimits();
                CCPythonMod.LOGGER.debug(
                    "Executing Python process {} on persistent runtime for computer {}.",
                    process.id(),
                    owner.computer().getID()
                );

                var runner = current.getBindings("python").getMember("__ccpython_run");
                if (runner == null || !runner.canExecute()) {
                    throw new IllegalStateException("Python bootstrap did not expose __ccpython_run.");
                }

                return runner.execute(
                    spec.program() == null ? "" : spec.program(),
                    spec.cwd(),
                    spec.interactive(),
                    spec.args().toArray(Object[]::new),
                    FileSystemAdapter.buildSearchPath(spec).toArray(String[]::new)
                );
            } finally {
                activeProcess = null;
                closeIfRequested();
            }
        }
    }

    public PythonActionResponse hostCall(String module, String method, List<Object> arguments) {
        var process = activeProcess;
        if (process == null) return PythonActionResponse.failure("No active Python process is bound to this computer runtime.");

        process.recordPayload(arguments);
        var safeArguments = arguments == null ? List.of() : arguments;
        var resetBudget = resetsStatementBudget(module, method);
        var currentContext = context;
        if (resetBudget && currentContext != null) currentContext.resetLimits();

        var nativeResponse = NativeHostDispatcher.dispatch(this, process, module, method, safeArguments);
        if (nativeResponse != null) {
            if (nativeResponse.ok()) process.recordPayload(nativeResponse.values());
            if (resetBudget && currentContext != null) currentContext.resetLimits();
            return nativeResponse;
        }

        var response = process.eventLoop().hostCall(module, method, safeArguments);
        if (response.ok()) process.recordPayload(response.values());
        if (resetBudget && currentContext != null) currentContext.resetLimits();
        return response;
    }

    private boolean resetsStatementBudget(String module, String method) {
        if (module == null || method == null) return false;

        return switch (module) {
            case "__global" -> method.equals("sleep") || method.equals("read");
            case "os" -> method.equals("pull_event") || method.equals("pull_event_raw");
            case "__midi" -> method.equals("play_audio_song") || method.equals("play_soundfont_song") || method.equals("play_hifi_soundfont_song");
            default -> false;
        };
    }

    public void requestLimitKill(String reason) {
        var process = activeProcess;
        if (process != null) {
            process.requestKill(reason);
        } else {
            discardContext();
        }
    }

    public void interrupt(PythonProcess process) {
        if (activeProcess != process) return;

        recreateContext.set(true);
        var current = context;
        if (current == null) return;

        try {
            current.interrupt(Duration.ofMillis(10L));
        } catch (TimeoutException exception) {
            CCPythonMod.LOGGER.debug("Timed out while interrupting Python runtime for computer {}.", owner.computer().getID(), exception);
            try {
                current.close(true);
            } catch (RuntimeException ignored) {
            }
        } catch (RuntimeException exception) {
            CCPythonMod.LOGGER.debug("Failed to interrupt Python runtime for computer {}.", owner.computer().getID(), exception);
        }
    }

    public void discardContext() {
        recreateContext.set(true);
        if (activeProcess != null) return;

        synchronized (lock) {
            closeIfRequested();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;

        recreateContext.set(true);
        synchronized (lock) {
            closeIfRequested();
        }
    }

    private Context ensureContext() {
        var existing = context;
        if (existing != null) return existing;

        CCPythonMod.LOGGER.debug("Creating persistent GraalPy context for computer {}.", owner.computer().getID());
        var created = SandboxManager.getInstance().createContext(this);
        try {
            PythonAPIBindings.getInstance().install(created, this);
            context = created;
            return created;
        } catch (Throwable throwable) {
            try {
                created.close(true);
            } catch (RuntimeException ignored) {
            }
            throw throwable;
        }
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Python runtime is already closed for this computer.");
    }

    private void closeIfRequested() {
        if (!recreateContext.getAndSet(false)) return;
        closeContext();
    }

    private void closeContext() {
        var current = context;
        context = null;
        if (current == null) return;

        CCPythonMod.LOGGER.debug("Closing persistent GraalPy context for computer {}.", owner.computer().getID());
        try {
            current.close(true);
        } catch (RuntimeException ignored) {
        }
    }
}
