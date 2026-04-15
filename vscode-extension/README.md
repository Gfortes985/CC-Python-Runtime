# CCPython VS Code Bridge

This extension is the first VS Code MVP for the in-game CCPython bridge.

## What it can do

- connect to the built-in bridge from the mod
- show loaded CC computers in a sidebar
- browse the CraftOS filesystem
- open files directly in VS Code
- save changes back into the computer
- turn computers on, off, and reboot them

## Bridge setup

The mod exposes the bridge at:

`http://127.0.0.1:26780/ccpython/bridge/v1`

You can change that in `config/ccpython-common.toml`.

If you enable remote bridge access on a server, set the same Bearer token in the extension settings:

- `ccpythonBridge.baseUrl`
- `ccpythonBridge.authToken`

## Running the extension

1. Open the `vscode-extension` folder in VS Code.
2. Press `F5` to launch the Extension Development Host.
3. In the new VS Code window, open the `CCPython` activity bar view.
4. Run `CCPython: Connect to Local Bridge` or `CCPython: Configure Bridge`.
5. Use `CCPython: Ping Bridge` to verify the connection.

## Current limitations

- only loaded computers are visible
- this MVP does not yet stream terminal output
- pairing and multi-user permissions still live on the mod side and are not surfaced in the extension yet
- Python run/stop commands are still the next step
