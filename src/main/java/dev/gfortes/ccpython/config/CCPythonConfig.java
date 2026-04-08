package dev.gfortes.ccpython.config;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CCPythonConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_PROCESSES_PER_COMPUTER;
    public static final ModConfigSpec.IntValue MAX_PARALLEL_RUNTIMES;
    public static final ModConfigSpec.LongValue MAX_STATEMENTS_PER_PROCESS;
    public static final ModConfigSpec.LongValue WATCHDOG_TIMEOUT_MILLIS;
    public static final ModConfigSpec.LongValue SOFT_MEMORY_BUDGET_BYTES;
    public static final ModConfigSpec.LongValue MAX_SOURCE_BYTES;
    public static final ModConfigSpec.LongValue MAX_BRIDGE_PAYLOAD_BYTES;
    public static final ModConfigSpec.IntValue MAX_OPEN_FILE_HANDLES_PER_PROCESS;
    public static final ModConfigSpec.BooleanValue ENABLE_RELAXED_SINGLEPLAYER_LIMITS;
    public static final ModConfigSpec.IntValue SINGLEPLAYER_MAX_PROCESSES_PER_COMPUTER;
    public static final ModConfigSpec.IntValue SINGLEPLAYER_MAX_PARALLEL_RUNTIMES;
    public static final ModConfigSpec.LongValue SINGLEPLAYER_MAX_STATEMENTS_PER_PROCESS;
    public static final ModConfigSpec.LongValue SINGLEPLAYER_WATCHDOG_TIMEOUT_MILLIS;
    public static final ModConfigSpec.LongValue SINGLEPLAYER_SOFT_MEMORY_BUDGET_BYTES;
    public static final ModConfigSpec.LongValue SINGLEPLAYER_MAX_SOURCE_BYTES;
    public static final ModConfigSpec.LongValue SINGLEPLAYER_MAX_BRIDGE_PAYLOAD_BYTES;
    public static final ModConfigSpec.IntValue SINGLEPLAYER_MAX_OPEN_FILE_HANDLES_PER_PROCESS;
    static {
        var builder = new ModConfigSpec.Builder();

        builder.push("runtime");
        MAX_PROCESSES_PER_COMPUTER = builder
            .comment("Maximum concurrent Python processes allowed per CC computer.")
            .defineInRange("maxProcessesPerComputer", 4, 1, 32);
        MAX_PARALLEL_RUNTIMES = builder
            .comment("Maximum executor threads used for Python runtimes.")
            .defineInRange("maxParallelRuntimes", 8, 1, 64);
        MAX_STATEMENTS_PER_PROCESS = builder
            .comment("Best-effort Graal statement limit per Python process.")
            .defineInRange("maxStatementsPerProcess", 1_500_000L, 10_000L, Long.MAX_VALUE);
        WATCHDOG_TIMEOUT_MILLIS = builder
            .comment("Hard watchdog timeout for CPU-bound Python code with no host yields.")
            .defineInRange("watchdogTimeoutMillis", 20_000L, 100L, Long.MAX_VALUE);
        SOFT_MEMORY_BUDGET_BYTES = builder
            .comment("Soft per-process memory budget used for payload, source and import accounting.")
            .defineInRange("softMemoryBudgetBytes", 8L * 1024L * 1024L, 128L * 1024L, Long.MAX_VALUE);
        MAX_SOURCE_BYTES = builder
            .comment("Maximum size of a single Python source file loaded from the CraftOS filesystem.")
            .defineInRange("maxSourceBytes", 256L * 1024L, 1L, Long.MAX_VALUE);
        MAX_BRIDGE_PAYLOAD_BYTES = builder
            .comment("Maximum marshalled payload size for one Lua <-> Python bridge exchange.")
            .defineInRange("maxBridgePayloadBytes", 64L * 1024L, 1L, Long.MAX_VALUE);
        MAX_OPEN_FILE_HANDLES_PER_PROCESS = builder
            .comment("Maximum simultaneously open CraftOS file handles per Python process.")
            .defineInRange("maxOpenFileHandlesPerProcess", 32, 1, 1024);
        builder.pop();

        builder.push("singleplayer");
        ENABLE_RELAXED_SINGLEPLAYER_LIMITS = builder
            .comment("When true, integrated singleplayer worlds use the relaxed limits below instead of the dedicated-server runtime limits.")
            .define("enableRelaxedSingleplayerLimits", true);
        SINGLEPLAYER_MAX_PROCESSES_PER_COMPUTER = builder
            .comment("Maximum concurrent Python processes allowed per CC computer in integrated singleplayer.")
            .defineInRange("maxProcessesPerComputer", 8, 1, 64);
        SINGLEPLAYER_MAX_PARALLEL_RUNTIMES = builder
            .comment("Maximum executor threads used for Python runtimes in integrated singleplayer.")
            .defineInRange("maxParallelRuntimes", 12, 1, 128);
        SINGLEPLAYER_MAX_STATEMENTS_PER_PROCESS = builder
            .comment("Best-effort Graal statement limit per Python process in integrated singleplayer.")
            .defineInRange("maxStatementsPerProcess", 5_000_000L, 10_000L, Long.MAX_VALUE);
        SINGLEPLAYER_WATCHDOG_TIMEOUT_MILLIS = builder
            .comment("Hard watchdog timeout for CPU-bound Python code with no host yields in integrated singleplayer.")
            .defineInRange("watchdogTimeoutMillis", 60_000L, 100L, Long.MAX_VALUE);
        SINGLEPLAYER_SOFT_MEMORY_BUDGET_BYTES = builder
            .comment("Soft per-process memory budget used for payload, source and import accounting in integrated singleplayer.")
            .defineInRange("softMemoryBudgetBytes", 32L * 1024L * 1024L, 128L * 1024L, Long.MAX_VALUE);
        SINGLEPLAYER_MAX_SOURCE_BYTES = builder
            .comment("Maximum size of a single Python source file loaded from the CraftOS filesystem in integrated singleplayer.")
            .defineInRange("maxSourceBytes", 2L * 1024L * 1024L, 1L, Long.MAX_VALUE);
        SINGLEPLAYER_MAX_BRIDGE_PAYLOAD_BYTES = builder
            .comment("Maximum marshalled payload size for one Lua <-> Python bridge exchange in integrated singleplayer.")
            .defineInRange("maxBridgePayloadBytes", 512L * 1024L, 1L, Long.MAX_VALUE);
        SINGLEPLAYER_MAX_OPEN_FILE_HANDLES_PER_PROCESS = builder
            .comment("Maximum simultaneously open CraftOS file handles per Python process in integrated singleplayer.")
            .defineInRange("maxOpenFileHandlesPerProcess", 128, 1, 4096);
        builder.pop();

        SPEC = builder.build();
    }

    private CCPythonConfig() {
    }

    public static int maxProcessesPerComputer(MinecraftServer server) {
        return useSingleplayerLimits(server) ? SINGLEPLAYER_MAX_PROCESSES_PER_COMPUTER.get() : MAX_PROCESSES_PER_COMPUTER.get();
    }

    public static int maxParallelRuntimes(MinecraftServer server) {
        return useSingleplayerLimits(server) ? SINGLEPLAYER_MAX_PARALLEL_RUNTIMES.get() : MAX_PARALLEL_RUNTIMES.get();
    }

    public static long maxStatementsPerProcess(MinecraftServer server) {
        return useSingleplayerLimits(server) ? SINGLEPLAYER_MAX_STATEMENTS_PER_PROCESS.get() : MAX_STATEMENTS_PER_PROCESS.get();
    }

    public static long watchdogTimeoutMillis(MinecraftServer server) {
        return useSingleplayerLimits(server) ? SINGLEPLAYER_WATCHDOG_TIMEOUT_MILLIS.get() : WATCHDOG_TIMEOUT_MILLIS.get();
    }

    public static long softMemoryBudgetBytes(MinecraftServer server) {
        return useSingleplayerLimits(server) ? SINGLEPLAYER_SOFT_MEMORY_BUDGET_BYTES.get() : SOFT_MEMORY_BUDGET_BYTES.get();
    }

    public static long maxSourceBytes(MinecraftServer server) {
        return useSingleplayerLimits(server) ? SINGLEPLAYER_MAX_SOURCE_BYTES.get() : MAX_SOURCE_BYTES.get();
    }

    public static long maxBridgePayloadBytes(MinecraftServer server) {
        return useSingleplayerLimits(server) ? SINGLEPLAYER_MAX_BRIDGE_PAYLOAD_BYTES.get() : MAX_BRIDGE_PAYLOAD_BYTES.get();
    }

    public static int maxOpenFileHandlesPerProcess(MinecraftServer server) {
        return useSingleplayerLimits(server) ? SINGLEPLAYER_MAX_OPEN_FILE_HANDLES_PER_PROCESS.get() : MAX_OPEN_FILE_HANDLES_PER_PROCESS.get();
    }

    private static boolean useSingleplayerLimits(MinecraftServer server) {
        return ENABLE_RELAXED_SINGLEPLAYER_LIMITS.get() && server != null && !server.isDedicatedServer();
    }
}
