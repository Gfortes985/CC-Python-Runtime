package dev.gfortes.ccpython.api;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dev.gfortes.ccpython.config.CCPythonConfig;
import dev.gfortes.ccpython.runtime.FileSystemAdapter;
import dev.gfortes.ccpython.runtime.PythonLaunchSpec;
import dev.gfortes.ccpython.runtime.PythonProcessHandle;
import dev.gfortes.ccpython.runtime.PythonRuntimeManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PythonLuaApi implements ILuaAPI {
    private final IComputerSystem computer;

    public PythonLuaApi(IComputerSystem computer) {
        this.computer = computer;
    }

    @Override
    public String[] getNames() {
        return new String[] { "ccpython" };
    }

    @Override
    public void shutdown() {
        PythonRuntimeManager.getInstance().cleanupComputer(computer);
    }

    @LuaFunction
    public final Object start(IArguments arguments) throws LuaException {
        var options = arguments.getTable(0);

        String program = stringValue(options.get("program"));
        String cwd = options.containsKey("cwd") ? stringValue(options.get("cwd")) : "/";
        boolean interactive = booleanValue(options.get("interactive"));
        List<Object> argv = listValue(options.get("args"));

        var spec = new PythonLaunchSpec(
            FileSystemAdapter.normalizeProgramPath(program, cwd),
            FileSystemAdapter.normalizeWorkingDir(cwd),
            argv,
            interactive
        );

        if (!interactive && spec.program() == null) {
            throw new LuaException("Python launcher requires a program path when interactive mode is disabled.");
        }

        return new PythonProcessHandle(PythonRuntimeManager.getInstance().launch(computer, spec));
    }

    @LuaFunction
    public final Map<String, Object> limits() {
        var server = computer.getLevel().getServer();
        var map = new LinkedHashMap<String, Object>();
        map.put("max_processes_per_computer", CCPythonConfig.maxProcessesPerComputer(server));
        map.put("max_parallel_runtimes", CCPythonConfig.maxParallelRuntimes(server));
        map.put("max_statements_per_process", CCPythonConfig.maxStatementsPerProcess(server));
        map.put("watchdog_timeout_millis", CCPythonConfig.watchdogTimeoutMillis(server));
        map.put("soft_memory_budget_bytes", CCPythonConfig.softMemoryBudgetBytes(server));
        map.put("max_source_bytes", CCPythonConfig.maxSourceBytes(server));
        map.put("max_bridge_payload_bytes", CCPythonConfig.maxBridgePayloadBytes(server));
        map.put("max_open_file_handles_per_process", CCPythonConfig.maxOpenFileHandlesPerProcess(server));
        return map;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static List<Object> listValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) return List.of();

        return map.entrySet().stream()
            .filter(entry -> entry.getKey() instanceof Number)
            .sorted(Comparator.comparingInt(entry -> ((Number) entry.getKey()).intValue()))
            .collect(ArrayList::new, (list, entry) -> list.add(entry.getValue()), ArrayList::addAll);
    }
}
