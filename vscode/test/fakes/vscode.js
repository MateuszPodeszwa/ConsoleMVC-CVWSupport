'use strict';

/**
 * A small fake of the VS Code API, enough for PreviewController and the providers: documents, settings, diagnostics
 * and webview panels, recorded so tests can look at them.
 *
 * @example
 * const vscode = createFakeVscode({ settings: { 'diagnostics.enabled': true } });
 * const document = vscode.openDocument('/shop/Views/Home/IndexView.cvw', '<text>x</text>');
 */
function createFakeVscode(options = {}) {
    const settings = { ...options.settings };
    const listeners = { open: [], change: [], close: [], configuration: [], trust: [] };
    const diagnostics = new Map();
    const panels = [];
    const messages = [];

    class Position {
        constructor(line, character) {
            this.line = line;
            this.character = character;
        }
    }
    class Range {
        constructor(start, end) {
            this.start = start;
            this.end = end;
        }
    }
    class Diagnostic {
        constructor(range, message, severity) {
            this.range = range;
            this.message = message;
            this.severity = severity;
        }
    }

    const event = list => listener => {
        list.push(listener);
        return { dispose: () => list.splice(list.indexOf(listener), 1) };
    };

    const vscode = {
        Range,
        Position,
        Diagnostic,
        DiagnosticSeverity: { Error: 0, Warning: 1 },
        ViewColumn: { Active: -1, Beside: -2 },
        Uri: { file: fsPath => ({ scheme: 'file', fsPath, path: fsPath, toString: () => 'file://' + fsPath }) },
        env: { clipboard: { writeText: async () => {} } },
        languages: {
            createDiagnosticCollection: () => ({
                set: (uri, list) => diagnostics.set(uri.toString(), list),
                delete: uri => diagnostics.delete(uri.toString()),
                dispose: () => diagnostics.clear()
            })
        },
        window: {
            createWebviewPanel: (type, title) => {
                const disposeListeners = [];
                const panel = {
                    title,
                    webview: { html: '', cspSource: 'vscode-resource:' },
                    reveal: () => {},
                    onDidDispose: listener => disposeListeners.push(listener),
                    dispose: () => disposeListeners.forEach(l => l())
                };
                panels.push(panel);
                return panel;
            },
            showInformationMessage: async message => {
                messages.push(message);
                return undefined;
            }
        },
        workspace: {
            isTrusted: options.trusted ?? true,
            textDocuments: [],
            getConfiguration: () => ({ get: (name, fallback) => (name in settings ? settings[name] : fallback) }),
            onDidOpenTextDocument: event(listeners.open),
            onDidChangeTextDocument: event(listeners.change),
            onDidCloseTextDocument: event(listeners.close),
            onDidChangeConfiguration: event(listeners.configuration),
            onDidGrantWorkspaceTrust: event(listeners.trust)
        },

        // Test helpers, not part of the API
        settings,
        diagnostics,
        panels,
        messages,
        openDocument(fsPath, text, scheme = 'file') {
            const document = {
                uri: scheme === 'file' ? vscode.Uri.file(fsPath) : { scheme, fsPath, path: fsPath, toString: () => `${scheme}:${fsPath}` },
                languageId: 'cvw',
                isClosed: false,
                text,
                getText: () => document.text,
                positionAt: offset => {
                    const before = document.text.slice(0, offset).split('\n');
                    return new Position(before.length - 1, before[before.length - 1].length);
                }
            };
            vscode.workspace.textDocuments.push(document);
            return document;
        }
    };
    return vscode;
}

module.exports = { createFakeVscode };
