'use strict';

/**
 * Builds the HTML page of the preview panel: the screen drawn by `cvw-preview`, the problems under it, or a message
 * when there is nothing to show. The page runs no scripts, and its Content Security Policy allows only inline styles
 * (the screen's colours are inline `style` attributes), so nothing in a view can load or run anything.
 *
 * @example
 * panel.webview.html = PreviewPage.screen({ screenHtml, problems, title: 'IndexView.cvw', cspSource: panel.webview.cspSource });
 */
class PreviewPage {
    /**
     * A page showing a drawn screen.
     *
     * @param {{screenHtml: string, problems: object[], title: string, cspSource: string, stale?: boolean}} content
     *     The `<pre class="cvw-screen">` from the tool (already encoded by it), its problems, the view's name, the
     *     webview's CSP source, and whether the screen is from an older version of the text (the latest failed).
     * @returns {string} The page.
     * @example
     * PreviewPage.screen({ screenHtml: '<pre class="cvw-screen">Hi</pre>', problems: [], title: 'A', cspSource: 'vscode-resource:' });
     */
    static screen(content) {
        const problems = content.problems.length === 0 ? '' : [
            '<section class="problems" aria-label="Problems">',
            '<h2>Problems</h2>',
            '<ul>',
            ...content.problems.map(p => {
                const where = p.line ? `Line ${p.line}, column ${p.column}: ` : '';
                const code = p.code ? `${p.code} ` : '';
                return `<li class="${PreviewPage.severityClass(p.severity)}">${PreviewPage.escape(where + code + p.message)}</li>`;
            }),
            '</ul>',
            '</section>'
        ].join('\n');
        const stale = content.stale ? '<p class="stale">Showing the last preview that could be drawn.</p>' : '';
        return PreviewPage.page(content.title, content.cspSource, `${stale}\n${PreviewPage.onlyScreen(content.screenHtml)}\n${problems}`);
    }

    /**
     * A page with a message instead of a screen, such as how to install the tool.
     *
     * @param {{title: string, cspSource: string, heading: string, lines: string[]}} content The message; lines are
     *     plain text, and text between backticks is shown as code.
     * @returns {string} The page.
     * @example
     * PreviewPage.message({ title: 'A', cspSource, heading: 'cvw-preview is not installed',
     *     lines: ['Install it with `dotnet tool install -g ConsoleMVC.Preview`.'] });
     */
    static message(content) {
        const lines = content.lines.map(line => `<p>${PreviewPage.escape(line).replace(/`([^`]+)`/g, '<code>$1</code>')}</p>`);
        return PreviewPage.page(content.title, content.cspSource,
            `<section class="message"><h2>${PreviewPage.escape(content.heading)}</h2>\n${lines.join('\n')}</section>`);
    }

    /**
     * Keeps only the tool's `<pre class="cvw-screen">…</pre>`: anything else is dropped, so a changed or unexpected
     * tool cannot add other markup to the page.
     *
     * @param {string} html The tool's screen.
     * @returns {string} The `<pre>` element, or an empty one.
     * @example
     * PreviewPage.onlyScreen('<pre class="cvw-screen">x</pre><script>'); // '<pre class="cvw-screen">x</pre>'
     */
    static onlyScreen(html) {
        const start = html.indexOf('<pre class="cvw-screen">');
        const end = html.indexOf('</pre>', start);
        if (start < 0 || end < 0) {
            return '<pre class="cvw-screen"></pre>';
        }
        const inner = html.slice(start + '<pre class="cvw-screen">'.length, end);
        // The tool writes only text and <span style="…"> runs; anything else is escaped
        const safe = inner.replace(/<(?!\/?span(?: style="[^"<>]*")?>)/g, '&lt;');
        return `<pre class="cvw-screen">${safe}</pre>`;
    }

    /**
     * Escapes text for HTML.
     *
     * @param {string} text Plain text.
     * @returns {string} Encoded text.
     * @example
     * PreviewPage.escape('<b> & "x"'); // '&lt;b&gt; &amp; &quot;x&quot;'
     */
    static escape(text) {
        return String(text).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', '\'': '&#39;' })[c]);
    }

    /** The CSS class of a problem's severity; unknown severities are notes. @private */
    static severityClass(severity) {
        return ['error', 'warning'].includes(severity) ? severity : 'note';
    }

    /** The whole page around a body. @private */
    static page(title, cspSource, body) {
        const csp = `default-src 'none'; style-src 'unsafe-inline' ${cspSource}; img-src ${cspSource};`;
        return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="${PreviewPage.escape(csp)}">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${PreviewPage.escape(title)}</title>
<style>
    body { background: var(--vscode-editor-background); color: var(--vscode-editor-foreground); font-family: var(--vscode-font-family); padding: 12px; }
    .cvw-screen { font-family: var(--vscode-editor-font-family), monospace; font-size: var(--vscode-editor-font-size); line-height: normal; margin: 0; padding: 12px; background: #0c0c0c; color: #cccccc; border-radius: 4px; overflow-x: auto; width: max-content; min-width: 100%; box-sizing: border-box; }
    .problems h2, .message h2 { font-size: 1.1em; margin: 16px 0 8px; }
    .problems ul { padding-left: 20px; margin: 0; }
    .problems li { margin: 4px 0; }
    .error { color: var(--vscode-errorForeground); }
    .warning { color: var(--vscode-editorWarning-foreground); }
    .note, .stale { color: var(--vscode-descriptionForeground); }
    code { font-family: var(--vscode-editor-font-family), monospace; background: var(--vscode-textCodeBlock-background); padding: 1px 4px; border-radius: 3px; }
</style>
</head>
<body>
${body}
</body>
</html>
`;
    }
}

module.exports = { PreviewPage };
