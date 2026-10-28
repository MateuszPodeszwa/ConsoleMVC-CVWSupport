'use strict';

const { spawn } = require('node:child_process');

/** How long one preview may take before it is stopped, in milliseconds. */
const TIMEOUT_MS = 10_000;

/** The most output read from the tool; a bigger preview is cut off. */
const MAX_OUTPUT_BYTES = 8 * 1024 * 1024;

/**
 * The outcome of running the tool.
 *
 * @example
 * if (result.status === PreviewStatus.NotInstalled) showInstallHint();
 */
const PreviewStatus = Object.freeze({
    /** The preview was drawn; `html` and `problems` are set. */
    Drawn: 'drawn',
    /** The command was not found. */
    NotInstalled: 'notInstalled',
    /** The tool failed, timed out or wrote something unexpected; `error` says what. */
    Failed: 'failed'
});

/**
 * Runs `cvw-preview` on a view's text and reads its HTML output: the drawn screen and the list of problems.
 * The command runs without a shell, with its arguments as a list, so nothing in a file name or the view can become a
 * command; the view's text goes through standard input.
 *
 * @example
 * const runner = new PreviewRunner('cvw-preview');
 * const result = await runner.run({ file: '/shop/Views/Home/IndexView.cvw', text, width: 80 });
 * if (result.status === PreviewStatus.Drawn) panel.show(result.html);
 */
class PreviewRunner {
    /**
     * @param {string} command The `cvw-preview` command or its full path.
     * @param {(command: string, args: string[], options: object) => import('node:child_process').ChildProcess} [spawner]
     *     Starts the process; tests pass a fake.
     * @example
     * const runner = new PreviewRunner('/home/me/.dotnet/tools/cvw-preview');
     */
    constructor(command, spawner = spawn) {
        this._command = command;
        this._spawn = spawner;
    }

    /**
     * The arguments for a preview.
     *
     * @param {{file?: string, width: number, highContrast?: boolean}} request What to draw; no file for an unsaved view.
     * @returns {string[]} The arguments.
     * @example
     * PreviewRunner.arguments({ file: 'IndexView.cvw', width: 100 });
     * // ['IndexView.cvw', '--stdin', '--format', 'html', '--width', '100']
     */
    static arguments(request) {
        const width = Math.min(500, Math.max(20, Math.trunc(Number(request.width) || 80)));
        // Without a file (an unsaved view) the tool has no project to find layouts and partial views in
        const args = [...(request.file ? [request.file] : []), '--stdin', '--format', 'html', '--width', String(width)];
        if (request.highContrast) {
            args.push('--high-contrast');
        }
        return args;
    }

    /**
     * Draws a view.
     *
     * @param {{file?: string, text: string, width: number, highContrast?: boolean}} request The view's
     *     path (for its layout and partial views; none for an unsaved view), its current text and how to draw it.
     * @returns {Promise<{status: string, html?: string, problems?: object[], error?: string}>} The outcome; never rejects.
     * @example
     * const { status, html, problems } = await runner.run({ file, text, width: 80 });
     */
    run(request) {
        return new Promise(resolve => {
            let child;
            try {
                child = this._spawn(this._command, PreviewRunner.arguments(request), {
                    shell: false,
                    windowsHide: true,
                    stdio: ['pipe', 'pipe', 'pipe']
                });
            } catch (error) {
                resolve(PreviewRunner._failure(error));
                return;
            }

            const output = [];
            let size = 0;
            let errorText = '';
            let settled = false;
            const finish = result => {
                if (!settled) {
                    settled = true;
                    clearTimeout(timer);
                    resolve(result);
                }
            };
            const timer = setTimeout(() => {
                child.kill();
                finish({ status: PreviewStatus.Failed, error: `cvw-preview did not finish within ${TIMEOUT_MS / 1000} seconds.` });
            }, TIMEOUT_MS);

            child.on('error', error => finish(PreviewRunner._failure(error)));
            child.stdout.on('data', chunk => {
                size += chunk.length;
                if (size <= MAX_OUTPUT_BYTES) {
                    output.push(chunk);
                } else {
                    child.kill();
                    finish({ status: PreviewStatus.Failed, error: 'The preview is too large to show.' });
                }
            });
            child.stderr.on('data', chunk => {
                errorText = (errorText + chunk.toString('utf8')).slice(-4096);
            });
            child.on('close', code => {
                if (code !== 0) {
                    finish({ status: PreviewStatus.Failed, error: errorText.trim() || `cvw-preview exited with code ${code}.` });
                    return;
                }
                const html = Buffer.concat(output).toString('utf8');
                if (!html.startsWith('<pre class="cvw-screen">')) {
                    finish({ status: PreviewStatus.Failed, error: 'cvw-preview wrote something other than a preview; is it ConsoleMVC.Preview 1.34 or later?' });
                    return;
                }
                finish({ status: PreviewStatus.Drawn, html, problems: PreviewRunner.parseProblems(html) });
            });

            child.stdin.on('error', () => { /* the process ended early; 'close' reports it */ });
            child.stdin.end(request.text, 'utf8');
        });
    }

    /**
     * Reads the problems listed after the screen in the tool's HTML output.
     *
     * @param {string} html The tool's output.
     * @returns {{severity: string, line: number | null, column: number | null, code: string | null, message: string}[]}
     *     The problems; notes have no line.
     * @example
     * PreviewRunner.parseProblems(html)[0]; // { severity: 'error', line: 3, column: 5, code: 'CVW105', message: 'Unknown element <blink>…' }
     */
    static parseProblems(html) {
        const listStart = html.indexOf('<ul class="cvw-problems">');
        if (listStart < 0) {
            return [];
        }

        const problems = [];
        const item = /<li class="cvw-(error|warning|note)"(?: data-line="(\d+)" data-column="(\d+)")?>([\s\S]*?)<\/li>/g;
        for (const match of html.slice(listStart).matchAll(item)) {
            let message = PreviewRunner.decode(match[4]);
            let code = null;
            const located = /^Line \d+, column \d+: (CVW\d+) ([\s\S]*)$/.exec(message);
            if (located) {
                code = located[1];
                message = located[2];
            }
            problems.push({
                severity: match[1],
                line: match[2] ? Number(match[2]) : null,
                column: match[3] ? Number(match[3]) : null,
                code,
                message
            });
        }
        return problems;
    }

    /**
     * The screen part of the output, without the problems list.
     *
     * @param {string} html The tool's output.
     * @returns {string} The `<pre class="cvw-screen">…</pre>` element.
     * @example
     * panel.webview.html = page(PreviewRunner.screen(html));
     */
    static screen(html) {
        const end = html.indexOf('</pre>');
        return end < 0 ? html : html.slice(0, end + '</pre>'.length);
    }

    /**
     * Decodes the HTML entities the tool writes (WebUtility.HtmlEncode).
     *
     * @param {string} text Encoded text.
     * @returns {string} Plain text.
     * @example
     * PreviewRunner.decode('&lt;box&gt; &amp; &#39;x&#39;'); // "<box> & 'x'"
     */
    static decode(text) {
        return text.replace(/&(?:#(\d+)|#x([0-9a-f]+)|(lt|gt|amp|quot|apos));/gi, (whole, decimal, hex, named) => {
            if (decimal) {
                return String.fromCodePoint(Number(decimal));
            }
            if (hex) {
                return String.fromCodePoint(parseInt(hex, 16));
            }
            return { lt: '<', gt: '>', amp: '&', quot: '"', apos: '\'' }[named.toLowerCase()];
        });
    }

    /** A failure from a spawn error: ENOENT means the command is not installed. @private */
    static _failure(error) {
        return error && error.code === 'ENOENT'
            ? { status: PreviewStatus.NotInstalled, error: 'cvw-preview was not found.' }
            : { status: PreviewStatus.Failed, error: String(error && error.message || error) };
    }
}

module.exports = { PreviewRunner, PreviewStatus, TIMEOUT_MS, MAX_OUTPUT_BYTES };
