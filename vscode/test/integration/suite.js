'use strict';

/**
 * The integration tests, run inside VS Code by runTest.js. Each test drives the real editor through the public
 * commands that VS Code itself uses for completion, hovers and diagnostics.
 *
 * @example
 * // VS Code calls run() after loading the extension
 */

const assert = require('node:assert/strict');
const path = require('node:path');
const vscode = require('vscode');

const tests = [];
const test = (name, body) => tests.push({ name, body });

/** Waits until a condition holds. */
async function waitFor(condition, what, timeout = 20000) {
    const start = Date.now();
    for (;;) {
        const value = await condition();
        if (value) {
            return value;
        }
        if (Date.now() - start > timeout) {
            throw new Error('Timed out waiting for ' + what);
        }
        await new Promise(resolve => setTimeout(resolve, 100));
    }
}

const projectFolder = () => vscode.workspace.workspaceFolders[0].uri.fsPath;
const openView = async relative => {
    const document = await vscode.workspace.openTextDocument(path.join(projectFolder(), relative));
    await vscode.window.showTextDocument(document);
    return document;
};
const untitled = async text => {
    const document = await vscode.workspace.openTextDocument({ language: 'cvw', content: text });
    await vscode.window.showTextDocument(document);
    return document;
};

test('.cvw files open as C-View Markup and the extension starts', async () => {
    const document = await openView('Views/Home/IndexView.cvw');
    assert.equal(document.languageId, 'cvw');
    const extension = vscode.extensions.getExtension('MateuszPodeszwa.consolemvc-cvw');
    await waitFor(() => extension.isActive, 'activation');
});

test('completion offers the elements allowed inside a table', async () => {
    const document = await untitled('<table>\n    <\n</table>');
    const list = await vscode.commands.executeCommand('vscode.executeCompletionItemProvider', document.uri, new vscode.Position(1, 5), '<');
    const labels = list.items.map(i => (typeof i.label === 'string' ? i.label : i.label.label));
    assert.ok(labels.includes('row'), labels.join(', '));
    assert.ok(!labels.includes('box'));
});

test('completion offers an attribute\'s values', async () => {
    const document = await untitled('<box border=""></box>');
    const list = await vscode.commands.executeCommand('vscode.executeCompletionItemProvider', document.uri, new vscode.Position(0, 13), '"');
    const labels = list.items.map(i => (typeof i.label === 'string' ? i.label : i.label.label));
    assert.ok(labels.includes('rounded'), labels.join(', '));
});

test('hover documents an element', async () => {
    const document = await untitled('<box></box>');
    const hovers = await vscode.commands.executeCommand('vscode.executeHoverProvider', document.uri, new vscode.Position(0, 2));
    const text = hovers.flatMap(h => h.contents).map(c => c.value ?? c).join('\n');
    assert.match(text, /A bordered panel/);
});

test('mistakes are reported while typing', async () => {
    const document = await openView('Views/Home/IndexView.cvw');
    const editor = vscode.window.activeTextEditor;
    await editor.edit(edit => edit.insert(new vscode.Position(3, 4), '<blink />'));
    const diagnostics = await waitFor(() => {
        const list = vscode.languages.getDiagnostics(document.uri);
        return list.length > 0 ? list : null;
    }, 'diagnostics');
    assert.equal(diagnostics[0].source, 'ConsoleMVC');
    assert.match(diagnostics[0].message, /blink/);
    await vscode.commands.executeCommand('undo');
});

test('the preview command opens a panel', async () => {
    await openView('Views/Home/IndexView.cvw');
    await vscode.commands.executeCommand('consolemvc.showPreviewToSide');
    await waitFor(() => vscode.window.tabGroups.all.flatMap(g => g.tabs).some(t => t.label.startsWith('Preview IndexView.cvw')), 'the preview tab');
});

test('block comments use @* *@ from the language configuration', async () => {
    const document = await untitled('<text>x</text>');
    await vscode.commands.executeCommand('editor.action.selectAll');
    await vscode.commands.executeCommand('editor.action.blockComment');
    assert.match(document.getText(), /^@\* ?<text>x<\/text> ?\*@$/);
});

/**
 * Runs the tests one after another and fails when any failed.
 *
 * @returns {Promise<void>} Resolves when all passed.
 */
async function run() {
    const failures = [];
    for (const { name, body } of tests) {
        try {
            await body();
            console.log(`  ok   ${name}`);
        } catch (error) {
            failures.push(name);
            console.log(`  FAIL ${name}\n       ${error && error.stack || error}`);
        }
    }
    console.log(`${tests.length - failures.length} passed, ${failures.length} failed`);
    if (failures.length > 0) {
        throw new Error(`${failures.length} integration test(s) failed: ${failures.join('; ')}`);
    }
}

module.exports = { run };
