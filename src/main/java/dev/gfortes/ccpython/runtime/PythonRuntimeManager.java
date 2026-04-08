package dev.gfortes.ccpython.runtime;

import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.lua.LuaException;
import dev.gfortes.ccpython.CCPythonMod;
import dev.gfortes.ccpython.network.NetworkSyncManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PythonRuntimeManager {
    private static final PythonRuntimeManager INSTANCE = new PythonRuntimeManager();

    private final Map<Integer, PythonComputerContext> computers = new ConcurrentHashMap<>();

    private PythonRuntimeManager() {
    }

    public static PythonRuntimeManager getInstance() {
        return INSTANCE;
    }

    public PythonProcess launch(IComputerSystem computer, PythonLaunchSpec spec) throws LuaException {
        var context = computers.compute(computer.getID(), (id, existing) -> {
            if (existing == null) return new PythonComputerContext(computer);
            existing.refresh(computer);
            return existing;
        });
        CCPythonMod.LOGGER.debug(
            "Launching Python process on computer {} with program '{}' (interactive={})",
            computer.getID(),
            spec.program(),
            spec.interactive()
        );
        return context.launch(spec);
    }

    public void cleanupComputer(IComputerSystem computer) {
        var context = computers.remove(computer.getID());
        if (context != null) context.closeAll("Computer shutdown.");
    }

    public void queueEvent(int computerId, String event, Object[] arguments) {
        if (event == null || event.isBlank() || PythonProcess.WAKE_EVENT.equals(event)) return;

        var context = computers.get(computerId);
        if (context != null) context.queueEvent(event, arguments);
    }

    public List<PythonStatusSnapshot> activeSnapshots() {
        var snapshots = new ArrayList<PythonStatusSnapshot>();
        for (var context : computers.values()) {
            snapshots.addAll(context.snapshots());
        }
        return snapshots;
    }

    public void syncPlayer(ServerPlayer player) {
        NetworkSyncManager.syncPlayer(player, activeSnapshots());
    }

    public void shutdownServer(MinecraftServer server) {
        computers.values().forEach(context -> context.closeAll("Server shutdown."));
        computers.clear();
        PythonExecutionService.getInstance().shutdown();
    }
}
