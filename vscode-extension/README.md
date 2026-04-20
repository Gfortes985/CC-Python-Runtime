# CCPython VS Code Bridge

This extension connects VS Code to CCPython computers through the built-in dev bridge exposed by the mod.

## Current feature set

- connect to a local world or a remote bridge
- pair with LAN or dedicated servers
- manage per-computer ownership and whitelist access
- browse loaded CC computers in a sidebar
- pick a current computer and keep it in the status bar
- open, edit, and save remote files
- upload files and whole folders
- drag and drop local files into the remote tree
- bind a local workspace to a computer for sync-on-save
- search files on the computer from VS Code
- run, stop, power on, shut down, and reboot computers
- open a live console panel with runtime status, terminal text, and traceback links
- jump from traceback entries to the correct remote file and line
- open `startup.py` quickly
- create new scripts from templates
- preview or inspect:
  - `png`, `jpg`, `jpeg`, `gif`, `webp`
  - `json`, `toml`, `yaml`, `yml`
  - `mid`, `midi`
- generate local Python API stubs for better autocomplete
- use snippets, hover docs, diagnostics, and CCPython API completions

## Bridge setup

The default local bridge URL is:

`http://127.0.0.1:26780/ccpython/bridge/v1`

You can change that in `config/ccpython-common.toml` or in VS Code settings:

- `ccpythonBridge.baseUrl`
- `ccpythonBridge.authToken`

For a dedicated server or LAN world:

1. Enable the bridge on the server.
2. If remote access is enabled, either:
   - set the same static bearer token in the extension, or
   - create a one-time pairing code from a trusted admin/local session for a specific online player with `Start Bridge Pairing`, then finish it on the remote client with `Complete Bridge Pairing`.

## Ownership and sharing

In multiplayer, the bridge uses an `owner + whitelist` model:

- the player who places a CC computer becomes its owner automatically
- only the owner, whitelisted players, or bridge admins can see and control that computer from VS Code
- the owner can grant or revoke access for other online players
- bridge admins can inspect access, transfer ownership, and create pairing codes for a selected player

Useful commands in the sidebar:

- `Show Computer Access`
- `Claim Computer Ownership`
- `Grant Computer Access`
- `Revoke Computer Access`
- `Set Computer Owner`

## Running the extension

1. Open the `vscode-extension` folder in VS Code.
2. Press `F5`.
3. Choose `Run CCPython Bridge Extension`.
4. In the Extension Development Host, open the `CCPython` activity bar.
5. Run `CCPython: Connect to Local Bridge` or `CCPython: Configure Bridge`.
6. Run `CCPython: Ping Bridge`.
7. Run `CCPython: Refresh Computers`.

## Fast workflow

Recommended day-to-day loop:

1. Select a current computer.
2. Open `startup.py` or create a script from a template.
3. Open the console panel.
4. Run the current file.
5. If you prefer a local folder workflow, bind the workspace to the current computer and let sync-on-save upload changes automatically.

## Notes

- Only loaded computers are visible.
- Terminal output is currently polled as snapshots.
- MIDI preview shows metadata and offers local open/download helpers, but it is not a full in-extension player.
- Data inspectors are meant for quick inspection, not full schema validation.
