# Architecture

## 1. Runtime choice

The project uses `GraalPy` embedded through `org.graalvm.python:python-embedding`.

Why this choice:

- `Jython` is effectively locked to Python 2 and does not match the target language model.
- `JNI + CPython` would violate the no-external-runtime goal and would make sandboxing and multiplayer hosting much harder.
- `GraalPy` keeps execution inside the JVM, works with Java 21, can deny host class lookup, IO, process creation and thread creation, and fits the server-only architecture.

## 2. Execution model

The execution stack is intentionally split:

1. `python.lua` is a normal CraftOS program.
2. It asks the Java API `ccpython.start(...)` to create a server-side Python process.
3. `PythonProcess` runs the GraalPy context on a dedicated executor thread.
4. Whenever Python code needs a CC API, the runtime emits a host-call action.
5. `ccpython.driver` receives that action and performs the real Lua call inside CraftOS.
6. The Lua driver resumes the Java process with the return values.

This keeps Minecraft's main server thread unblocked while preserving CraftOS semantics and multiplayer behavior.

## 3. Event and coroutine model

Python does not run on the client and does not talk to Minecraft packets directly for terminal output. Instead:

- Python calls `os.pull_event()`, `sleep()`, `rednet.receive()` and turtle functions through the Lua bridge.
- Those Lua functions yield naturally inside CC: Tweaked.
- The Java runtime waits on a `CompletableFuture` until the Lua driver sends results back.
- `CoroutineAdapter` converts the Java process state into `MethodResult.pullEventRaw(...)` waits so Ctrl+T and CraftOS lifecycle rules continue to work.

## 4. Networking

Terminal synchronization:

- reused from CC: Tweaked directly
- this is the correct multiplayer behavior because the terminal already has robust server/client sync

Custom Python payloads:

- `PythonRuntimeStatePayload`
- `PythonRuntimeErrorPayload`

Those payloads provide metadata, tracebacks and room for future client-side overlays without moving Python execution to the client.

## 5. Sandboxing

The sandbox has multiple layers:

- no client execution
- no direct JVM access
- no direct host class lookup
- no host IO
- no host process creation
- no host thread creation
- curated import allowlist
- blocked dangerous modules (`socket`, `ssl`, `subprocess`, `threading`, `polyglot`, etc.)
- best-effort Graal statement limit
- watchdog timeout for CPU-bound loops
- soft payload/source budget for memory accounting

Important caveat:

- hard heap isolation depends on the stricter Graal sandbox options being available at runtime
- when they are unavailable, the mod logs a warning and continues with soft accounting + watchdog protection

## 6. Project components

- `PythonRuntimeManager`: per-server registry of computer contexts
- `PythonComputerContext`: per-computer process ownership
- `PythonExecutionService`: executor pool + watchdog
- `PythonEventLoop`: blocks the GraalPy worker until Lua responds
- `PythonAPIBindings`: installs the host bridge and Python bootstrap
- `SandboxManager`: builds the GraalPy context with restricted access
- `FileSystemAdapter`: normalizes CraftOS paths and search paths
- `CoroutineAdapter`: translates runtime wakeups into CC cooperative waits
- `NetworkSyncManager`: pushes runtime state/errors to clients
- `ClientTerminalSync`: documents the split between native CC terminal sync and Python metadata sync
- `PacketHandler`: registers NeoForge payload codecs/handlers

## 7. Remaining work

The repository intentionally leaves room for future iterations:

- richer filesystem compatibility for binary handles
- persistent per-computer REPL history
- full startup.py autorun integration
- client overlays for runtime state in terminal GUIs
- deeper test coverage with dedicated server and multishell scenarios
