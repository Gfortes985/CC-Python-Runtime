"use strict";

const path = require("node:path");

function extensionOf(fileName) {
    return path.extname(fileName).toLowerCase();
}

function isPreviewableImageName(fileName) {
    return [".png", ".jpg", ".jpeg", ".gif", ".webp"].includes(extensionOf(fileName));
}

function isInspectableDataName(fileName) {
    return [".json", ".toml", ".yaml", ".yml"].includes(extensionOf(fileName));
}

function isMidiName(fileName) {
    return [".mid", ".midi"].includes(extensionOf(fileName));
}

function imageMimeType(fileName) {
    switch (extensionOf(fileName)) {
        case ".png":
            return "image/png";
        case ".gif":
            return "image/gif";
        case ".webp":
            return "image/webp";
        default:
            return "image/jpeg";
    }
}

function inspectDataFile(fileName, content) {
    const format = extensionOf(fileName).replace(/^\./, "") || "text";
    const rawText = content.toString("utf8");
    let pretty = rawText;
    let summary = "Structured text file";

    if (format === "json") {
        try {
            const parsed = JSON.parse(rawText);
            pretty = JSON.stringify(parsed, null, 2);
            summary = Array.isArray(parsed)
                ? `JSON array with ${parsed.length} item(s)`
                : `JSON object with ${Object.keys(parsed || {}).length} key(s)`;
        } catch {
            summary = "JSON file (invalid JSON, showing raw text)";
        }
    } else if (format === "toml") {
        summary = "TOML file";
    } else {
        summary = "YAML file";
    }

    return {
        format,
        summary,
        rawText,
        pretty
    };
}

function inspectMidiFile(content) {
    if (content.length < 14 || content.subarray(0, 4).toString("ascii") !== "MThd") {
        return {
            valid: false,
            bytes: content.length
        };
    }

    const headerLength = content.readUInt32BE(4);
    const format = content.readUInt16BE(8);
    const tracks = content.readUInt16BE(10);
    const division = content.readUInt16BE(12);
    let offset = 8 + headerLength;
    let chunkCount = 1;

    while (offset + 8 <= content.length) {
        const type = content.subarray(offset, offset + 4).toString("ascii");
        const length = content.readUInt32BE(offset + 4);
        chunkCount += 1;
        offset += 8 + length;
        if (type !== "MTrk") break;
    }

    return {
        valid: true,
        bytes: content.length,
        headerLength,
        format,
        tracks,
        division,
        chunkCount
    };
}

function renderDataInspectorHtml(entry, inspection) {
    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <style>
    body { font-family: var(--vscode-font-family); color: var(--vscode-foreground); background: var(--vscode-editor-background); padding: 16px; }
    .meta { margin-bottom: 16px; opacity: 0.85; }
    pre { white-space: pre-wrap; word-break: break-word; padding: 12px; border-radius: 8px; border: 1px solid var(--vscode-panel-border); background: color-mix(in srgb, var(--vscode-editor-background) 90%, black 10%); }
  </style>
</head>
<body>
  <div class="meta"><strong>${escapeHtml(entry.entry.name)}</strong><br/>${escapeHtml(inspection.summary)}<br/>${escapeHtml(entry.entry.path)}</div>
  <pre>${escapeHtml(inspection.pretty)}</pre>
</body>
</html>`;
}

function renderMidiInspectorHtml(entry, info, dataUri) {
    const details = info.valid
        ? `
            <li>Format: ${info.format}</li>
            <li>Tracks: ${info.tracks}</li>
            <li>Division: ${info.division}</li>
            <li>Chunks: ${info.chunkCount}</li>
            <li>Bytes: ${info.bytes}</li>
        `
        : `<li>File does not look like a valid Standard MIDI file.</li><li>Bytes: ${info.bytes}</li>`;

    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <style>
    body { font-family: var(--vscode-font-family); color: var(--vscode-foreground); background: var(--vscode-editor-background); padding: 16px; }
    .toolbar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
    button { border: 1px solid var(--vscode-button-border, transparent); background: var(--vscode-button-background); color: var(--vscode-button-foreground); padding: 6px 12px; border-radius: 6px; cursor: pointer; }
    button.secondary { background: var(--vscode-button-secondaryBackground, transparent); color: var(--vscode-button-secondaryForeground, var(--vscode-foreground)); }
    .panel { border: 1px solid var(--vscode-panel-border); border-radius: 8px; padding: 12px; margin-top: 12px; }
  </style>
</head>
<body>
  <div class="toolbar">
    <button id="openLocal">Open Locally</button>
    <button id="download" class="secondary">Download</button>
    <span>${escapeHtml(entry.entry.path)}</span>
  </div>
  <div class="panel">
    <strong>MIDI Info</strong>
    <ul>${details}</ul>
  </div>
  <div class="panel">
    <strong>Browser Preview</strong>
    <p>This depends on your VS Code webview engine. If it stays silent, use Open Locally.</p>
    <audio controls src="${dataUri}"></audio>
  </div>
  <script>
    const vscode = acquireVsCodeApi();
    document.getElementById('openLocal').addEventListener('click', () => vscode.postMessage({ command: 'openLocal' }));
    document.getElementById('download').addEventListener('click', () => vscode.postMessage({ command: 'download' }));
  </script>
</body>
</html>`;
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

module.exports = {
    escapeHtml,
    imageMimeType,
    inspectDataFile,
    inspectMidiFile,
    isInspectableDataName,
    isMidiName,
    isPreviewableImageName,
    renderDataInspectorHtml,
    renderMidiInspectorHtml
};
