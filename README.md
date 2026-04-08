# CC Python Runtime

`CC Python Runtime` is a NeoForge addon for `Minecraft 1.21.1` + `CC: Tweaked` which adds Python as a second language inside CraftOS computers and turtles.

The mod keeps Lua intact and uses a server-side GraalPy runtime for every running Python process. Terminal rendering and player input continue to travel through the native CC: Tweaked terminal stack, while Python-specific runtime state and tracebacks are mirrored through NeoForge payloads for client visibility.

## Design summary

- Lua remains the default language and the shell stays unchanged.
- Python executes on the server only.
- Each running `python` or `py` command owns one GraalPy context and one Lua bridge process.
- The bridge reuses CraftOS APIs (`term`, `turtle`, `rednet`, `redstone`, `peripheral`, `fs`, `os`) by forwarding Python host calls into Lua.
- Multiplayer works because clients never execute Python and still consume the normal CC terminal packets.

## Current scope

- `python file.py` launcher
- `py` alias
- interactive REPL
- Python import loader backed by the CraftOS filesystem
- best-effort statement, watchdog and soft-memory limits
- runtime state and traceback payloads for client sync

## Important limitation

This repository implements a serious server-side foundation and a usable vertical slice, but it is still a staged runtime rather than a drop-in, full-CPython replacement. The most notable tradeoff is sandboxing: the Java-embedded GraalPy backend is the correct base choice, but hard heap isolation remains best-effort and falls back to soft accounting when the stricter Graal sandbox options are unavailable.

See `docs/ARCHITECTURE.md` for a full breakdown.
