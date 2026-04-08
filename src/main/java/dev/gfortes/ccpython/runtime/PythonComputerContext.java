package dev.gfortes.ccpython.runtime;

import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.lua.LuaException;
import dev.gfortes.ccpython.config.CCPythonConfig;
import dev.gfortes.ccpython.network.NetworkSyncManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PythonComputerContext {
    private final Map<String, PythonProcess> processes = new ConcurrentHashMap<>();
    private final PythonComputerRuntime runtime;
    private volatile IComputerSystem computer;

    public PythonComputerContext(IComputerSystem computer) {
        this.computer = computer;
        this.runtime = new PythonComputerRuntime(this);
    }

    public synchronized PythonProcess launch(PythonLaunchSpec spec) throws LuaException {
        refresh(computer);
        if (processes.size() >= CCPythonConfig.maxProcessesPerComputer(computer.getLevel().getServer())) {
            throw new LuaException("This computer already reached its Python process limit.");
        }

        var process = new PythonProcess(this, spec);
        processes.put(process.id(), process);
        process.start();
        return process;
    }

    public void refresh(IComputerSystem computer) {
        this.computer = computer;
    }

    public IComputerSystem computer() {
        return computer;
    }

    public PythonComputerRuntime runtime() {
        return runtime;
    }

    public void detach(PythonProcess process) {
        processes.remove(process.id());
        NetworkSyncManager.broadcastClear(computer.getLevel(), computer.getID(), process.id());
    }

    public void queueEvent(String event, Object[] arguments) {
        processes.values().forEach(process -> process.queueComputerEvent(event, arguments));
    }

    public void closeAll(String reason) {
        processes.values().forEach(process -> process.markKilled(reason));
        processes.clear();
        runtime.close();
    }

    public List<PythonStatusSnapshot> snapshots() {
        return processes.values().stream()
            .map(PythonProcess::snapshot)
            .toList();
    }
}
