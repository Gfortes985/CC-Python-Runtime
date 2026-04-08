package dev.gfortes.ccpython.client;

import dev.gfortes.ccpython.network.payload.PythonRuntimeErrorPayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeClearPayload;
import dev.gfortes.ccpython.network.payload.PythonRuntimeStatePayload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPythonRuntimeState {
    private static final Map<String, PythonRuntimeStatePayload> STATES = new ConcurrentHashMap<>();
    private static final Map<String, PythonRuntimeErrorPayload> ERRORS = new ConcurrentHashMap<>();

    private ClientPythonRuntimeState() {
    }

    public static void apply(PythonRuntimeStatePayload payload) {
        STATES.put(key(payload.computerId(), payload.processId()), payload);
    }

    public static void apply(PythonRuntimeErrorPayload payload) {
        ERRORS.put(key(payload.computerId(), payload.processId()), payload);
    }

    public static void apply(PythonRuntimeClearPayload payload) {
        String key = key(payload.computerId(), payload.processId());
        STATES.remove(key);
        ERRORS.remove(key);
    }

    public static void clearAll() {
        STATES.clear();
        ERRORS.clear();
    }

    public static PythonRuntimeStatePayload getState(int computerId, String processId) {
        return STATES.get(key(computerId, processId));
    }

    public static PythonRuntimeErrorPayload getError(int computerId, String processId) {
        return ERRORS.get(key(computerId, processId));
    }

    private static String key(int computerId, String processId) {
        return computerId + ":" + processId;
    }
}
