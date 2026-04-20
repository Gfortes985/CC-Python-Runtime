"use strict";

const vscode = require("vscode");

class BridgeClient {
    constructor(output) {
        this.output = output;
    }

    baseUrl() {
        return this.configuration().get("baseUrl", "http://127.0.0.1:26780/ccpython/bridge/v1").replace(/\/+$/, "");
    }

    authToken() {
        return this.configuration().get("authToken", "").trim();
    }

    configuration() {
        return vscode.workspace.getConfiguration("ccpythonBridge");
    }

    async ping() {
        return this.request("GET", "/ping");
    }

    async authStatus() {
        return this.request("GET", "/auth/status");
    }

    async startPairing(label = "VS Code", player = null) {
        return this.request("POST", "/auth/pair/start", {
            query: { label, player }
        });
    }

    async completePairing(code, label = "VS Code") {
        return this.request("POST", "/auth/pair/complete", {
            query: { code, label }
        });
    }

    async listComputers() {
        const response = await this.request("GET", "/computers");
        return response.computers || [];
    }

    async listPlayers() {
        const response = await this.request("GET", "/players");
        return response.players || [];
    }

    async getComputer(computerId) {
        const response = await this.request("GET", `/computers/${computerId}`);
        return response.computer;
    }

    async getAcl(computerId) {
        const response = await this.request("GET", `/computers/${computerId}/acl`);
        return response.acl || null;
    }

    async claimAcl(computerId, player = null) {
        return this.request("POST", `/computers/${computerId}/acl/claim`, {
            query: player ? { player } : {}
        });
    }

    async grantAcl(computerId, player) {
        return this.request("POST", `/computers/${computerId}/acl/grant`, {
            query: { player }
        });
    }

    async revokeAcl(computerId, player) {
        return this.request("POST", `/computers/${computerId}/acl/revoke`, {
            query: { player }
        });
    }

    async setOwnerAcl(computerId, player) {
        return this.request("POST", `/computers/${computerId}/acl/set-owner`, {
            query: { player }
        });
    }

    async listFiles(computerId, path) {
        const response = await this.request("GET", `/computers/${computerId}/files`, {
            query: { path }
        });
        return response.entries || [];
    }

    async readFile(computerId, path) {
        const response = await this.request("GET", `/computers/${computerId}/file`, {
            query: { path, encoding: "base64" }
        });
        return Buffer.from(response.content || "", "base64");
    }

    async readFileMeta(computerId, path) {
        const response = await this.request("GET", `/computers/${computerId}/file`, {
            query: { path, encoding: "base64" }
        });
        return {
            size: response.size || 0,
            content: Buffer.from(response.content || "", "base64")
        };
    }

    async writeFile(computerId, path, content) {
        return this.request("PUT", `/computers/${computerId}/file`, {
            query: { path, encoding: "raw" },
            body: Buffer.isBuffer(content) ? content : Buffer.from(content)
        });
    }

    async deleteFile(computerId, path) {
        return this.request("DELETE", `/computers/${computerId}/file`, {
            query: { path }
        });
    }

    async createDirectory(computerId, path) {
        return this.request("POST", `/computers/${computerId}/mkdir`, {
            query: { path }
        });
    }

    async move(computerId, from, to) {
        return this.request("POST", `/computers/${computerId}/move`, {
            query: { from, to }
        });
    }

    async power(computerId, action) {
        return this.request("POST", `/computers/${computerId}/power/${action}`);
    }

    async runtimeState(computerId) {
        return this.request("GET", `/computers/${computerId}/runtime`);
    }

    async terminalState(computerId) {
        return this.request("GET", `/computers/${computerId}/terminal`);
    }

    async sendTerminalPaste(computerId, text) {
        return this.request("POST", `/computers/${computerId}/terminal/input`, {
            query: { kind: "paste" },
            body: Buffer.from(text, "utf8")
        });
    }

    async sendTerminalChar(computerId, text) {
        return this.request("POST", `/computers/${computerId}/terminal/input`, {
            query: { kind: "char" },
            body: Buffer.from(text, "utf8")
        });
    }

    async sendTerminalKey(computerId, key) {
        return this.request("POST", `/computers/${computerId}/terminal/input`, {
            query: { kind: "key", key }
        });
    }

    async sendTerminalTerminate(computerId) {
        return this.request("POST", `/computers/${computerId}/terminal/input`, {
            query: { kind: "terminate" }
        });
    }

    async runPython(computerId, program, options = {}) {
        const query = {
            cwd: options.cwd || "/",
            interactive: options.interactive ? "true" : "false"
        };
        if (program) query.program = program;
        return this.request("POST", `/computers/${computerId}/python/run`, { query });
    }

    async stopPython(computerId, processId) {
        return this.request("POST", `/computers/${computerId}/python/stop`, {
            query: processId ? { process_id: processId } : {}
        });
    }

    async search(computerId, query, options = {}) {
        const response = await this.request("GET", `/computers/${computerId}/search`, {
            query: {
                query,
                path: options.path || "/",
                limit: options.limit || 100
            }
        });
        return response.results || [];
    }

    async statPath(computerId, path) {
        const normalized = normalizePath(path);
        if (normalized === "/") {
            return {
                type: vscode.FileType.Directory,
                ctime: 0,
                mtime: 0,
                size: 0
            };
        }

        try {
            const file = await this.readFileMeta(computerId, normalized);
            return {
                type: vscode.FileType.File,
                ctime: 0,
                mtime: 0,
                size: file.size || file.content.length
            };
        } catch (error) {
            if (!isBridgeDirectory(error)) throw error;
        }

        const entries = await this.listFiles(computerId, normalized);
        return {
            type: vscode.FileType.Directory,
            ctime: 0,
            mtime: 0,
            size: Array.isArray(entries) ? entries.length : 0
        };
    }

    async request(method, route, options = {}) {
        const baseUrls = candidateBaseUrls(this.baseUrl());
        let lastError = null;

        for (const baseUrl of baseUrls) {
            try {
                return await this.requestOnce(baseUrl, method, route, options);
            } catch (error) {
                lastError = error;
                if (!isRetryableFetchError(error)) throw error;
            }
        }

        throw lastError || new Error("Bridge request failed.");
    }

    async requestOnce(baseUrl, method, route, options = {}) {
        const url = new URL(baseUrl + route);
        if (options.query) {
            for (const [key, value] of Object.entries(options.query)) {
                if (value !== undefined && value !== null) {
                    url.searchParams.set(key, String(value));
                }
            }
        }

        const headers = {
            "Accept": "application/json"
        };
        if (options.body) headers["Content-Type"] = "application/octet-stream";

        const token = this.authToken();
        if (token) headers["Authorization"] = `Bearer ${token}`;

        this.output.appendLine(`[bridge] ${method} ${url.toString()}`);
        const response = await fetch(url, {
            method,
            headers,
            body: options.body
        });

        const text = await response.text();
        let payload = null;
        if (text) {
            try {
                payload = JSON.parse(text);
            } catch (error) {
                throw new Error(`Bridge returned invalid JSON (${response.status}): ${text}`);
            }
        }

        if (!response.ok || (payload && payload.ok === false)) {
            const message = payload && payload.error
                ? payload.error
                : `Bridge request failed with HTTP ${response.status}`;
            const error = new Error(message);
            error.status = response.status;
            throw error;
        }

        return payload || { ok: true };
    }
}

function normalizePath(path) {
    if (!path) return "/";
    const normalized = path.replace(/\\/g, "/");
    return normalized.startsWith("/") ? normalized : `/${normalized}`;
}

function isBridgeDirectory(error) {
    if (!error || typeof error.message !== "string") return false;
    return error.message.includes("is a directory");
}

function candidateBaseUrls(baseUrl) {
    const normalized = baseUrl.replace(/\/+$/, "");
    const url = new URL(normalized);
    const candidates = [normalized];
    const tail = url.pathname;

    if (url.hostname === "127.0.0.1") {
        candidates.push(`${url.protocol}//localhost${portPart(url)}${tail}`);
        candidates.push(`${url.protocol}//[::1]${portPart(url)}${tail}`);
    } else if (url.hostname === "localhost") {
        candidates.push(`${url.protocol}//127.0.0.1${portPart(url)}${tail}`);
        candidates.push(`${url.protocol}//[::1]${portPart(url)}${tail}`);
    } else if (url.hostname === "[::1]" || url.hostname === "::1") {
        candidates.push(`${url.protocol}//127.0.0.1${portPart(url)}${tail}`);
        candidates.push(`${url.protocol}//localhost${portPart(url)}${tail}`);
    }

    return [...new Set(candidates)];
}

function portPart(url) {
    return url.port ? `:${url.port}` : "";
}

function isRetryableFetchError(error) {
    if (!error) return false;
    const message = typeof error.message === "string" ? error.message.toLowerCase() : "";
    return message.includes("fetch failed") || message.includes("econnrefused") || message.includes("could not connect");
}

module.exports = {
    BridgeClient,
    normalizePath
};
