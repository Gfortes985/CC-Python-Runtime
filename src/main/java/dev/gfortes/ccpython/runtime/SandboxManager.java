package dev.gfortes.ccpython.runtime;

import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.config.CCPythonConfig;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.io.IOAccess;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SandboxManager {
    private static final SandboxManager INSTANCE = new SandboxManager();
    private static final Engine SHARED_ENGINE = Engine.newBuilder()
        .option("engine.WarnInterpreterOnly", "false")
        .build();
    private static final HostAccess HOST_ACCESS = HostAccess.newBuilder(HostAccess.NONE)
        .allowAccessAnnotatedBy(HostAccess.Export.class)
        .allowArrayAccess(true)
        .allowListAccess(true)
        .allowMapAccess(true)
        .allowIterableAccess(true)
        .allowIteratorAccess(true)
        .build();

    private final AtomicBoolean warmupStarted = new AtomicBoolean(false);
    private SandboxManager() {
    }

    public static SandboxManager getInstance() {
        return INSTANCE;
    }

    public Context createContext(PythonComputerRuntime runtime) {
        return newBuilder(runtime).build();
    }

    public void warmUpAsync() {
        if (!warmupStarted.compareAndSet(false, true)) return;

        var thread = new Thread(() -> {
            try (var context = baseBuilder().build()) {
                PythonAPIBindings.getInstance().warmUp(context);
                CCPythonMod.LOGGER.debug("Completed GraalPy warm-up.");
            } catch (Throwable throwable) {
                CCPythonMod.LOGGER.warn("GraalPy warm-up failed.", throwable);
            }
        }, "ccpython-warmup");
        thread.setDaemon(true);
        thread.start();
    }

    private Context.Builder newBuilder(PythonComputerRuntime runtime) {
        var server = runtime.owner().computer().getLevel().getServer();
        var resourceLimits = ResourceLimits.newBuilder()
            .statementLimit(CCPythonConfig.maxStatementsPerProcess(server), source -> true)
            .onLimit(event -> runtime.requestLimitKill("Python process exceeded the configured statement budget."))
            .build();

        return baseBuilder().resourceLimits(resourceLimits);
    }

    private Context.Builder baseBuilder() {
        return Context.newBuilder("python")
            .engine(SHARED_ENGINE)
            .allowHostAccess(HOST_ACCESS)
            .allowHostClassLookup(className -> false)
            .allowHostClassLoading(false)
            .allowCreateThread(false)
            .allowCreateProcess(false)
            .allowNativeAccess(false)
            .allowAllAccess(false)
            .allowPolyglotAccess(PolyglotAccess.NONE)
            .allowEnvironmentAccess(EnvironmentAccess.NONE)
            .allowIO(IOAccess.NONE)
            .useSystemExit(false)
            .option("python.DontWriteBytecodeFlag", "true")
            .option("python.PosixModuleBackend", "java")
            .option("python.WarnOptions", "");
    }
}
