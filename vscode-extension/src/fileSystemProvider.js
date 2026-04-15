"use strict";

const vscode = require("vscode");
const { normalizePath } = require("./bridgeClient");

class CCPythonFileSystemProvider {
    constructor(client, output) {
        this.client = client;
        this.output = output;
        this.emitter = new vscode.EventEmitter();
        this.metadata = new Map();
    }

    get onDidChangeFile() {
        return this.emitter.event;
    }

    watch() {
        return new vscode.Disposable(() => {});
    }

    stat(uri) {
        return this.withErrorContext(uri, async () => {
            const key = this.cacheKey(uri);
            if (this.metadata.has(key)) return this.metadata.get(key);

            const { computerId, path } = parseUri(uri);
            const stat = await this.client.statPath(computerId, path);
            this.metadata.set(key, stat);
            return stat;
        });
    }

    readDirectory(uri) {
        return this.withErrorContext(uri, async () => {
            const { computerId, path } = parseUri(uri);
            const entries = await this.client.listFiles(computerId, path);
            const now = Date.now();
            const result = [];
            for (const entry of entries) {
                const type = entry.is_dir ? vscode.FileType.Directory : vscode.FileType.File;
                const childUri = toComputerUri(computerId, entry.path);
                this.metadata.set(this.cacheKey(childUri), {
                    type,
                    ctime: entry.created || now,
                    mtime: entry.modified || now,
                    size: entry.size || 0
                });
                result.push([entry.name, type]);
            }
            this.metadata.set(this.cacheKey(uri), {
                type: vscode.FileType.Directory,
                ctime: now,
                mtime: now,
                size: entries.length
            });
            return result;
        });
    }

    readFile(uri) {
        return this.withErrorContext(uri, async () => {
            const { computerId, path } = parseUri(uri);
            const content = await this.client.readFile(computerId, path);
            this.metadata.set(this.cacheKey(uri), {
                type: vscode.FileType.File,
                ctime: 0,
                mtime: Date.now(),
                size: content.length
            });
            return content;
        });
    }

    writeFile(uri, content, options) {
        return this.withErrorContext(uri, async () => {
            const { computerId, path } = parseUri(uri);

            let exists = true;
            try {
                await this.stat(uri);
            } catch {
                exists = false;
            }

            if (!exists && !options.create) {
                throw vscode.FileSystemError.FileNotFound(uri);
            }
            if (exists && options.create && !options.overwrite) {
                throw vscode.FileSystemError.FileExists(uri);
            }

            await this.client.writeFile(computerId, path, Buffer.from(content));
            const now = Date.now();
            this.metadata.set(this.cacheKey(uri), {
                type: vscode.FileType.File,
                ctime: now,
                mtime: now,
                size: content.length
            });
            this.fire([
                { type: exists ? vscode.FileChangeType.Changed : vscode.FileChangeType.Created, uri }
            ]);
        });
    }

    createDirectory(uri) {
        return this.withErrorContext(uri, async () => {
            const { computerId, path } = parseUri(uri);
            await this.client.createDirectory(computerId, path);
            const now = Date.now();
            this.metadata.set(this.cacheKey(uri), {
                type: vscode.FileType.Directory,
                ctime: now,
                mtime: now,
                size: 0
            });
            this.fire([{ type: vscode.FileChangeType.Created, uri }]);
        });
    }

    delete(uri) {
        return this.withErrorContext(uri, async () => {
            const { computerId, path } = parseUri(uri);
            await this.client.deleteFile(computerId, path);
            this.metadata.delete(this.cacheKey(uri));
            this.fire([{ type: vscode.FileChangeType.Deleted, uri }]);
        });
    }

    rename(oldUri, newUri, options) {
        return this.withErrorContext(oldUri, async () => {
            const oldInfo = parseUri(oldUri);
            const newInfo = parseUri(newUri);
            if (oldInfo.computerId !== newInfo.computerId) {
                throw new Error("Cross-computer rename is not supported.");
            }

            if (!options.overwrite) {
                try {
                    await this.stat(newUri);
                    throw vscode.FileSystemError.FileExists(newUri);
                } catch (error) {
                    if (isFileExistsError(error)) {
                        throw error;
                    }
                }
            }

            await this.client.move(oldInfo.computerId, oldInfo.path, newInfo.path);
            this.metadata.delete(this.cacheKey(oldUri));
            this.fire([
                { type: vscode.FileChangeType.Deleted, uri: oldUri },
                { type: vscode.FileChangeType.Created, uri: newUri }
            ]);
        });
    }

    cacheKey(uri) {
        return `${uri.authority}${uri.path}`;
    }

    fire(changes) {
        this.emitter.fire(changes);
    }

    async withErrorContext(uri, operation) {
        try {
            return await operation();
        } catch (error) {
            this.output.appendLine(`[fs] ${uri.toString()} -> ${error && error.message ? error.message : String(error)}`);
            if (error instanceof vscode.FileSystemError) throw error;

            if (error && typeof error.message === "string" && error.message.includes("does not exist")) {
                throw vscode.FileSystemError.FileNotFound(uri);
            }
            if (error && typeof error.message === "string" && error.message.includes("is a directory")) {
                throw vscode.FileSystemError.FileIsADirectory(uri);
            }
            if (error && typeof error.message === "string" && error.message.includes("is not a directory")) {
                throw vscode.FileSystemError.FileNotADirectory(uri);
            }

            throw error;
        }
    }
}

function isFileExistsError(error) {
    return error instanceof vscode.FileSystemError && /File exists/i.test(error.message || "");
}

function parseUri(uri) {
    const computerId = Number.parseInt(uri.authority, 10);
    if (!Number.isFinite(computerId)) {
        throw new Error(`Invalid CCPython URI authority '${uri.authority}'.`);
    }

    return {
        computerId,
        path: normalizePath(uri.path || "/")
    };
}

function toComputerUri(computerId, path) {
    return vscode.Uri.from({
        scheme: "ccpython",
        authority: String(computerId),
        path: normalizePath(path)
    });
}

module.exports = {
    CCPythonFileSystemProvider,
    parseUri,
    toComputerUri
};
