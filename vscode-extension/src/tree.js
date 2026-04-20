"use strict";

const vscode = require("vscode");
const { toComputerUri } = require("./fileSystemProvider");

class ComputerNode {
    constructor(computer) {
        this.computer = computer;
    }
}

class EntryNode {
    constructor(computer, entry) {
        this.computer = computer;
        this.entry = entry;
    }
}

class CCPythonTreeDragAndDropController {
    constructor(onDropExternalFiles) {
        this.onDropExternalFiles = onDropExternalFiles;
        this.dragMimeTypes = [];
        this.dropMimeTypes = ["text/uri-list"];
    }

    async handleDrop(target, dataTransfer) {
        const item = dataTransfer.get("text/uri-list");
        if (!item) return;

        const raw = await item.asString();
        const uris = raw
            .split(/\r?\n/)
            .map(line => line.trim())
            .filter(line => line && !line.startsWith("#"))
            .map(line => vscode.Uri.parse(line));

        if (uris.length > 0) {
            await this.onDropExternalFiles(target, uris);
        }
    }

    handleDrag() {
        return;
    }
}

class CCPythonTreeProvider {
    constructor(client, output) {
        this.client = client;
        this.output = output;
        this.emitter = new vscode.EventEmitter();
    }

    get onDidChangeTreeData() {
        return this.emitter.event;
    }

    refresh() {
        this.emitter.fire(undefined);
    }

    async getChildren(element) {
        if (!element) {
            const computers = await this.client.listComputers();
            return computers.map(computer => new ComputerNode(computer));
        }

        if (element instanceof ComputerNode) {
            const entries = await this.client.listFiles(element.computer.id, "/");
            return entries.map(entry => new EntryNode(element.computer, entry));
        }

        if (element instanceof EntryNode && element.entry.is_dir) {
            const entries = await this.client.listFiles(element.computer.id, element.entry.path);
            return entries.map(entry => new EntryNode(element.computer, entry));
        }

        return [];
    }

    getTreeItem(element) {
        if (element instanceof ComputerNode) {
            const label = element.computer.label || `Computer ${element.computer.id}`;
            const item = new vscode.TreeItem(label, vscode.TreeItemCollapsibleState.Collapsed);
            item.description = `#${element.computer.id}${element.computer.on ? "" : " (off)"}`;
            item.contextValue = "computer";
            item.iconPath = new vscode.ThemeIcon(element.computer.on ? "vm-active" : "vm-outline");
            item.tooltip = new vscode.MarkdownString([
                `**Computer ${element.computer.id}**`,
                "",
                `Label: ${element.computer.label || "(none)"}`,
                `State: ${element.computer.on ? "on" : "off"}`,
                `Family: ${element.computer.family || "unknown"}`,
                `Dimension: ${element.computer.dimension}`,
                `Position: ${element.computer.position.x}, ${element.computer.position.y}, ${element.computer.position.z}`,
                `Python processes: ${element.computer.python_processes}`
            ].join("\n"));
            return item;
        }

        const isDirectory = !!element.entry.is_dir;
        const item = new vscode.TreeItem(
            element.entry.name,
            isDirectory ? vscode.TreeItemCollapsibleState.Collapsed : vscode.TreeItemCollapsibleState.None
        );
        const contextValue = resolveContextValue(element.entry.name, isDirectory);
        item.contextValue = contextValue;
        item.resourceUri = toComputerUri(element.computer.id, element.entry.path);
        item.iconPath = new vscode.ThemeIcon(iconForContext(contextValue));
        item.description = isDirectory ? "" : formatSize(element.entry.size || 0);
        if (!isDirectory) {
            item.command = {
                command: "ccpython.openFile",
                title: "Open File",
                arguments: [element]
            };
        }
        return item;
    }
}

function formatSize(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function isPreviewableImageName(fileName) {
    const extension = fileName.includes(".") ? fileName.split(".").pop().toLowerCase() : "";
    return extension === "png" || extension === "jpg" || extension === "jpeg" || extension === "gif" || extension === "webp";
}

function isMidiName(fileName) {
    const extension = fileName.includes(".") ? fileName.split(".").pop().toLowerCase() : "";
    return extension === "mid" || extension === "midi";
}

function isInspectableDataName(fileName) {
    const extension = fileName.includes(".") ? fileName.split(".").pop().toLowerCase() : "";
    return extension === "json" || extension === "toml" || extension === "yaml" || extension === "yml";
}

function resolveContextValue(fileName, isDirectory) {
    if (isDirectory) return "directory";
    if (isPreviewableImageName(fileName)) return "imageFile";
    if (isMidiName(fileName)) return "midiFile";
    if (isInspectableDataName(fileName)) return "dataFile";
    return "file";
}

function iconForContext(contextValue) {
    switch (contextValue) {
        case "directory":
            return "folder";
        case "imageFile":
            return "file-media";
        case "midiFile":
            return "file-media";
        case "dataFile":
            return "symbol-object";
        default:
            return "file";
    }
}

module.exports = {
    CCPythonTreeProvider,
    CCPythonTreeDragAndDropController,
    ComputerNode,
    EntryNode
};
