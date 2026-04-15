"use strict";

const fs = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const vscode = require("vscode");
const { BridgeClient } = require("./bridgeClient");
const { CCPythonFileSystemProvider, toComputerUri } = require("./fileSystemProvider");
const { CCPythonTreeProvider, ComputerNode, EntryNode } = require("./tree");
const { registerApiIntelligence } = require("./apiIntelligence");

function activate(context) {
    const output = vscode.window.createOutputChannel("CCPython Bridge");
    const client = new BridgeClient(output);
    const fileSystemProvider = new CCPythonFileSystemProvider(client, output);
    const treeProvider = new CCPythonTreeProvider(client, output);

    context.subscriptions.push(output);
    context.subscriptions.push(
        vscode.workspace.registerFileSystemProvider("ccpython", fileSystemProvider, {
            isCaseSensitive: true,
            isReadonly: false
        })
    );

    const treeView = vscode.window.createTreeView("ccpythonComputers", {
        treeDataProvider: treeProvider,
        showCollapseAll: true
    });
    context.subscriptions.push(treeView);
    registerApiIntelligence(context);

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.connectLocalBridge", async () => {
            const config = client.configuration();
            await config.update("baseUrl", "http://127.0.0.1:26780/ccpython/bridge/v1", vscode.ConfigurationTarget.Global);
            treeProvider.refresh();
            vscode.window.showInformationMessage("CCPython bridge URL set to the local default.");
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.configureBridge", async () => {
            const currentUrl = client.baseUrl();
            const baseUrl = await vscode.window.showInputBox({
                prompt: "Bridge base URL",
                value: currentUrl,
                placeHolder: "http://127.0.0.1:26780/ccpython/bridge/v1",
                ignoreFocusOut: true
            });
            if (baseUrl === undefined) return;

            const currentToken = client.authToken();
            const authToken = await vscode.window.showInputBox({
                prompt: "Bearer token (leave empty for local bridge)",
                value: currentToken,
                password: true,
                ignoreFocusOut: true
            });
            if (authToken === undefined) return;

            const config = client.configuration();
            await config.update("baseUrl", baseUrl.trim(), vscode.ConfigurationTarget.Global);
            await config.update("authToken", authToken.trim(), vscode.ConfigurationTarget.Global);
            treeProvider.refresh();
            vscode.window.showInformationMessage("CCPython bridge settings updated.");
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.pingBridge", async () => {
            await runWithErrorHandling("Ping Bridge", async () => {
                const info = await client.ping();
                output.appendLine(`[bridge] ping -> ${JSON.stringify(info)}`);
                vscode.window.showInformationMessage(
                    `CCPython bridge is online at ${info.host || "unknown-host"}:${info.port || "unknown-port"} (${info.server_type}).`
                );
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.refreshComputers", () => {
            treeProvider.refresh();
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.openComputerWorkspace", async element => {
            const computer = resolveComputer(element);
            if (!computer) return;

            const uri = toComputerUri(computer.id, "/");
            const name = computer.label ? `${computer.label} (#${computer.id})` : `Computer ${computer.id}`;
            const folders = vscode.workspace.workspaceFolders || [];
            const exists = folders.some(folder => folder.uri.toString() === uri.toString());
            if (!exists) {
                vscode.workspace.updateWorkspaceFolders(folders.length, 0, { uri, name });
            }

            vscode.commands.executeCommand("workbench.view.explorer");
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.openFile", async element => {
            const entry = resolveEntry(element);
            if (!entry || entry.entry.is_dir) return;
            if (isPreviewableImageName(entry.entry.name)) {
                await vscode.commands.executeCommand("ccpython.previewImage", element);
                return;
            }
            const uri = toComputerUri(entry.computer.id, entry.entry.path);
            const document = await vscode.workspace.openTextDocument(uri);
            await vscode.window.showTextDocument(document, { preview: false });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.previewImage", async element => {
            const entry = resolveEntry(element);
            if (!entry || entry.entry.is_dir || !isPreviewableImageName(entry.entry.name)) return;

            await runWithErrorHandling("Preview Image", async () => {
                const content = await client.readFile(entry.computer.id, entry.entry.path);
                const extension = path.extname(entry.entry.name).toLowerCase();
                const mimeType = extension === ".png" ? "image/png" : "image/jpeg";
                const dataUri = `data:${mimeType};base64,${content.toString("base64")}`;

                const panel = vscode.window.createWebviewPanel(
                    "ccpythonImagePreview",
                    `${entry.entry.name} Preview`,
                    vscode.ViewColumn.Active,
                    {
                        enableScripts: true
                    }
                );

                panel.webview.html = renderImagePreviewHtml(panel.webview, entry, dataUri);
                panel.webview.onDidReceiveMessage(async message => {
                    if (!message || typeof message.command !== "string") return;
                    if (message.command === "openLocal") {
                        await runWithErrorHandling("Open File Locally", async () => {
                            const localUri = await materializeRemoteFileLocally(client, entry);
                            await vscode.commands.executeCommand("vscode.open", localUri);
                        });
                    }
                    if (message.command === "download") {
                        await vscode.commands.executeCommand("ccpython.downloadFile", entry);
                    }
                });
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.openFileLocally", async element => {
            const entry = resolveEntry(element);
            if (!entry || entry.entry.is_dir) return;

            await runWithErrorHandling("Open File Locally", async () => {
                const localUri = await materializeRemoteFileLocally(client, entry);
                await vscode.commands.executeCommand("vscode.open", localUri);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.uploadFiles", async element => {
            const target = resolveUploadTarget(element);
            if (!target) return;

            await runWithErrorHandling("Upload Files", async () => {
                const selectedFiles = await vscode.window.showOpenDialog({
                    canSelectFiles: true,
                    canSelectFolders: false,
                    canSelectMany: true,
                    openLabel: "Upload to CCPython Computer",
                    filters: uploadFilters()
                });
                if (!selectedFiles || selectedFiles.length === 0) return;

                await vscode.window.withProgress({
                    location: vscode.ProgressLocation.Notification,
                    title: `Uploading ${selectedFiles.length} file(s)`,
                    cancellable: false
                }, async progress => {
                    let completed = 0;
                    for (const fileUri of selectedFiles) {
                        const fileName = path.basename(fileUri.fsPath);
                        const remotePath = joinRemotePath(target.remoteDirectory, fileName);
                        const content = await fs.readFile(fileUri.fsPath);
                        await client.writeFile(target.computer.id, remotePath, content);
                        completed += 1;
                        progress.report({
                            increment: (100 / selectedFiles.length),
                            message: `${completed}/${selectedFiles.length}: ${fileName}`
                        });
                    }
                });

                treeProvider.refresh();
                vscode.window.showInformationMessage(
                    `Uploaded ${selectedFiles.length} file(s) to ${target.computer.label || `computer ${target.computer.id}`}.`
                );
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.downloadFile", async element => {
            const entry = resolveEntry(element);
            if (!entry || entry.entry.is_dir) return;

            await runWithErrorHandling("Download File", async () => {
                await downloadRemoteFile(entry, {
                    client,
                    saveDialog: true
                });
                vscode.window.showInformationMessage(`Downloaded ${entry.entry.name}.`);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.powerOnComputer", async element => {
            const computer = resolveComputer(element);
            if (!computer) return;
            await runWithErrorHandling(`Turn On Computer ${computer.id}`, async () => {
                await client.power(computer.id, "on");
                treeProvider.refresh();
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.shutdownComputer", async element => {
            const computer = resolveComputer(element);
            if (!computer) return;
            await runWithErrorHandling(`Shut Down Computer ${computer.id}`, async () => {
                await client.power(computer.id, "off");
                treeProvider.refresh();
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.rebootComputer", async element => {
            const computer = resolveComputer(element);
            if (!computer) return;
            await runWithErrorHandling(`Reboot Computer ${computer.id}`, async () => {
                await client.power(computer.id, "reboot");
                treeProvider.refresh();
            });
        })
    );

    context.subscriptions.push(
        vscode.workspace.onDidChangeConfiguration(event => {
            if (event.affectsConfiguration("ccpythonBridge")) {
                treeProvider.refresh();
            }
        })
    );

    output.appendLine("CCPython Bridge extension activated.");
}

function deactivate() {}

function resolveComputer(element) {
    if (element instanceof ComputerNode) return element.computer;
    if (element instanceof EntryNode) return element.computer;
    return null;
}

function resolveEntry(element) {
    return element instanceof EntryNode ? element : null;
}

function resolveUploadTarget(element) {
    if (element instanceof ComputerNode) {
        return {
            computer: element.computer,
            remoteDirectory: "/"
        };
    }
    if (element instanceof EntryNode && element.entry.is_dir) {
        return {
            computer: element.computer,
            remoteDirectory: element.entry.path
        };
    }
    return null;
}

function joinRemotePath(directory, fileName) {
    if (!directory || directory === "/") return `/${fileName}`;
    return `${directory.replace(/\/+$/, "")}/${fileName}`;
}

function uploadFilters() {
    return {
        "Programs": ["py", "lua", "txt", "md"],
        "Data": ["json", "toml", "yaml", "yml", "csv"],
        "Images": ["png", "jpg", "jpeg", "gif", "bmp", "webp"],
        "Audio & MIDI": ["mid", "midi", "wav", "mp3", "ogg"],
        "All Files": ["*"]
    };
}

function saveFilters(fileName) {
    const extension = path.extname(fileName).replace(/^\./, "").toLowerCase();
    if (!extension) return { "All Files": ["*"] };
    return {
        [`*.${extension}`]: [extension],
        "All Files": ["*"]
    };
}

function defaultDownloadDirectory() {
    const workspaceFolder = vscode.workspace.workspaceFolders && vscode.workspace.workspaceFolders[0];
    if (workspaceFolder && workspaceFolder.uri.scheme === "file") {
        return workspaceFolder.uri.fsPath;
    }
    return os.homedir();
}

async function downloadRemoteFile(entry, options) {
    const saveUri = options.saveDialog
        ? await vscode.window.showSaveDialog({
            defaultUri: vscode.Uri.file(path.join(defaultDownloadDirectory(), entry.entry.name)),
            saveLabel: "Download from CCPython Computer",
            filters: saveFilters(entry.entry.name)
        })
        : options.destination;

    if (!saveUri) return null;

    const content = await options.client.readFile(entry.computer.id, entry.entry.path);
    await fs.mkdir(path.dirname(saveUri.fsPath), { recursive: true });
    await fs.writeFile(saveUri.fsPath, content);
    return saveUri;
}

async function materializeRemoteFileLocally(client, entry) {
    const localPath = path.join(
        os.tmpdir(),
        "ccpython-bridge",
        "remote-files",
        String(entry.computer.id),
        ...entry.entry.path.split("/").filter(Boolean)
    );

    const localUri = vscode.Uri.file(localPath);
    await downloadRemoteFile(entry, {
        client,
        saveDialog: false,
        destination: localUri
    });
    return localUri;
}

function isPreviewableImageName(fileName) {
    const extension = path.extname(fileName).toLowerCase();
    return extension === ".png" || extension === ".jpg" || extension === ".jpeg";
}

function renderImagePreviewHtml(webview, entry, dataUri) {
    const computerLabel = entry.computer.label || `Computer ${entry.computer.id}`;
    return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        body {
            font-family: var(--vscode-font-family);
            color: var(--vscode-foreground);
            background: var(--vscode-editor-background);
            margin: 0;
            padding: 16px;
        }
        .toolbar {
            display: flex;
            gap: 10px;
            align-items: center;
            margin-bottom: 16px;
            flex-wrap: wrap;
        }
        button {
            border: 1px solid var(--vscode-button-border, transparent);
            background: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
            padding: 6px 12px;
            border-radius: 6px;
            cursor: pointer;
        }
        button.secondary {
            background: var(--vscode-button-secondaryBackground, transparent);
            color: var(--vscode-button-secondaryForeground, var(--vscode-foreground));
        }
        .meta {
            opacity: 0.8;
            font-size: 12px;
        }
        .frame {
            border: 1px solid var(--vscode-panel-border);
            border-radius: 10px;
            padding: 12px;
            overflow: auto;
            background: color-mix(in srgb, var(--vscode-editor-background) 88%, black 12%);
        }
        img {
            display: block;
            max-width: 100%;
            height: auto;
            margin: 0 auto;
        }
    </style>
</head>
<body>
    <div class="toolbar">
        <button id="openLocal">Open Locally</button>
        <button id="download" class="secondary">Download</button>
        <span class="meta">${escapeHtml(computerLabel)} • ${escapeHtml(entry.entry.path)}</span>
    </div>
    <div class="frame">
        <img src="${dataUri}" alt="${escapeHtml(entry.entry.name)}" />
    </div>
    <script>
        const vscode = acquireVsCodeApi();
        document.getElementById('openLocal').addEventListener('click', () => {
            vscode.postMessage({ command: 'openLocal' });
        });
        document.getElementById('download').addEventListener('click', () => {
            vscode.postMessage({ command: 'download' });
        });
    </script>
</body>
</html>`;
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\"/g, "&quot;");
}

async function runWithErrorHandling(label, operation) {
    try {
        await operation();
    } catch (error) {
        const message = error && error.message ? error.message : String(error);
        vscode.window.showErrorMessage(`${label} failed: ${message}`);
    }
}

module.exports = {
    activate,
    deactivate
};
