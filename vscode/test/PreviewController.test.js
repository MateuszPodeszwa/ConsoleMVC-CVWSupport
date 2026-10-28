'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { PreviewController } = require('../src/preview/PreviewController');
const { createFakeVscode } = require('./fakes/vscode');
const { schema } = require('./helpers');

const fakeTool = path.join(__dirname, 'fakes', 'fake-preview.js');

/**
 * A cvw-preview command that runs the fake tool: a small executable script in a temporary folder.
 * Executable scripts need a POSIX system, so these tests are skipped on Windows.
 */
function fakeCommand(mode = 'echo') {
    const folder = fs.mkdtempSync(path.join(os.tmpdir(), 'cvw-fake-'));
    const command = path.join(folder, 'cvw-preview');
    fs.writeFileSync(command, `#!/bin/sh\nFAKE_MODE=${mode} exec "${process.execPath}" "${fakeTool}" "$@"\n`, { mode: 0o755 });
    return command;
}

/** Waits until a condition holds, for work started by timers. */
async function waitFor(condition, timeout = 5000) {
    const start = Date.now();
    while (!condition()) {
        if (Date.now() - start > timeout) {
            throw new Error('Timed out waiting');
        }
        await new Promise(resolve => setTimeout(resolve, 10));
    }
}

const posixOnly = { skip: process.platform === 'win32' ? 'needs an executable script' : false };

test('without the tool, the checks come from the schema and installing is suggested once', async () => {
    const vscode = createFakeVscode({ settings: { 'preview.path': '/nowhere/cvw-preview' } });
    const controller = new PreviewController(vscode, schema);
    const document = vscode.openDocument('/shop/Views/Home/IndexView.cvw', '<box><blink /></box>');

    await controller.refresh(document);
    await controller.refresh(document);

    const [problem] = vscode.diagnostics.get(document.uri.toString());
    assert.equal(problem.code, 'CVW105');
    assert.equal(problem.source, 'ConsoleMVC');
    assert.equal(vscode.messages.length, 1);
    assert.match(vscode.messages[0], /dotnet tool install -g ConsoleMVC.Preview/);
    controller.dispose();
});

test('the preview panel explains how to install the tool', async () => {
    const vscode = createFakeVscode({ settings: { 'preview.path': '/nowhere/cvw-preview' } });
    const controller = new PreviewController(vscode, schema);
    const document = vscode.openDocument('/shop/Views/Home/IndexView.cvw', '<text>x</text>');
    controller.showPreview(document, vscode.ViewColumn.Beside);
    await controller.refresh(document);
    assert.match(vscode.panels[0].webview.html, /<code>dotnet tool install -g ConsoleMVC.Preview<\/code>/);
    controller.dispose();
});

test('with the tool, its problems become diagnostics and the panel shows the screen', posixOnly, async () => {
    const vscode = createFakeVscode({ settings: { 'preview.path': fakeCommand() } });
    const controller = new PreviewController(vscode, schema);
    const document = vscode.openDocument('/shop/Views/Home/IndexView.cvw', '<box>\n  <blink />\n</box>');
    controller.showPreview(document, vscode.ViewColumn.Beside);
    await waitFor(() => vscode.panels[0].webview.html.includes('<pre class="cvw-screen">'));

    const [problem] = vscode.diagnostics.get(document.uri.toString());
    assert.equal(problem.code, 'CVW105');
    assert.deepEqual([problem.range.start.line, problem.range.start.character, problem.range.end.character], [1, 2, 8]);
    const html = vscode.panels[0].webview.html;
    assert.match(html, /<pre class="cvw-screen">&lt;box&gt;/);
    assert.match(html, /The partial view &#39;Header&#39; was not found/);
    controller.dispose();
});

test('when the tool fails after a good preview, the last screen stays with the error', posixOnly, async () => {
    const vscode = createFakeVscode({ settings: { 'preview.path': fakeCommand() } });
    const controller = new PreviewController(vscode, schema);
    const document = vscode.openDocument('/shop/Views/Home/IndexView.cvw', '<text>first</text>');
    controller.showPreview(document, vscode.ViewColumn.Beside); // draws straight away
    await waitFor(() => /&lt;text&gt;first/.test(vscode.panels[0].webview.html));

    vscode.settings['preview.path'] = fakeCommand('fail');
    controller._workingCommand = null;
    document.text = '<text>second</text>';
    await controller.refresh(document);

    const html = vscode.panels[0].webview.html;
    assert.match(html, /&lt;text&gt;first/);
    assert.match(html, /Showing the last preview/);
    assert.match(html, /cannot read/);
    controller.dispose();
});

test('in an untrusted folder the tool never runs', async () => {
    const vscode = createFakeVscode({ trusted: false, settings: { 'preview.path': '/must/not/run' } });
    const controller = new PreviewController(vscode, schema);
    const document = vscode.openDocument('/shop/Views/Home/IndexView.cvw', '<box><blink /></box>');
    controller.showPreview(document, vscode.ViewColumn.Beside);
    await controller.refresh(document);
    assert.equal(vscode.messages.length, 0);
    assert.equal(vscode.diagnostics.get(document.uri.toString())[0].code, 'CVW105');
    assert.match(vscode.panels[0].webview.html, /untrusted folders/);
    controller.dispose();
});

test('a view that is not a file uses the schema checks', async () => {
    const vscode = createFakeVscode({ settings: { 'preview.path': '/must/not/run' } });
    const controller = new PreviewController(vscode, schema);
    const document = vscode.openDocument('/remote/IndexView.cvw', '<text size="1">x</text>', 'vscode-vfs');
    await controller.refresh(document);
    assert.equal(vscode.diagnostics.get(document.uri.toString())[0].code, 'CVW100');
    assert.equal(vscode.messages.length, 0);
    controller.dispose();
});

test('with checks turned off and no panel, there are no diagnostics', async () => {
    const vscode = createFakeVscode({ settings: { 'diagnostics.enabled': false } });
    const controller = new PreviewController(vscode, schema);
    const document = vscode.openDocument('/shop/Views/Home/IndexView.cvw', '<box><blink /></box>');
    await controller.refresh(document);
    assert.equal(vscode.diagnostics.has(document.uri.toString()), false);
    controller.dispose();
});

test('changes are debounced: only the last text is checked', async () => {
    const vscode = createFakeVscode({ settings: { 'preview.path': '/nowhere/cvw-preview', 'preview.delay': 20 } });
    const controller = new PreviewController(vscode, schema).start();
    const document = vscode.openDocument('/shop/Views/Home/IndexView.cvw', '<box><one /></box>');
    controller.schedule(document);
    document.text = '<box><two /></box>';
    controller.schedule(document);
    await new Promise(resolve => setTimeout(resolve, 100));
    const problems = vscode.diagnostics.get(document.uri.toString());
    assert.equal(problems.length, 1);
    assert.match(problems[0].message, /<two>/);
    controller.dispose();
});

test('views in other languages are ignored', () => {
    const vscode = createFakeVscode();
    const controller = new PreviewController(vscode, schema);
    const document = vscode.openDocument('/shop/Program.cs', 'x');
    document.languageId = 'csharp';
    controller.schedule(document, 0);
    assert.equal(controller._timers.size, 0);
    controller.dispose();
});
