# CCPython Dev Bridge

The CCPython dev bridge is an HTTP API exposed by the mod for local tools such as the VS Code extension.

Base path:

`/ccpython/bridge/v1`

## Config

Bridge settings live in `config/ccpython-common.toml`:

- `devBridge.enabled`
- `devBridge.host`
- `devBridge.port`
- `devBridge.allowRemote`
- `devBridge.authToken`
- `devBridge.enablePairing`
- `devBridge.pairingCodeTtlSeconds`

Default behavior is local-only:

- the bridge binds to `127.0.0.1`
- localhost requests are trusted
- remote access is disabled

When `devBridge.allowRemote = true`:

- the bridge binds to `devBridge.host`
- localhost stays trusted
- non-localhost requests require either:
  - `Authorization: Bearer <authToken>` from config, or
  - a bearer token issued through bridge pairing

When `devBridge.enablePairing = true`:

- a trusted admin session can create a short-lived pairing code for a specific online player
- an already paired player session can create a new pairing code only for that same player
- the remote client exchanges that code for a persistent bearer token bound to that player

## Multiplayer access model

The bridge now uses `owner + whitelist` access control per loaded CC computer:

- newly placed computers are auto-claimed by the player who placed the block
- only the owner, whitelisted players, or bridge admins can see a computer through `/computers`
- owner and whitelisted players can use filesystem/runtime/power endpoints for that computer
- only the owner or a bridge admin can change that computer's whitelist
- only a bridge admin can transfer ownership to another player

ACL metadata is stored in:

`config/ccpython/bridge/acl.tsv`

Pairing tokens are stored in:

`config/ccpython/bridge/tokens.tsv`

## Core endpoints

- `GET /ping`
- `GET /computers`
- `GET /computers/{id}`

Computer metadata includes family, filesystem root, mount information, and power state for loaded computers.

## Filesystem endpoints

- `GET /computers/{id}/files?path=/`
- `GET /computers/{id}/file?path=/startup.py&encoding=utf8`
- `GET /computers/{id}/file?path=/image.png&encoding=base64`
- `PUT /computers/{id}/file?path=/startup.py&encoding=raw`
- `DELETE /computers/{id}/file?path=/old.lua`
- `POST /computers/{id}/mkdir?path=/project`
- `POST /computers/{id}/move?from=/old.lua&to=/new.lua`
- `GET /computers/{id}/search?query=monitor&path=/&limit=100`

Search returns path matches and text-content matches for likely text files.

## Runtime endpoints

- `GET /computers/{id}/runtime`
- `GET /computers/{id}/terminal`
- `POST /computers/{id}/python/run?program=/startup.py&cwd=/&interactive=false`
- `POST /computers/{id}/python/stop`
- `POST /computers/{id}/python/stop?process_id=<id>`

Runtime payloads expose:

- aggregate status such as `idle`, `running`, or `crashed`
- active Python processes
- last traceback and last process snapshot when available
- terminal text buffer snapshots for live console polling

## Power endpoints

- `POST /computers/{id}/power/on`
- `POST /computers/{id}/power/off`
- `POST /computers/{id}/power/reboot`

## Auth endpoints

- `GET /auth/status`
- `GET /players`
- `POST /auth/pair/start?label=VSCode&player=PlayerName`
- `POST /auth/pair/complete?code=123456&label=VSCode`

`/auth/status` reports whether the bridge is local-only, remote-enabled, or pairing-enabled, plus the current bridge identity.

## ACL endpoints

- `GET /computers/{id}/acl`
- `POST /computers/{id}/acl/claim`
- `POST /computers/{id}/acl/grant?player=PlayerName`
- `POST /computers/{id}/acl/revoke?player=PlayerName`
- `POST /computers/{id}/acl/set-owner?player=PlayerName`

For player-bound sessions:

- `claim` claims an unowned computer for the authenticated player
- `grant` and `revoke` require the authenticated player to be the owner
- `set-owner` requires an admin session

For admin sessions:

- `claim` and `pair/start` require a target online player via `?player=...`
- admins can manage any loaded computer

## Notes

- Only currently loaded computers are visible.
- Filesystem and power operations are executed on the Minecraft server thread for safety.
- Terminal output is currently polled as snapshots, not streamed over WebSocket.
- The bridge is designed to serve both singleplayer localhost workflows and LAN/dedicated server workflows with auth and per-computer ACLs.
