# CCPython Dev Bridge

The first bridge MVP exposes a small HTTP API for external editors such as VS Code.

## Config

Bridge settings live in `config/ccpython-common.toml`:

- `devBridge.enabled`
- `devBridge.host`
- `devBridge.port`
- `devBridge.allowRemote`
- `devBridge.authToken`

Default behavior is local-only:

- the bridge binds to `127.0.0.1`
- localhost requests are trusted
- remote access is disabled

When `devBridge.allowRemote = true`:

- the bridge binds to `devBridge.host`
- localhost stays trusted
- non-localhost requests require `Authorization: Bearer <authToken>`

## Current endpoints

Base path: `/ccpython/bridge/v1`

- `GET /ping`
- `GET /computers`
- `GET /computers/{id}`
- `GET /computers/{id}/files?path=/`
- `GET /computers/{id}/file?path=/startup.lua&encoding=utf8`
- `PUT /computers/{id}/file?path=/startup.lua&encoding=raw`
- `DELETE /computers/{id}/file?path=/startup.lua`
- `POST /computers/{id}/mkdir?path=/project`
- `POST /computers/{id}/move?from=/old.lua&to=/new.lua`
- `POST /computers/{id}/power/on`
- `POST /computers/{id}/power/off`
- `POST /computers/{id}/power/reboot`

## Notes

- The bridge currently works with computers that are loaded on the server.
- File operations are executed on the Minecraft server thread for safety.
- This is the backend foundation for a future VS Code extension. Pairing, workspace mirroring, terminal streaming, and conflict handling are still next-step work.
