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
        const isImageFile = !isDirectory && isPreviewableImageName(element.entry.name);
        item.contextValue = isDirectory ? "directory" : (isImageFile ? "imageFile" : "file");
        item.resourceUri = toComputerUri(element.computer.id, element.entry.path);
        item.iconPath = new vscode.ThemeIcon(isDirectory ? "folder" : (isImageFile ? "file-media" : "file"));
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
    return extension === "png" || extension === "jpg" || extension === "jpeg";
}

module.exports = {
    CCPythonTreeProvider,
    ComputerNode,
    EntryNode
};
