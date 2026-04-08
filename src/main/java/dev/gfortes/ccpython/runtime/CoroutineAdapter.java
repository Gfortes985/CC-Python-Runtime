package dev.gfortes.ccpython.runtime;

import dan200.computercraft.api.lua.ILuaCallback;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;

public final class CoroutineAdapter {
    private final PythonProcess process;
    private final ILuaCallback callback = this::resume;

    public CoroutineAdapter(PythonProcess process) {
        this.process = process;
    }

    public MethodResult await() throws LuaException {
        return resume(null);
    }

    public MethodResult resume(Object[] event) throws LuaException {
        if (event != null && event.length > 0 && "terminate".equals(event[0])) {
            process.markKilled("Terminated by Ctrl+T.");
        }

        var deliverable = process.peekDeliverableAction();
        if (deliverable != null) return MethodResult.of(deliverable.toLuaTable());
        return MethodResult.pullEventRaw(null, callback);
    }
}
