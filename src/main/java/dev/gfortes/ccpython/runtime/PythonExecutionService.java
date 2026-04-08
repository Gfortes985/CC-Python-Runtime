package dev.gfortes.ccpython.runtime;

import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.config.CCPythonConfig;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class PythonExecutionService {
    private static final PythonExecutionService INSTANCE = new PythonExecutionService();

    private final Object lock = new Object();
    private final Set<PythonProcess> activeProcesses = ConcurrentHashMap.newKeySet();

    private ThreadPoolExecutor executor;
    private ScheduledExecutorService watchdog;

    private PythonExecutionService() {
    }

    public static PythonExecutionService getInstance() {
        return INSTANCE;
    }

    public Future<?> submit(PythonProcess process) {
        CCPythonMod.LOGGER.debug("Submitting Python process {} to execution service.", process.id());
        ensureStarted(process);
        activeProcesses.add(process);
        CCPythonMod.LOGGER.debug(
            "Python execution service accepted process {} (poolSize={}, active={}, queued={}).",
            process.id(),
            executor.getPoolSize(),
            executor.getActiveCount(),
            executor.getQueue().size()
        );
        return executor.submit(() -> {
            CCPythonMod.LOGGER.debug(
                "Python process {} started on runtime thread {}.",
                process.id(),
                Thread.currentThread().getName()
            );
            try {
                process.run();
            } catch (Throwable throwable) {
                CCPythonMod.LOGGER.error("Python process {} crashed before completion.", process.id(), throwable);
                throw throwable;
            } finally {
                CCPythonMod.LOGGER.debug("Python process {} left runtime thread {}.", process.id(), Thread.currentThread().getName());
                activeProcesses.remove(process);
            }
        });
    }

    public void shutdown() {
        synchronized (lock) {
            activeProcesses.forEach(process -> process.markKilled("Minecraft server stopped."));
            if (watchdog != null) watchdog.shutdownNow();
            if (executor != null) executor.shutdownNow();
            watchdog = null;
            executor = null;
            activeProcesses.clear();
        }
    }

    private void ensureStarted(PythonProcess process) {
        synchronized (lock) {
            if (executor != null && watchdog != null) return;
            var server = process.server();

            var threadFactory = new ThreadFactory() {
                private int index = 0;

                @Override
                public Thread newThread(Runnable runnable) {
                    var thread = new Thread(runnable, "ccpython-runtime-" + index++);
                    thread.setDaemon(false);
                    CCPythonMod.LOGGER.debug("Created Python runtime thread {}.", thread.getName());
                    return thread;
                }
            };

            executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                CCPythonConfig.maxParallelRuntimes(server),
                threadFactory
            );
            executor.prestartAllCoreThreads();
            watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
                var thread = new Thread(runnable, "ccpython-watchdog");
                thread.setDaemon(false);
                CCPythonMod.LOGGER.debug("Created Python watchdog thread {}.", thread.getName());
                return thread;
            });
            CCPythonMod.LOGGER.debug(
                "Python execution service started (maxParallelRuntimes={}, prestartedThreads={}).",
                CCPythonConfig.maxParallelRuntimes(server),
                executor.getPoolSize()
            );
            watchdog.scheduleAtFixedRate(this::runWatchdog, 1L, 1L, TimeUnit.SECONDS);
        }
    }

    private void runWatchdog() {
        for (var process : activeProcesses) process.checkWatchdog();
    }
}
