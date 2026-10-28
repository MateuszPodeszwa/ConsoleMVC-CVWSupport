'use strict';

const path = require('node:path');
const { PreviewRunner, PreviewStatus } = require('./PreviewRunner');
const { PreviewPage } = require('./PreviewPage');
const { ToolLocator } = require('./ToolLocator');
const { SchemaValidator } = require('../language/SchemaValidator');
const { ProblemRange } = require('../language/ProblemRange');

/** The language identifier of views. */
const LANGUAGE_ID = 'cvw';

/**
 * Keeps the checks and preview panels of open views up to date: after each pause in typing it runs `cvw-preview` on
 * the view's text, shows the screen in the view's preview panel and its problems as diagnostics. When the tool cannot
 * run (not installed, an untrusted folder, a file that is not on disk) the checks come from the markup schema.
 *
 * ```mermaid
 * sequenceDiagram
 *     participant E as Editor
 *     participant C as PreviewController
 *     participant R as PreviewRunner
 *     participant P as Preview panel
 *     E->>C: document changed
 *     C->>C: wait for the delay (debounce)
 *     C->>R: run(file, text, width)
 *     R-->>C: html + problems | not installed | failed
 *     C->>E: diagnostics (tool's, or SchemaValidator's)
 *     C->>P: PreviewPage.screen(html, problems) or a message
 * ```
 *
 * @example
 * const controller = new PreviewController(vscode, schema);
 * context.subscriptions.push(controller);
 * controller.showPreview(editor.document, vscode.ViewColumn.Beside);
 */
class PreviewController {
    /**
     * @param {typeof import('vscode')} vscode The VS Code API.
     * @param {import('../schema/MarkupSchema').MarkupSchema} schema The markup schema.
     * @example
     * new PreviewController(require('vscode'), MarkupSchema.load());
     */
    constructor(vscode, schema) {
        this._vscode = vscode;
        this._validator = new SchemaValidator(schema);
        this._diagnostics = vscode.languages.createDiagnosticCollection('consolemvc');
        /** @type {Map<string, NodeJS.Timeout>} */
        this._timers = new Map();
        /** @type {Map<string, import('vscode').WebviewPanel>} */
        this._panels = new Map();
        /** @type {Map<string, {html: string, problems: object[]}>} The last screen drawn for each view. */
        this._lastDrawn = new Map();
        /** @type {Map<string, number>} The newest run for each view; older results are ignored. */
        this._runs = new Map();
        /** @type {string | null} The command that worked, so later runs start with it. */
        this._workingCommand = null;
        this._toolMissing = false;
        this._installHintShown = false;
        this._disposables = [this._diagnostics];
    }

    /**
     * Starts following the open views: their changes, saves, closing and the settings.
     *
     * @returns {this} The controller.
     * @example
     * context.subscriptions.push(new PreviewController(vscode, schema).start());
     */
    start() {
        const vscode = this._vscode;
        this._disposables.push(
            vscode.workspace.onDidOpenTextDocument(document => this.schedule(document, 0)),
            vscode.workspace.onDidChangeTextDocument(event => this.schedule(event.document)),
            vscode.workspace.onDidCloseTextDocument(document => this._forget(document)),
            vscode.workspace.onDidChangeConfiguration(event => {
                if (event.affectsConfiguration('consolemvc')) {
                    this._toolMissing = false;
                    this._workingCommand = null;
                    this._refreshAll();
                }
            }),
            vscode.workspace.onDidGrantWorkspaceTrust(() => this._refreshAll())
        );
        this._refreshAll();
        return this;
    }

    /**
     * Opens (or shows) the preview panel of a view.
     *
     * @param {import('vscode').TextDocument} document The view.
     * @param {import('vscode').ViewColumn} column Where to open the panel.
     * @example
     * controller.showPreview(editor.document, vscode.ViewColumn.Beside);
     */
    showPreview(document, column) {
        const key = document.uri.toString();
        const existing = this._panels.get(key);
        if (existing) {
            existing.reveal(column, true);
            return;
        }

        const panel = this._vscode.window.createWebviewPanel('consolemvc.preview', `Preview ${PreviewController._name(document)}`,
            { viewColumn: column, preserveFocus: true }, { enableScripts: false, enableCommandUris: false, localResourceRoots: [] });
        panel.iconPath = this._vscode.Uri.file(path.join(__dirname, '..', '..', 'icons', 'cvw.svg'));
        panel.onDidDispose(() => this._panels.delete(key));
        this._panels.set(key, panel);

        const drawn = this._lastDrawn.get(key);
        panel.webview.html = drawn
            ? PreviewPage.screen({ screenHtml: drawn.html, problems: drawn.problems, title: panel.title, cspSource: panel.webview.cspSource })
            : PreviewPage.message({ title: panel.title, cspSource: panel.webview.cspSource, heading: 'Drawing the preview…', lines: [] });
        this.schedule(document, 0);
    }

    /**
     * Updates a view's checks and preview after the configured delay; each change restarts the wait.
     *
     * @param {import('vscode').TextDocument} document The view.
     * @param {number} [delay] The wait in milliseconds; the setting by default.
     * @example
     * controller.schedule(document, 0); // now
     */
    schedule(document, delay) {
        if (document.languageId !== LANGUAGE_ID) {
            return;
        }
        const key = document.uri.toString();
        clearTimeout(this._timers.get(key));
        const wait = delay ?? this._setting('preview.delay', 300);
        this._timers.set(key, setTimeout(() => {
            this._timers.delete(key);
            this.refresh(document).catch(() => { /* a failed refresh leaves the last state */ });
        }, wait));
    }

    /**
     * Updates a view's checks and preview now.
     *
     * @param {import('vscode').TextDocument} document The view.
     * @returns {Promise<void>} Resolves when done.
     * @example
     * await controller.refresh(document);
     */
    async refresh(document) {
        const key = document.uri.toString();
        const checking = this._setting('diagnostics.enabled', true);
        const panel = this._panels.get(key);
        if (!checking && !panel) {
            this._diagnostics.delete(document.uri);
            return;
        }

        const run = (this._runs.get(key) ?? 0) + 1;
        this._runs.set(key, run);
        const text = document.getText();
        const result = await this._draw(document, text);
        if (this._runs.get(key) !== run || document.isClosed) {
            return; // a newer run started, or the view was closed
        }

        if (result.status === PreviewStatus.Drawn) {
            const screen = { html: result.html, problems: result.problems };
            this._lastDrawn.set(key, screen);
            this._setDiagnostics(document, text, checking ? this._toolDiagnostics(text, result.problems) : []);
            this._show(key, screen, false);
            return;
        }

        this._setDiagnostics(document, text, checking ? this._schemaDiagnostics(text) : []);
        const last = this._lastDrawn.get(key);
        if (result.status === PreviewStatus.Failed && last) {
            this._show(key, { ...last, problems: [{ severity: 'error', line: null, column: null, code: null, message: result.error }] }, true);
        } else {
            this._showMessage(key, result);
        }
    }

    /** Stops every timer and closes the panels. */
    dispose() {
        this._timers.forEach(timer => clearTimeout(timer));
        this._panels.forEach(panel => panel.dispose());
        this._disposables.forEach(d => d.dispose());
    }

    // ---------------------------------------------------------------- drawing

    /**
     * Runs the tool when it may run here; otherwise says why not.
     *
     * @private
     */
    async _draw(document, text) {
        if (!this._vscode.workspace.isTrusted) {
            return { status: 'untrusted' };
        }
        if (document.uri.scheme !== 'file' && document.uri.scheme !== 'untitled') {
            return { status: 'notOnDisk' };
        }
        if (this._toolMissing) {
            return { status: PreviewStatus.NotInstalled };
        }

        const request = {
            file: document.uri.scheme === 'file' ? document.uri.fsPath : undefined,
            text,
            width: this._setting('preview.width', 80),
            highContrast: this._setting('preview.highContrast', false)
            // No working folder: the file is passed by its full path, and a folder that is gone would make the
            // start fail with ENOENT, which reads as "not installed"
        };

        const configured = this._vscode.workspace.getConfiguration('consolemvc').get('preview.path');
        const commands = this._workingCommand ? [this._workingCommand] : ToolLocator.candidates(configured);
        for (const command of commands) {
            const result = await new PreviewRunner(command).run(request);
            if (result.status !== PreviewStatus.NotInstalled) {
                this._workingCommand = command;
                return result;
            }
        }

        this._toolMissing = true;
        this._suggestInstall();
        return { status: PreviewStatus.NotInstalled };
    }

    /** Shows a drawn screen in the view's panel, if it has one. @private */
    _show(key, screen, stale) {
        const panel = this._panels.get(key);
        if (panel) {
            panel.webview.html = PreviewPage.screen({
                screenHtml: screen.html, problems: screen.problems, title: panel.title, cspSource: panel.webview.cspSource, stale
            });
        }
    }

    /** Shows why there is no screen in the view's panel, if it has one. @private */
    _showMessage(key, result) {
        const panel = this._panels.get(key);
        if (!panel) {
            return;
        }
        const messages = {
            [PreviewStatus.NotInstalled]: ['The preview needs the cvw-preview tool', [
                'Install it with `dotnet tool install -g ConsoleMVC.Preview`, then reopen the preview.',
                'If it is installed somewhere else, set `consolemvc.preview.path` in your user settings.'
            ]],
            untrusted: ['The preview is off in untrusted folders', ['Trust this folder to draw previews with cvw-preview.']],
            notOnDisk: ['The preview needs a file on disk', ['Open the view from a folder on this computer.']],
            [PreviewStatus.Failed]: ['The preview could not be drawn', [result.error ?? 'cvw-preview failed.']]
        };
        const [heading, lines] = messages[result.status] ?? messages[PreviewStatus.Failed];
        panel.webview.html = PreviewPage.message({ title: panel.title, cspSource: panel.webview.cspSource, heading, lines });
    }

    /** Offers the install command once per session. @private */
    _suggestInstall() {
        if (this._installHintShown) {
            return;
        }
        this._installHintShown = true;
        const copy = 'Copy install command';
        this._vscode.window.showInformationMessage(
            'Install cvw-preview for the ConsoleMVC preview and the build\'s own checks: dotnet tool install -g ConsoleMVC.Preview',
            copy
        ).then(choice => {
            if (choice === copy) {
                this._vscode.env.clipboard.writeText('dotnet tool install -g ConsoleMVC.Preview');
            }
        });
    }

    // ---------------------------------------------------------------- diagnostics

    /** Diagnostics from the tool's problems; notes without a line are shown in the panel only. @private */
    _toolDiagnostics(text, problems) {
        return problems.filter(p => p.line !== null).map(p => ({
            ...ProblemRange.fromLineColumn(text, p.line, p.column),
            severity: p.severity,
            code: p.code,
            message: p.message
        }));
    }

    /** @private */
    _schemaDiagnostics(text) {
        return this._validator.validate(text);
    }

    /** @private */
    _setDiagnostics(document, text, problems) {
        const vscode = this._vscode;
        this._diagnostics.set(document.uri, problems.map(p => {
            const range = new vscode.Range(document.positionAt(p.start), document.positionAt(Math.max(p.end, p.start)));
            const diagnostic = new vscode.Diagnostic(range, p.message,
                p.severity === 'warning' ? vscode.DiagnosticSeverity.Warning : vscode.DiagnosticSeverity.Error);
            diagnostic.source = 'ConsoleMVC';
            if (p.code) {
                diagnostic.code = p.code;
            }
            return diagnostic;
        }));
    }

    // ---------------------------------------------------------------- helpers

    /** @private */
    _refreshAll() {
        this._vscode.workspace.textDocuments.forEach(document => this.schedule(document, 0));
    }

    /** @private */
    _forget(document) {
        const key = document.uri.toString();
        clearTimeout(this._timers.get(key));
        this._timers.delete(key);
        this._runs.delete(key);
        this._lastDrawn.delete(key);
        this._diagnostics.delete(document.uri);
    }

    /** @private */
    _setting(name, fallback) {
        return this._vscode.workspace.getConfiguration('consolemvc').get(name, fallback);
    }

    /** The file name shown in a panel's title. @private */
    static _name(document) {
        return path.basename(document.uri.path || 'view.cvw');
    }
}

module.exports = { PreviewController, LANGUAGE_ID };
