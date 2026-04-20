"use strict";

const fs = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const vscode = require("vscode");
const { generateStubText, registerApiIntelligence } = require("./apiIntelligence");
const { BridgeClient } = require("./bridgeClient");
const {
    escapeHtml,
    imageMimeType,
    inspectDataFile,
    inspectMidiFile,
    isInspectableDataName,
    isMidiName,
    isPreviewableImageName,
    renderDataInspectorHtml,
    renderMidiInspectorHtml
} = require("./inspectors");
const { CCPythonFileSystemProvider, parseUri, toComputerUri } = require("./fileSystemProvider");
const {
    CCPythonTreeDragAndDropController,
    CCPythonTreeProvider,
    ComputerNode,
    EntryNode
} = require("./tree");
const { getTemplate, templateItems } = require("./templates");

function activate(context) {
    const output = vscode.window.createOutputChannel("CCPython Bridge");
    const client = new BridgeClient(output);
    const fileSystemProvider = new CCPythonFileSystemProvider(client, output);
    const treeProvider = new CCPythonTreeProvider(client, output);
    const state = {
        currentComputerId: context.workspaceState.get("ccpython.currentComputerId", null),
        bridgeInfo: null,
        lastTreeSelection: null,
        consolePanel: null,
        consolePanelComputerId: null,
        consolePollTimer: null,
        syncBindings: context.workspaceState.get("ccpython.syncBindings", {})
    };

    const currentComputerStatus = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 120);
    const bridgeStatus = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 119);
    currentComputerStatus.command = "ccpython.selectCurrentComputer";
    bridgeStatus.command = "ccpython.pingBridge";
    currentComputerStatus.show();
    bridgeStatus.show();

    const dragAndDropController = new CCPythonTreeDragAndDropController(async (target, uris) => {
        const uploadTarget = resolveUploadTarget(target) || await resolveCurrentUploadTarget();
        if (!uploadTarget) return;
        await uploadUrisToTarget(uris, uploadTarget, { treeProvider, client, output });
    });

    context.subscriptions.push(output, currentComputerStatus, bridgeStatus);
    context.subscriptions.push(
        vscode.workspace.registerFileSystemProvider("ccpython", fileSystemProvider, {
            isCaseSensitive: true,
            isReadonly: false
        })
    );

    const treeView = vscode.window.createTreeView("ccpythonComputers", {
        treeDataProvider: treeProvider,
        showCollapseAll: true,
        dragAndDropController
    });
    context.subscriptions.push(treeView);
    registerApiIntelligence(context);

    treeView.onDidChangeSelection(async event => {
        const selected = event.selection[0];
        state.lastTreeSelection = selected || null;
        const computer = resolveComputer(selected);
        if (computer) {
            await setCurrentComputer(computer);
        }
    }, null, context.subscriptions);

    const refreshBridgeStatus = async () => {
        try {
            const info = await client.ping();
            state.bridgeInfo = info;
            bridgeStatus.text = `$(radio-tower) CCPy ${info.host || "bridge"}:${info.port || "?"}`;
            bridgeStatus.tooltip = `Connected to ${info.server_type} bridge\nIdentity: ${describeIdentity(info.identity)}`;
        } catch (error) {
            state.bridgeInfo = null;
            bridgeStatus.text = "$(warning) CCPy disconnected";
            bridgeStatus.tooltip = error && error.message ? error.message : "Bridge offline";
        }

        await refreshCurrentComputerStatus();
    };

    const refreshCurrentComputerStatus = async () => {
        if (!state.currentComputerId) {
            currentComputerStatus.text = "$(vm-outline) CCPy no computer";
            currentComputerStatus.tooltip = "Choose a current CCPython computer";
            return;
        }

        try {
            const computer = await client.getComputer(state.currentComputerId);
            const runtime = await client.runtimeState(state.currentComputerId);
            const status = runtime.status || "idle";
            const icon = computer.on ? "vm-active" : "vm-outline";
            currentComputerStatus.text = `$(${icon}) ${computer.label || `#${computer.id}`} ${status}`;
            currentComputerStatus.tooltip = [
                `Computer ${computer.id}`,
                `State: ${computer.on ? "on" : "off"}`,
                `Runtime: ${status}`,
                runtime.last_process ? `Last: ${runtime.last_process.state} ${runtime.last_process.program}` : "Last: none"
            ].join("\n");
        } catch (error) {
            currentComputerStatus.text = `$(vm-outline) #${state.currentComputerId} unavailable`;
            currentComputerStatus.tooltip = error && error.message ? error.message : "Current computer is not available";
        }
    };

    const statusTimer = setInterval(() => {
        refreshBridgeStatus().catch(error => output.appendLine(`[status] ${error && error.message ? error.message : String(error)}`));
    }, 4000);
    context.subscriptions.push(new vscode.Disposable(() => clearInterval(statusTimer)));

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.connectLocalBridge", async () => {
            const config = client.configuration();
            await config.update("baseUrl", "http://127.0.0.1:26780/ccpython/bridge/v1", vscode.ConfigurationTarget.Global);
            treeProvider.refresh();
            await refreshBridgeStatus();
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
            await refreshBridgeStatus();
            vscode.window.showInformationMessage("CCPython bridge settings updated.");
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.pingBridge", async () => {
            await runWithErrorHandling("Ping Bridge", async () => {
                const info = await client.ping();
                state.bridgeInfo = info;
                output.appendLine(`[bridge] ping -> ${JSON.stringify(info)}`);
                await refreshCurrentComputerStatus();
                vscode.window.showInformationMessage(
                    `CCPython bridge is online at ${info.host || "unknown-host"}:${info.port || "unknown-port"} (${info.server_type}).`
                );
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.startBridgePairing", async () => {
            await runWithErrorHandling("Start Bridge Pairing", async () => {
                const auth = await client.authStatus();
                const identity = auth.identity || {};
                const label = await vscode.window.showInputBox({
                    prompt: "Label for the remote client",
                    value: `VS Code on ${os.hostname()}`,
                    ignoreFocusOut: true
                });
                if (label === undefined) return;

                let player = null;
                let playerLabel = identity.player_name || identity.player_uuid || null;
                if (identity.admin) {
                    const selectedPlayer = await pickOnlinePlayer(client, {
                        title: "Choose the player who should receive this pairing code"
                    });
                    if (!selectedPlayer) return;
                    player = selectedPlayer.name;
                    playerLabel = selectedPlayer.name;
                } else if (!identity.player_uuid) {
                    throw new Error("This bridge session is not allowed to create a player pairing code.");
                }

                const response = await client.startPairing(label.trim() || "VS Code", player);
                const pairing = response.pairing;
                if (!pairing) throw new Error("Bridge did not return a pairing code.");

                await vscode.env.clipboard.writeText(pairing.code);
                vscode.window.showInformationMessage(`Pair code ${pairing.code} for ${playerLabel || "player"} copied to clipboard. Expires at ${pairing.expires_at_iso}.`);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.completeBridgePairing", async () => {
            await runWithErrorHandling("Complete Bridge Pairing", async () => {
                const code = await vscode.window.showInputBox({
                    prompt: "Enter the one-time pairing code",
                    ignoreFocusOut: true
                });
                if (!code) return;

                const label = await vscode.window.showInputBox({
                    prompt: "Session label",
                    value: `VS Code on ${os.hostname()}`,
                    ignoreFocusOut: true
                });
                if (label === undefined) return;

                const response = await client.completePairing(code.trim().toUpperCase(), label.trim() || "VS Code");
                if (!response.token) throw new Error("Bridge did not return a bearer token.");

                await client.configuration().update("authToken", response.token, vscode.ConfigurationTarget.Global);
                await refreshBridgeStatus();
                vscode.window.showInformationMessage("Remote CCPython bridge paired successfully.");
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.refreshComputers", async () => {
            treeProvider.refresh();
            await refreshBridgeStatus();
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.showComputerAccess", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Show Computer Access");
            if (!computer) return;
            await runWithErrorHandling("Show Computer Access", async () => {
                await showComputerAccess(computer);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.claimComputerOwnership", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Claim Computer Ownership");
            if (!computer) return;
            await runWithErrorHandling("Claim Computer Ownership", async () => {
                await client.claimAcl(computer.id);
                treeProvider.refresh();
                await refreshBridgeStatus();
                await showComputerAccess(computer);
                vscode.window.showInformationMessage(`Claimed ownership of ${computer.label || `computer ${computer.id}`}.`);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.grantComputerAccess", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Grant Computer Access");
            if (!computer) return;
            await runWithErrorHandling("Grant Computer Access", async () => {
                const acl = await client.getAcl(computer.id);
                const excludedUuids = new Set([
                    ...(acl && acl.owner ? [acl.owner.uuid] : []),
                    ...((acl && Array.isArray(acl.whitelist) ? acl.whitelist : []).map(player => player.uuid))
                ]);
                const selectedPlayer = await pickOnlinePlayer(client, {
                    title: `Grant access to ${computer.label || `computer ${computer.id}`}`,
                    excludeUuids
                });
                if (!selectedPlayer) return;

                await client.grantAcl(computer.id, selectedPlayer.uuid);
                treeProvider.refresh();
                await showComputerAccess(computer);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.revokeComputerAccess", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Revoke Computer Access");
            if (!computer) return;
            await runWithErrorHandling("Revoke Computer Access", async () => {
                const acl = await client.getAcl(computer.id);
                const whitelist = acl && Array.isArray(acl.whitelist) ? acl.whitelist : [];
                if (whitelist.length === 0) {
                    vscode.window.showInformationMessage(`No shared players on ${computer.label || `computer ${computer.id}`}.`);
                    return;
                }

                const choice = await vscode.window.showQuickPick(
                    whitelist.map(player => ({
                        label: player.name,
                        description: player.uuid,
                        player
                    })),
                    {
                        title: `Revoke access from ${computer.label || `computer ${computer.id}`}`
                    }
                );
                if (!choice) return;

                await client.revokeAcl(computer.id, choice.player.uuid);
                treeProvider.refresh();
                await showComputerAccess(computer);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.setComputerOwner", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Set Computer Owner");
            if (!computer) return;
            await runWithErrorHandling("Set Computer Owner", async () => {
                const selectedPlayer = await pickOnlinePlayer(client, {
                    title: `Transfer ownership of ${computer.label || `computer ${computer.id}`}`
                });
                if (!selectedPlayer) return;

                await client.setOwnerAcl(computer.id, selectedPlayer.uuid);
                treeProvider.refresh();
                await showComputerAccess(computer);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.selectCurrentComputer", async element => {
            const direct = resolveComputer(element);
            if (direct) {
                await setCurrentComputer(direct);
                return;
            }

            await runWithErrorHandling("Select Current Computer", async () => {
                const computers = await client.listComputers();
                if (computers.length === 0) {
                    vscode.window.showWarningMessage("No loaded CCPython computers were found.");
                    return;
                }

                const choice = await vscode.window.showQuickPick(
                    computers.map(computer => ({
                        label: computer.label || `Computer ${computer.id}`,
                        description: `#${computer.id} ${computer.on ? "on" : "off"}`,
                        computer
                    })),
                    {
                        title: "Select current CCPython computer"
                    }
                );
                if (!choice) return;
                await setCurrentComputer(choice.computer);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.openComputerWorkspace", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Open Computer as Workspace");
            if (!computer) return;

            const uri = toComputerUri(computer.id, "/");
            const name = computer.label ? `${computer.label} (#${computer.id})` : `Computer ${computer.id}`;
            const folders = vscode.workspace.workspaceFolders || [];
            const exists = folders.some(folder => folder.uri.toString() === uri.toString());
            if (!exists) {
                vscode.workspace.updateWorkspaceFolders(folders.length, 0, { uri, name });
            }

            await setCurrentComputer(computer);
            vscode.commands.executeCommand("workbench.view.explorer");
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.openStartup", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Open Startup");
            if (!computer) return;

            const startupUri = toComputerUri(computer.id, "/startup.py");
            try {
                const document = await vscode.workspace.openTextDocument(startupUri);
                await vscode.window.showTextDocument(document, { preview: false });
            } catch {
                const template = getTemplate("startup");
                await client.writeFile(computer.id, "/startup.py", Buffer.from(template.content, "utf8"));
                treeProvider.refresh();
                const document = await vscode.workspace.openTextDocument(startupUri);
                await vscode.window.showTextDocument(document, { preview: false });
            }
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.createNewScript", async element => {
            const target = resolveUploadTarget(element) || await resolveCurrentUploadTarget();
            if (!target) return;

            await runWithErrorHandling("Create New Script", async () => {
                const templateChoice = await vscode.window.showQuickPick([
                    { label: "Blank Python file", key: "blank", fileName: "script.py", content: "import cc\n\n" },
                    ...templateItems()
                ], {
                    title: "Choose a CCPython template"
                });
                if (!templateChoice) return;

                const fileName = await vscode.window.showInputBox({
                    prompt: "New script file name",
                    value: templateChoice.fileName,
                    ignoreFocusOut: true
                });
                if (!fileName) return;

                const remotePath = joinRemotePath(target.remoteDirectory, fileName);
                const content = templateChoice.content || "import cc\n\n";
                await client.writeFile(target.computer.id, remotePath, Buffer.from(content, "utf8"));
                treeProvider.refresh();
                const document = await vscode.workspace.openTextDocument(toComputerUri(target.computer.id, remotePath));
                await vscode.window.showTextDocument(document, { preview: false });
            });
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

            if (isMidiName(entry.entry.name)) {
                await vscode.commands.executeCommand("ccpython.previewMidi", element);
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
                const dataUri = `data:${imageMimeType(entry.entry.name)};base64,${content.toString("base64")}`;
                const panel = vscode.window.createWebviewPanel(
                    "ccpythonImagePreview",
                    `${entry.entry.name} Preview`,
                    vscode.ViewColumn.Active,
                    { enableScripts: true }
                );

                panel.webview.html = renderImagePreviewHtml(entry, dataUri);
                panel.webview.onDidReceiveMessage(async message => {
                    await handleFilePreviewMessage(message, entry);
                });
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.inspectDataFile", async element => {
            const entry = resolveEntry(element);
            if (!entry || entry.entry.is_dir || !isInspectableDataName(entry.entry.name)) return;

            await runWithErrorHandling("Inspect Data File", async () => {
                const content = await client.readFile(entry.computer.id, entry.entry.path);
                const inspection = inspectDataFile(entry.entry.name, content);
                const panel = vscode.window.createWebviewPanel(
                    "ccpythonDataInspector",
                    `${entry.entry.name} Inspect`,
                    vscode.ViewColumn.Active,
                    {}
                );
                panel.webview.html = renderDataInspectorHtml(entry, inspection);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.previewMidi", async element => {
            const entry = resolveEntry(element);
            if (!entry || entry.entry.is_dir || !isMidiName(entry.entry.name)) return;

            await runWithErrorHandling("Preview MIDI", async () => {
                const content = await client.readFile(entry.computer.id, entry.entry.path);
                const info = inspectMidiFile(content);
                const panel = vscode.window.createWebviewPanel(
                    "ccpythonMidiPreview",
                    `${entry.entry.name} MIDI`,
                    vscode.ViewColumn.Active,
                    { enableScripts: true }
                );
                const dataUri = `data:audio/midi;base64,${content.toString("base64")}`;
                panel.webview.html = renderMidiInspectorHtml(entry, info, dataUri);
                panel.webview.onDidReceiveMessage(async message => {
                    await handleFilePreviewMessage(message, entry);
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
            const target = resolveUploadTarget(element) || await resolveCurrentUploadTarget();
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
                await uploadUrisToTarget(selectedFiles, target, { treeProvider, client, output });
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.uploadFolder", async element => {
            const target = resolveUploadTarget(element) || await resolveCurrentUploadTarget();
            if (!target) return;

            await runWithErrorHandling("Upload Folder", async () => {
                const selected = await vscode.window.showOpenDialog({
                    canSelectFiles: false,
                    canSelectFolders: true,
                    canSelectMany: false,
                    openLabel: "Upload Folder to CCPython Computer"
                });
                if (!selected || selected.length === 0) return;
                await uploadUrisToTarget(selected, target, { treeProvider, client, output });
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
        vscode.commands.registerCommand("ccpython.searchComputerFiles", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Search Computer Files");
            if (!computer) return;
            const entry = resolveEntry(element);
            const searchRoot = entry
                ? (entry.entry.is_dir ? entry.entry.path : path.posix.dirname(entry.entry.path))
                : "/";

            await runWithErrorHandling("Search Computer Files", async () => {
                const query = await vscode.window.showInputBox({
                    prompt: "Search query",
                    ignoreFocusOut: true
                });
                if (!query) return;

                const results = await client.search(computer.id, query, {
                    path: searchRoot,
                    limit: 100
                });
                if (results.length === 0) {
                    vscode.window.showInformationMessage(`No matches for "${query}" on ${computer.label || `computer ${computer.id}`}${searchRoot === "/" ? "" : ` under ${searchRoot}`}.`);
                    return;
                }

                const choice = await vscode.window.showQuickPick(
                    results.map(result => ({
                        label: result.path,
                        description: result.line ? `line ${result.line}` : result.kind,
                        detail: result.snippet || "",
                        result
                    })),
                    {
                        title: `Matches for "${query}"${searchRoot === "/" ? "" : ` in ${searchRoot}`}`
                    }
                );
                if (!choice) return;
                await openRemoteLocation(computer.id, choice.result.path, choice.result.line || 1);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.bindWorkspaceToCurrentComputer", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Bind Workspace");
            if (!computer) return;
            const uploadTarget = resolveUploadTarget(element);
            const defaultRemoteRoot = uploadTarget ? uploadTarget.remoteDirectory : "/";

            const folders = (vscode.workspace.workspaceFolders || []).filter(folder => folder.uri.scheme === "file");
            if (folders.length === 0) {
                vscode.window.showWarningMessage("Open a local workspace folder before enabling Sync on Save.");
                return;
            }

            const chosenFolder = folders.length === 1
                ? folders[0]
                : await vscode.window.showQuickPick(
                    folders.map(folder => ({
                        label: folder.name,
                        description: folder.uri.fsPath,
                        folder
                    })),
                    { title: "Choose a local workspace folder to sync" }
                ).then(choice => choice && choice.folder);

            if (!chosenFolder) return;

            const remoteRoot = await vscode.window.showInputBox({
                prompt: "Remote directory root for Sync on Save",
                value: defaultRemoteRoot,
                ignoreFocusOut: true
            });
            if (remoteRoot === undefined) return;

            state.syncBindings[chosenFolder.uri.fsPath] = {
                computerId: computer.id,
                remoteRoot: normalizeRemoteDirectory(remoteRoot)
            };
            await context.workspaceState.update("ccpython.syncBindings", state.syncBindings);
            vscode.window.showInformationMessage(`Sync on Save enabled for ${chosenFolder.name} -> ${computer.label || `computer ${computer.id}`}:${normalizeRemoteDirectory(remoteRoot)}`);
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.generateApiStubs", async () => {
            await runWithErrorHandling("Generate API Stubs", async () => {
                const workspaceFolder = await chooseWritableWorkspaceFolder();
                if (!workspaceFolder) {
                    vscode.window.showWarningMessage("Open a local workspace folder to generate CCPython API stubs.");
                    return;
                }

                const stubRoot = path.join(workspaceFolder.uri.fsPath, ".ccpython", "stubs");
                const stubFile = path.join(stubRoot, "cc.pyi");
                const settingsPath = path.join(workspaceFolder.uri.fsPath, ".vscode", "settings.json");

                await fs.mkdir(path.dirname(stubFile), { recursive: true });
                await fs.writeFile(stubFile, generateStubText(), "utf8");
                await ensurePythonExtraPath(settingsPath, stubRoot);

                const document = await vscode.workspace.openTextDocument(vscode.Uri.file(stubFile));
                await vscode.window.showTextDocument(document, { preview: false });
                vscode.window.showInformationMessage(`Generated CCPython stubs in ${stubFile}.`);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.runPythonFile", async element => {
            await runWithErrorHandling("Run Python File", async () => {
                const target = await resolveRunnableTarget(element);
                if (!target) return;

                if (target.localDocument) {
                    await syncLocalDocumentToRemote(target.localDocument, target.binding);
                }

                const response = await client.runPython(target.computer.id, target.remotePath, {
                    cwd: path.posix.dirname(target.remotePath)
                });
                treeProvider.refresh();
                await refreshCurrentComputerStatus();
                await showConsoleForComputer(target.computer);
                vscode.window.showInformationMessage(`Started ${response.process.program} on ${target.computer.label || `computer ${target.computer.id}`}.`);
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.stopPython", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Stop Python");
            if (!computer) return;

            await runWithErrorHandling("Stop Python", async () => {
                const runtime = await client.runtimeState(computer.id);
                let processId = null;
                if (Array.isArray(runtime.processes) && runtime.processes.length > 1) {
                    const choice = await vscode.window.showQuickPick([
                        { label: "All running processes", description: "", processId: null },
                        ...runtime.processes.map(process => ({
                            label: process.program || process.process_id,
                            description: `${process.state} ${process.process_id}`,
                            processId: process.process_id
                        }))
                    ], {
                        title: "Choose a process to stop"
                    });
                    if (!choice) return;
                    processId = choice.processId;
                } else if (runtime.processes && runtime.processes.length === 1) {
                    processId = runtime.processes[0].process_id;
                }

                await client.stopPython(computer.id, processId);
                await refreshCurrentComputerStatus();
                await pushConsoleSnapshot();
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.openConsole", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Open Console");
            if (!computer) return;
            await showConsoleForComputer(computer);
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.powerOnComputer", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Turn On Computer");
            if (!computer) return;
            await runWithErrorHandling(`Turn On Computer ${computer.id}`, async () => {
                await client.power(computer.id, "on");
                treeProvider.refresh();
                await refreshCurrentComputerStatus();
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.shutdownComputer", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Shut Down Computer");
            if (!computer) return;
            await runWithErrorHandling(`Shut Down Computer ${computer.id}`, async () => {
                await client.power(computer.id, "off");
                treeProvider.refresh();
                await refreshCurrentComputerStatus();
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand("ccpython.rebootComputer", async element => {
            const computer = resolveComputer(element) || await ensureCurrentComputer("Reboot Computer");
            if (!computer) return;
            await runWithErrorHandling(`Reboot Computer ${computer.id}`, async () => {
                await client.power(computer.id, "reboot");
                treeProvider.refresh();
                await refreshCurrentComputerStatus();
                await pushConsoleSnapshot();
            });
        })
    );

    context.subscriptions.push(
        vscode.workspace.onDidChangeConfiguration(async event => {
            if (event.affectsConfiguration("ccpythonBridge")) {
                treeProvider.refresh();
                await refreshBridgeStatus();
            }
        })
    );

    context.subscriptions.push(
        vscode.workspace.onDidSaveTextDocument(async document => {
            const binding = bindingForDocument(document);
            if (!binding) return;

            try {
                await syncLocalDocumentToRemote(document, binding, { quiet: true });
            } catch (error) {
                const message = error && error.message ? error.message : String(error);
                output.appendLine(`[sync:error] ${document.uri.fsPath}: ${message}`);
                vscode.window.showWarningMessage(`CCPython Sync on Save failed for ${path.basename(document.uri.fsPath)}: ${message}`);
            }
        })
    );

    context.subscriptions.push(new vscode.Disposable(() => stopConsolePolling()));
    output.appendLine("CCPython Bridge extension activated.");
    refreshBridgeStatus().catch(error => output.appendLine(`[activate] ${error && error.message ? error.message : String(error)}`));

    async function setCurrentComputer(computer) {
        state.currentComputerId = computer.id;
        await context.workspaceState.update("ccpython.currentComputerId", computer.id);
        await refreshCurrentComputerStatus();
        if (state.consolePanel && state.consolePanelComputerId === computer.id) {
            await pushConsoleSnapshot();
        }
    }

    async function ensureCurrentComputer(label) {
        if (state.currentComputerId) {
            try {
                const computer = await client.getComputer(state.currentComputerId);
                return computer;
            } catch (error) {
                output.appendLine(`[current-computer] ${label}: ${error && error.message ? error.message : String(error)}`);
            }
        }

        const computers = await client.listComputers();
        if (computers.length === 0) {
            vscode.window.showWarningMessage("No loaded CCPython computers were found.");
            return null;
        }

        const fallback = computers[0];
        await setCurrentComputer(fallback);
        return fallback;
    }

    async function resolveCurrentUploadTarget() {
        const computer = await ensureCurrentComputer("Upload");
        if (!computer) return null;
        return { computer, remoteDirectory: "/" };
    }

    async function showComputerAccess(computer) {
        const acl = await client.getAcl(computer.id);
        const owner = acl && acl.owner ? `${acl.owner.name} (${acl.owner.uuid})` : "unowned";
        const whitelist = acl && Array.isArray(acl.whitelist) && acl.whitelist.length > 0
            ? acl.whitelist.map(player => `${player.name} (${player.uuid})`).join("\n")
            : "none";

        output.appendLine(`[acl] computer ${computer.id} owner=${owner}`);
        output.appendLine(`[acl] whitelist:\n${whitelist}`);
        vscode.window.showInformationMessage(
            `${computer.label || `Computer ${computer.id}`}: owner ${owner}, shared with ${acl && Array.isArray(acl.whitelist) ? acl.whitelist.length : 0} player(s).`
        );
    }

    async function resolveRunnableTarget(element) {
        const entry = resolveEntry(element);
        if (entry && !entry.entry.is_dir) {
            if (!isRunnablePythonPath(entry.entry.path)) {
                vscode.window.showWarningMessage("Only Python files can be run through the CCPython runtime.");
                return null;
            }
            return {
                computer: entry.computer,
                remotePath: entry.entry.path,
                binding: null,
                localDocument: null
            };
        }

        const selectedEntry = resolveEntry(state.lastTreeSelection);
        if (selectedEntry && !selectedEntry.entry.is_dir) {
            if (!isRunnablePythonPath(selectedEntry.entry.path)) {
                vscode.window.showWarningMessage("The selected tree item is not a Python file.");
                return null;
            }
            await setCurrentComputer(selectedEntry.computer);
            return {
                computer: selectedEntry.computer,
                remotePath: selectedEntry.entry.path,
                binding: null,
                localDocument: null
            };
        }

        const activeEditor = vscode.window.activeTextEditor;
        if (!activeEditor) {
            const computer = await ensureCurrentComputer("Run Python File");
            if (!computer) return null;
            try {
                await client.statPath(computer.id, "/startup.py");
            } catch {
                vscode.window.showWarningMessage("Open or select a Python file first, or create /startup.py before using Run Active.");
                return null;
            }
            return {
                computer,
                remotePath: "/startup.py",
                binding: null,
                localDocument: null
            };
        }

        const document = activeEditor.document;
        if (document.uri.scheme === "ccpython") {
            const parsed = parseUri(document.uri);
            if (!isRunnablePythonPath(parsed.path)) {
                vscode.window.showWarningMessage("Only Python files can be run through the CCPython runtime.");
                return null;
            }
            const computer = await client.getComputer(parsed.computerId);
            await setCurrentComputer(computer);
            return {
                computer,
                remotePath: parsed.path,
                binding: null,
                localDocument: null
            };
        }

        if (document.uri.scheme === "file") {
            const binding = bindingForDocument(document);
            if (!binding) {
                vscode.window.showWarningMessage("This local file is not bound to a CCPython computer. Use 'Bind Workspace to Current Computer' first.");
                return null;
            }

            if (!isRunnablePythonPath(document.uri.fsPath)) {
                vscode.window.showWarningMessage("Only Python files can be run through the CCPython runtime.");
                return null;
            }

            const remotePath = localFileToRemotePath(document.uri.fsPath, binding);
            const computer = await client.getComputer(binding.computerId);
            await setCurrentComputer(computer);
            return {
                computer,
                remotePath,
                binding,
                localDocument: document
            };
        }

        vscode.window.showWarningMessage("Only local files or CCPython remote files can be run.");
        return null;
    }

    async function handleFilePreviewMessage(message, entry) {
        if (!message || typeof message.command !== "string") return;
        if (message.command === "openLocal") {
            const localUri = await materializeRemoteFileLocally(client, entry);
            await vscode.commands.executeCommand("vscode.open", localUri);
            return;
        }
        if (message.command === "download") {
            await vscode.commands.executeCommand("ccpython.downloadFile", entry);
        }
    }

    async function showConsoleForComputer(computer) {
        await setCurrentComputer(computer);

        if (!state.consolePanel) {
            state.consolePanel = vscode.window.createWebviewPanel(
                "ccpythonConsole",
                `CCPython Console #${computer.id}`,
                vscode.ViewColumn.Beside,
                { enableScripts: true }
            );
            state.consolePanel.webview.html = renderConsoleHtml(computer);
            state.consolePanel.onDidDispose(() => {
                stopConsolePolling();
                state.consolePanel = null;
                state.consolePanelComputerId = null;
            }, null, context.subscriptions);
            state.consolePanel.webview.onDidReceiveMessage(async message => {
                if (!message || typeof message.command !== "string") return;
                if (message.command === "refresh") {
                    await pushConsoleSnapshot();
                    return;
                }
                if (message.command === "run") {
                    await vscode.commands.executeCommand("ccpython.runPythonFile");
                    return;
                }
                if (message.command === "stop") {
                    await vscode.commands.executeCommand("ccpython.stopPython");
                    return;
                }
                if (message.command === "reboot") {
                    await vscode.commands.executeCommand("ccpython.rebootComputer");
                    return;
                }
                if (message.command === "openPath" && message.path) {
                    await openRemoteLocation(state.consolePanelComputerId, message.path, Number(message.line || 1));
                    return;
                }
                if (message.command === "terminalChar" && message.text) {
                    await client.sendTerminalChar(state.consolePanelComputerId, message.text);
                    setTimeout(() => pushConsoleSnapshot().catch(() => {}), 75);
                    return;
                }
                if (message.command === "terminalPaste" && message.text) {
                    await client.sendTerminalPaste(state.consolePanelComputerId, message.text);
                    setTimeout(() => pushConsoleSnapshot().catch(() => {}), 75);
                    return;
                }
                if (message.command === "terminalKey" && Number.isFinite(Number(message.key))) {
                    await client.sendTerminalKey(state.consolePanelComputerId, Number(message.key));
                    setTimeout(() => pushConsoleSnapshot().catch(() => {}), 75);
                    return;
                }
                if (message.command === "terminalTerminate") {
                    await client.sendTerminalTerminate(state.consolePanelComputerId);
                    setTimeout(() => pushConsoleSnapshot().catch(() => {}), 75);
                }
            });
        } else {
            state.consolePanel.reveal(vscode.ViewColumn.Beside);
            state.consolePanel.title = `CCPython Console #${computer.id}`;
        }

        state.consolePanelComputerId = computer.id;
        await pushConsoleSnapshot();
        startConsolePolling();
    }

    async function pushConsoleSnapshot() {
        if (!state.consolePanel || !state.consolePanelComputerId) return;
        const [runtime, terminal, computer] = await Promise.all([
            client.runtimeState(state.consolePanelComputerId),
            client.terminalState(state.consolePanelComputerId),
            client.getComputer(state.consolePanelComputerId)
        ]);

        state.consolePanel.title = `CCPython Console #${computer.id}`;
        await state.consolePanel.webview.postMessage({
            command: "snapshot",
            payload: {
                computer,
                runtime,
                terminal
            }
        });
    }

    function startConsolePolling() {
        if (state.consolePollTimer) clearInterval(state.consolePollTimer);
        state.consolePollTimer = setInterval(() => {
            pushConsoleSnapshot().catch(error => output.appendLine(`[console] ${error && error.message ? error.message : String(error)}`));
        }, 1000);
    }

    function stopConsolePolling() {
        if (state.consolePollTimer) {
            clearInterval(state.consolePollTimer);
            state.consolePollTimer = null;
        }
    }

    function bindingForDocument(document) {
        if (!document || document.uri.scheme !== "file") return null;
        const filePath = document.uri.fsPath;
        if (isIgnoredSyncPath(filePath)) return null;
        const candidates = Object.entries(state.syncBindings)
            .filter(([workspacePath]) => isInsideWorkspace(filePath, workspacePath))
            .sort((left, right) => right[0].length - left[0].length);

        if (candidates.length === 0) return null;
        return {
            workspacePath: candidates[0][0],
            ...candidates[0][1]
        };
    }

    async function syncLocalDocumentToRemote(document, binding, options = {}) {
        const remotePath = localFileToRemotePath(document.uri.fsPath, binding);
        const content = Buffer.from(document.getText(), "utf8");
        await ensureRemoteDirectory(client, binding.computerId, path.posix.dirname(remotePath));
        await client.writeFile(binding.computerId, remotePath, content);
        if (!options.quiet) {
            treeProvider.refresh();
            output.appendLine(`[sync] ${document.uri.fsPath} -> ${binding.computerId}:${remotePath}`);
        }
    }
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

function normalizeRemoteDirectory(remoteDirectory) {
    if (!remoteDirectory || remoteDirectory === "/") return "/";
    return remoteDirectory.startsWith("/") ? remoteDirectory.replace(/\/+$/, "") : `/${remoteDirectory.replace(/\/+$/, "")}`;
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

async function uploadUrisToTarget(uris, target, options) {
    const flattened = [];
    for (const uri of uris) {
        const stat = await fs.stat(uri.fsPath);
        if (stat.isDirectory()) {
            const folderName = path.basename(uri.fsPath);
            await collectLocalDirectory(uri.fsPath, joinRemotePath(target.remoteDirectory, folderName), flattened);
        } else {
            flattened.push({
                localPath: uri.fsPath,
                remotePath: joinRemotePath(target.remoteDirectory, path.basename(uri.fsPath))
            });
        }
    }

    if (flattened.length === 0) return;

    await vscode.window.withProgress({
        location: vscode.ProgressLocation.Notification,
        title: `Uploading ${flattened.length} item(s)`,
        cancellable: false
    }, async progress => {
        let completed = 0;
        for (const item of flattened) {
            await ensureRemoteDirectory(options.client, target.computer.id, path.posix.dirname(item.remotePath));
            const content = await fs.readFile(item.localPath);
            await options.client.writeFile(target.computer.id, item.remotePath, content);
            completed += 1;
            progress.report({
                increment: 100 / flattened.length,
                message: `${completed}/${flattened.length}: ${path.basename(item.localPath)}`
            });
        }
    });

    options.treeProvider.refresh();
    options.output.appendLine(`[upload] ${flattened.length} item(s) -> ${target.computer.id}:${target.remoteDirectory}`);
    vscode.window.showInformationMessage(`Uploaded ${flattened.length} item(s) to ${target.computer.label || `computer ${target.computer.id}`}.`);
}

async function collectLocalDirectory(localDirectory, remoteDirectory, collected) {
    const entries = await fs.readdir(localDirectory, { withFileTypes: true });
    for (const entry of entries) {
        const localPath = path.join(localDirectory, entry.name);
        const remotePath = joinRemotePath(remoteDirectory, entry.name);
        if (entry.isDirectory()) {
            await collectLocalDirectory(localPath, remotePath, collected);
        } else {
            collected.push({ localPath, remotePath });
        }
    }
}

async function ensureRemoteDirectory(client, computerId, remoteDirectory) {
    const parts = normalizeRemoteDirectory(remoteDirectory).split("/").filter(Boolean);
    let current = "/";
    for (const part of parts) {
        current = joinRemotePath(current, part);
        try {
            await client.createDirectory(computerId, current);
        } catch (error) {
            if (!error || !error.message || !/(exists|directory)/i.test(error.message)) {
                throw error;
            }
        }
    }
}

async function openRemoteLocation(computerId, remotePath, lineNumber) {
    if (!computerId || !remotePath || !remotePath.startsWith("/")) return;
    const document = await vscode.workspace.openTextDocument(toComputerUri(computerId, remotePath));
    const editor = await vscode.window.showTextDocument(document, { preview: false });
    const line = Math.max(0, Number(lineNumber || 1) - 1);
    const position = new vscode.Position(line, 0);
    editor.selection = new vscode.Selection(position, position);
    editor.revealRange(new vscode.Range(position, position), vscode.TextEditorRevealType.InCenter);
}

function localFileToRemotePath(filePath, binding) {
    const relative = path.relative(binding.workspacePath, filePath).split(path.sep).join("/");
    return joinRemotePath(binding.remoteRoot, relative);
}

function isRunnablePythonPath(filePath) {
    return typeof filePath === "string" && filePath.toLowerCase().endsWith(".py");
}

function isInsideWorkspace(filePath, workspacePath) {
    const relative = path.relative(workspacePath, filePath);
    return !!relative && !relative.startsWith("..") && !path.isAbsolute(relative);
}

function isIgnoredSyncPath(filePath) {
    const segments = filePath.split(path.sep).map(segment => segment.toLowerCase());
    return segments.includes(".git") || segments.includes(".vscode") || segments.includes(".ccpython") || segments.includes("node_modules");
}

async function chooseWritableWorkspaceFolder() {
    const folders = (vscode.workspace.workspaceFolders || []).filter(folder => folder.uri.scheme === "file");
    if (folders.length === 0) return null;
    if (folders.length === 1) return folders[0];

    const choice = await vscode.window.showQuickPick(
        folders.map(folder => ({
            label: folder.name,
            description: folder.uri.fsPath,
            folder
        })),
        {
            title: "Choose a local workspace folder"
        }
    );
    return choice ? choice.folder : null;
}

async function ensurePythonExtraPath(settingsPath, stubRoot) {
    await fs.mkdir(path.dirname(settingsPath), { recursive: true });

    let settings = {};
    try {
        settings = JSON.parse(await fs.readFile(settingsPath, "utf8"));
    } catch {
        settings = {};
    }

    const extraPaths = Array.isArray(settings["python.analysis.extraPaths"])
        ? settings["python.analysis.extraPaths"]
        : [];
    if (!extraPaths.includes(stubRoot)) {
        extraPaths.push(stubRoot);
        settings["python.analysis.extraPaths"] = extraPaths;
    }

    await fs.writeFile(settingsPath, JSON.stringify(settings, null, 2) + "\n", "utf8");
}

async function pickOnlinePlayer(client, options = {}) {
    const players = await client.listPlayers();
    const excluded = options.excludeUuids || new Set();
    const choices = players
        .filter(player => !excluded.has(player.uuid))
        .map(player => ({
            label: player.name,
            description: player.uuid,
            player
        }));

    if (choices.length === 0) {
        vscode.window.showWarningMessage("No matching online players are available right now.");
        return null;
    }

    const choice = await vscode.window.showQuickPick(choices, {
        title: options.title || "Choose an online player"
    });
    return choice ? choice.player : null;
}

function describeIdentity(identity) {
    if (!identity) return "unknown";
    if (identity.admin) return identity.loopback ? "admin (localhost)" : "admin";
    if (identity.player_name) return `player ${identity.player_name}`;
    if (identity.player_uuid) return `player ${identity.player_uuid}`;
    return identity.source || "unknown";
}

function renderImagePreviewHtml(entry, dataUri) {
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
        <span class="meta">${escapeHtml(computerLabel)} - ${escapeHtml(entry.entry.path)}</span>
    </div>
    <div class="frame">
        <img src="${dataUri}" alt="${escapeHtml(entry.entry.name)}" />
    </div>
    <script>
        const vscode = acquireVsCodeApi();
        document.getElementById('openLocal').addEventListener('click', () => vscode.postMessage({ command: 'openLocal' }));
        document.getElementById('download').addEventListener('click', () => vscode.postMessage({ command: 'download' }));
    </script>
</body>
</html>`;
}

function renderConsoleHtml(computer) {
    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <style>
    body { font-family: var(--vscode-font-family); background: var(--vscode-editor-background); color: var(--vscode-foreground); margin: 0; padding: 16px; }
    .toolbar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; align-items: center; }
    button { border: 1px solid var(--vscode-button-border, transparent); background: var(--vscode-button-background); color: var(--vscode-button-foreground); padding: 6px 12px; border-radius: 6px; cursor: pointer; }
    button.secondary { background: var(--vscode-button-secondaryBackground, transparent); color: var(--vscode-button-secondaryForeground, var(--vscode-foreground)); }
    .panel { border: 1px solid var(--vscode-panel-border); border-radius: 8px; padding: 12px; margin-bottom: 12px; }
    pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: var(--vscode-editor-font-family, monospace); }
    .status-line { opacity: 0.85; }
    .traceback a { color: var(--vscode-textLink-foreground); cursor: pointer; text-decoration: underline; }
    .muted { opacity: 0.65; }
    .terminal-surface { outline: none; min-height: 220px; cursor: text; }
    .terminal-surface:focus { box-shadow: 0 0 0 1px var(--vscode-focusBorder); border-radius: 6px; }
    .terminal-hint { margin-top: 8px; font-size: 12px; opacity: 0.7; }
    .input-row { display: flex; gap: 8px; margin-top: 12px; align-items: center; }
    .input-row input {
      flex: 1;
      min-width: 0;
      background: var(--vscode-input-background);
      color: var(--vscode-input-foreground);
      border: 1px solid var(--vscode-input-border, var(--vscode-panel-border));
      border-radius: 6px;
      padding: 8px 10px;
    }
  </style>
</head>
<body>
  <div class="toolbar">
    <button id="run">Run Active</button>
    <button id="stop" class="secondary">Stop</button>
    <button id="reboot" class="secondary">Reboot</button>
    <button id="refresh" class="secondary">Refresh</button>
    <button id="terminate" class="secondary">Ctrl+T</button>
    <span class="status-line" id="headline">Computer ${computer.id}</span>
  </div>
  <div class="panel">
    <strong>Runtime</strong>
    <div id="runtime" class="muted">Waiting for snapshot...</div>
  </div>
  <div class="panel traceback">
    <strong>Traceback</strong>
    <div id="traceback" class="muted">No traceback.</div>
  </div>
  <div class="panel">
    <strong>Terminal</strong>
    <pre id="terminal" class="terminal-surface" tabindex="0">Waiting for terminal snapshot...</pre>
    <div class="terminal-hint">Click the terminal and type, or use the input box below for whole-line commands.</div>
    <div class="input-row">
      <input id="lineInput" type="text" placeholder="Type a command and press Enter" />
      <button id="sendLine">Send</button>
    </div>
  </div>
  <script>
    const vscode = acquireVsCodeApi();
    const terminal = document.getElementById('terminal');
    const runtime = document.getElementById('runtime');
    const headline = document.getElementById('headline');
    const traceback = document.getElementById('traceback');
    const lineInput = document.getElementById('lineInput');

    const KEY_MAP = {
      Enter: 257,
      Tab: 258,
      Backspace: 259,
      Delete: 261,
      ArrowRight: 262,
      ArrowLeft: 263,
      ArrowDown: 264,
      ArrowUp: 265,
      Home: 268,
      End: 269
    };

    document.getElementById('run').addEventListener('click', () => vscode.postMessage({ command: 'run' }));
    document.getElementById('stop').addEventListener('click', () => vscode.postMessage({ command: 'stop' }));
    document.getElementById('reboot').addEventListener('click', () => vscode.postMessage({ command: 'reboot' }));
    document.getElementById('refresh').addEventListener('click', () => vscode.postMessage({ command: 'refresh' }));
    document.getElementById('terminate').addEventListener('click', () => vscode.postMessage({ command: 'terminalTerminate' }));

    function escapeHtml(value) {
      return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
    }

    function renderTraceback(tracebackText) {
      if (!tracebackText) {
        traceback.innerHTML = '<span class="muted">No traceback.</span>';
        return;
      }

      const lines = tracebackText.split(/\\r?\\n/).map(line => {
        const match = /File\\s+\\"([^\\"]+)\\",\\s+line\\s+(\\d+)/.exec(line);
        if (!match || !match[1].startsWith('/')) return escapeHtml(line);
        return escapeHtml(line).replace(
          escapeHtml(match[0]),
          '<a data-path="' + escapeHtml(match[1]) + '" data-line="' + escapeHtml(match[2]) + '">' + escapeHtml(match[0]) + '</a>'
        );
      });

      traceback.innerHTML = lines.join('<br/>');
      traceback.querySelectorAll('a[data-path]').forEach(anchor => {
        anchor.addEventListener('click', () => {
          vscode.postMessage({
            command: 'openPath',
            path: anchor.getAttribute('data-path'),
            line: Number(anchor.getAttribute('data-line'))
          });
        });
      });
    }

    window.addEventListener('message', event => {
      const payload = event.data && event.data.payload;
      if (!payload) return;

      const remoteComputer = payload.computer || {};
      const runtimeState = payload.runtime || {};
      const terminalState = payload.terminal || {};
      const processes = Array.isArray(runtimeState.processes) ? runtimeState.processes : [];
      const lastProcess = runtimeState.last_process || null;

      headline.textContent = (remoteComputer.label || ('Computer ' + remoteComputer.id)) + ' - ' + (runtimeState.status || 'idle');
      runtime.textContent = processes.length > 0
        ? processes.map(process => process.program + ' - ' + process.state + ' - ' + process.process_id).join('\\n')
        : (lastProcess ? ('Last: ' + lastProcess.program + ' - ' + lastProcess.state) : 'Idle');
      terminal.textContent = terminalState.available
        ? (terminalState.lines || []).map(line => line.text || '').join('\\n')
        : 'No terminal snapshot available yet.';
      renderTraceback(lastProcess && lastProcess.traceback ? lastProcess.traceback : null);
    });

    function sendCurrentLine() {
      const text = lineInput.value;
      if (!text) return;
      vscode.postMessage({ command: 'terminalPaste', text });
      vscode.postMessage({ command: 'terminalKey', key: KEY_MAP.Enter });
      lineInput.value = '';
      terminal.focus();
    }

    document.getElementById('sendLine').addEventListener('click', sendCurrentLine);
    lineInput.addEventListener('keydown', event => {
      if (event.key === 'Enter') {
        event.preventDefault();
        sendCurrentLine();
      }
    });

    terminal.addEventListener('click', () => terminal.focus());
    terminal.addEventListener('keydown', event => {
      if (event.ctrlKey && (event.key === 't' || event.key === 'T')) {
        event.preventDefault();
        vscode.postMessage({ command: 'terminalTerminate' });
        return;
      }

      if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
        event.preventDefault();
        vscode.postMessage({ command: 'terminalChar', text: event.key });
        return;
      }

      const mapped = KEY_MAP[event.key];
      if (mapped) {
        event.preventDefault();
        vscode.postMessage({ command: 'terminalKey', key: mapped });
      }
    });

    terminal.addEventListener('paste', event => {
      const text = event.clipboardData && event.clipboardData.getData('text/plain');
      if (!text) return;
      event.preventDefault();
      vscode.postMessage({ command: 'terminalPaste', text });
    });
  </script>
</body>
</html>`;
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
