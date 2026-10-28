'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { SchemaValidator } = require('../src/language/SchemaValidator');
const { schema } = require('./helpers');

const root = path.join(__dirname, '..');
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));

test('every file the manifest names exists', () => {
    const files = [
        manifest.main,
        manifest.icon,
        ...manifest.contributes.languages.flatMap(l => [l.configuration, l.icon.light, l.icon.dark]),
        ...manifest.contributes.grammars.map(g => g.path),
        ...manifest.contributes.snippets.map(s => s.path)
    ];
    for (const file of files) {
        assert.ok(fs.existsSync(path.join(root, file)), file);
    }
});

test('every command in a menu or key binding is declared', () => {
    const declared = new Set(manifest.contributes.commands.map(c => c.command));
    const used = [
        ...Object.values(manifest.contributes.menus).flat().map(m => m.command),
        ...manifest.contributes.keybindings.map(k => k.command)
    ];
    for (const command of used) {
        assert.ok(declared.has(command), command);
    }
});

test('every declared command is registered by the extension', () => {
    const source = fs.readFileSync(path.join(root, 'src', 'extension.js'), 'utf8');
    for (const { command } of manifest.contributes.commands) {
        assert.ok(source.includes(`'${command}'`), command);
    }
});

test('the settings the code reads are declared', () => {
    const declared = Object.keys(manifest.contributes.configuration.properties).map(k => k.replace(/^consolemvc\./, ''));
    const source = fs.readFileSync(path.join(root, 'src', 'preview', 'PreviewController.js'), 'utf8');
    const read = [...source.matchAll(/_setting\('([\w.]+)'/g), ...source.matchAll(/\.get\('([\w.]+)'\)/g)].map(m => m[1]);
    assert.ok(read.length >= 4);
    for (const name of read) {
        assert.ok(declared.includes(name), name);
    }
});

test('the preview command path can only be set by the user, not by a folder', () => {
    assert.equal(manifest.contributes.configuration.properties['consolemvc.preview.path'].scope, 'machine');
});

test('every snippet writes markup the schema accepts', () => {
    const snippets = JSON.parse(fs.readFileSync(path.join(root, 'snippets', 'cvw.code-snippets'), 'utf8'));
    const validator = new SchemaValidator(schema);
    for (const [name, snippet] of Object.entries(snippets)) {
        // Fill the placeholders with their defaults, or a word when they have none
        const body = snippet.body.join('\n')
            .replace(/\$\{\d+:((?:[^{}]|\{[^{}]*\})*)\}/g, (whole, inner) => inner.replace(/\$\{\d+:([^{}]*)\}/g, '$1'))
            .replace(/\$\d+/g, 'x');
        assert.deepEqual(validator.validate(body), [], name);
    }
});

test('the bundled schema is the one this extension was built for', () => {
    const bundled = JSON.parse(fs.readFileSync(path.join(root, 'schema', 'cvw-schema.json'), 'utf8'));
    assert.equal(bundled.formatVersion, 1);
});
