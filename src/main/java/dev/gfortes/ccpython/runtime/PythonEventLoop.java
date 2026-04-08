package dev.gfortes.ccpython.runtime;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class PythonEventLoop {
    private final PythonProcess process;

    public PythonEventLoop(PythonProcess process) {
        this.process = process;
    }

    public PythonActionResponse hostCall(String module, String method, List<Object> arguments) {
        return exchange(PythonAction.hostCall(module, method, arguments));
    }

    private PythonActionResponse exchange(PythonAction action) {
        process.ensureAlive();
        var future = new CompletableFuture<PythonActionResponse>();
        process.beginHostWait(action, future);
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PythonActionResponse.failure("Python process interrupted while waiting for a CC host response.");
        } catch (ExecutionException exception) {
            return PythonActionResponse.failure(exception.getCause() == null ? exception.getMessage() : exception.getCause().getMessage());
        }
    }
}
