package dev.gfortes.ccpython.runtime;

import dan200.computercraft.api.lua.IDynamicLuaObject;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dev.gfortes.ccpython.util.LuaValues;
import java.util.LinkedHashMap;

public final class PythonProcessHandle implements IDynamicLuaObject {
    private static final String[] METHODS = { "await", "respond", "close", "status", "id" };

    private final PythonProcess process;
    private final CoroutineAdapter coroutineAdapter;

    public PythonProcessHandle(PythonProcess process) {
        this.process = process;
        this.coroutineAdapter = new CoroutineAdapter(process);
    }

    @Override
    public String[] getMethodNames() {
        return METHODS;
    }

    @Override
    public MethodResult callMethod(ILuaContext context, int method, IArguments arguments) throws LuaException {
        return switch (method) {
            case 0 -> coroutineAdapter.await();
            case 1 -> respond(arguments);
            case 2 -> {
                process.markKilled("Lua driver closed the Python process.");
                yield MethodResult.of();
            }
            case 3 -> MethodResult.of(status());
            case 4 -> MethodResult.of(process.id());
            default -> throw new LuaException("Unknown Python process handle method.");
        };
    }

    private MethodResult respond(IArguments arguments) throws LuaException {
        boolean ok = arguments.getBoolean(0);
        if (ok) {
            process.finishHostWait(PythonActionResponse.success(LuaValues.toList(arguments.drop(1).getAll())));
        } else {
            process.finishHostWait(PythonActionResponse.failure(arguments.optString(1, "Lua host call failed.")));
        }
        return MethodResult.of();
    }

    private LinkedHashMap<String, Object> status() {
        var snapshot = process.snapshot();
        var state = new LinkedHashMap<String, Object>();
        state.put("computer_id", snapshot.computerId());
        state.put("process_id", snapshot.processId());
        state.put("state", snapshot.state().name().toLowerCase());
        state.put("program", snapshot.program());
        state.put("interactive", snapshot.interactive());
        state.put("started_at", snapshot.startedAt());
        state.put("detail", snapshot.detail());
        return state;
    }
}
