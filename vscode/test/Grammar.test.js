'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const oniguruma = require('vscode-oniguruma');
const { GrammarBuilder } = require('../src/grammar/GrammarBuilder');
const { LanguageConfigurationBuilder } = require('../src/grammar/LanguageConfigurationBuilder');
const { tokenize, scopesOf } = require('./fakes/tokenizer');
const { schema, fixture } = require('./helpers');

const root = path.join(__dirname, '..');
const has = (scopes, scope) => scopes.some(s => s === scope || s.startsWith(scope + '.'));

test('the grammar and language configuration are current with the schema (npm run build-grammar)', () => {
    assert.equal(fs.readFileSync(path.join(root, 'syntaxes', 'cvw.tmLanguage.json'), 'utf8'), GrammarBuilder.toJson(GrammarBuilder.build(schema)));
    assert.equal(fs.readFileSync(path.join(root, 'language-configuration.json'), 'utf8'),
        JSON.stringify(LanguageConfigurationBuilder.build(schema), null, 4) + '\n');
});

test('every element has a rule', () => {
    const grammar = GrammarBuilder.build(schema);
    for (const element of schema.elements) {
        assert.ok(grammar.repository[GrammarBuilder.elementRuleName(element.name)], element.name);
    }
});

test('every regular expression compiles in Oniguruma', async () => {
    await tokenize(''); // loads the engine
    const patterns = [];
    const visit = node => {
        if (Array.isArray(node)) {
            node.forEach(visit);
        } else if (node && typeof node === 'object') {
            for (const key of ['match', 'begin', 'end']) {
                if (typeof node[key] === 'string') {
                    patterns.push(node[key]);
                }
            }
            Object.values(node).forEach(visit);
        }
    };
    visit(GrammarBuilder.build(schema));
    const configuration = LanguageConfigurationBuilder.build(schema);
    for (const rule of configuration.onEnterRules) {
        assert.doesNotThrow(() => new RegExp(rule.beforeText));
    }
    for (const pattern of patterns) {
        assert.doesNotThrow(() => new oniguruma.OnigScanner([pattern]).dispose(), pattern);
    }
    assert.ok(patterns.length > 50);
});

test('tags, attributes and values have their scopes', async () => {
    const lines = await tokenize('<box title="Orders" border="double">');
    assert.ok(has(scopesOf(lines, '<'), 'punctuation.definition.tag.begin.cvw'));
    assert.ok(has(scopesOf(lines, 'box'), 'entity.name.tag.cvw'));
    assert.ok(has(scopesOf(lines, 'title'), 'entity.other.attribute-name.cvw'));
    assert.ok(has(scopesOf(lines, 'Orders'), 'string.quoted.double.cvw'));
});

test('text inside an element is content, not C#', async () => {
    const lines = await tokenize('<text>Hello world</text>');
    const scopes = scopesOf(lines, 'Hello world');
    assert.ok(has(scopes, 'meta.content.cvw'));
    assert.ok(!scopes.some(s => s.endsWith('.cs')));
});

test('expressions in content and attributes are C#, after a transition', async () => {
    const lines = await tokenize('<text color="@Model.Colour">Hi @Model.Name and @(a + b)</text>');
    assert.ok(has(scopesOf(lines, '@', 0), 'keyword.control.cvw.transition'));
    assert.ok(has(scopesOf(lines, 'Model', 1), 'meta.embedded.expression.cs'));
    assert.ok(has(scopesOf(lines, 'a'), 'meta.embedded.expression.cs'));
});

test('an e-mail address is not an expression', async () => {
    const lines = await tokenize('<text>me@example.com</text>');
    assert.ok(lines.flat().every(t => !has(t.scopes, 'keyword.control.cvw.transition')));
});

test('@@ is an escape and entities are constants', async () => {
    const lines = await tokenize('<text>@@ &lt;</text>');
    assert.ok(has(scopesOf(lines, '@@'), 'constant.character.escape.cvw'));
    assert.ok(has(scopesOf(lines, '&lt;'), 'constant.character.entity.cvw'));
});

test('comments are comments, across lines', async () => {
    const lines = await tokenize('@* one\n<text>two</text> *@\n<text>three</text>');
    assert.ok(has(scopesOf(lines, '<text>two</text> '), 'comment.block.cvw'));
    assert.ok(has(scopesOf(lines, 'text', 0), 'entity.name.tag.cvw'));
});

test('directives are keywords with their argument', async () => {
    const lines = await tokenize('@model Shop.Models.Cart\n@inject IClock Clock\n@layout none\n@refresh 5s');
    assert.ok(has(scopesOf(lines, 'model'), 'keyword.control.directive.cvw'));
    assert.ok(has(scopesOf(lines, 'Shop.Models.Cart'), 'entity.name.type.cvw'));
    assert.ok(has(scopesOf(lines, 'Clock'), 'variable.other.readwrite.cvw'));
    assert.ok(has(scopesOf(lines, '5s'), 'constant.numeric.duration.cvw'));
});

test('markup inside @foreach is still markup', async () => {
    const lines = await tokenize('<table>\n@foreach (var row in Model.Rows)\n{\n    <row><cell>@row.Name</cell></row>\n}\n</table>');
    assert.ok(has(scopesOf(lines, 'foreach'), 'keyword.control.flow.cs'));
    assert.ok(has(scopesOf(lines, 'row', 1), 'entity.name.tag.cvw')); // 0 is the loop variable
    assert.ok(has(scopesOf(lines, 'table', 1), 'entity.name.tag.cvw'));
});

test('markup inside top-level C# braces is still markup', async () => {
    const lines = await tokenize('if (Model.Ready)\n{\n    <text>Ready</text>\n}\nreturn NavigationResult.Quit();');
    assert.ok(has(scopesOf(lines, 'text', 0), 'entity.name.tag.cvw'));
    assert.ok(has(scopesOf(lines, 'return'), 'keyword.control.flow.cs'));
});

test('a nested element of the same name closes at its own tag', async () => {
    const lines = await tokenize('<box><box><text>in</text></box>after</box> outside');
    assert.ok(has(scopesOf(lines, 'after'), 'meta.content.cvw'));
    assert.ok(!has(scopesOf(lines, 'outside'), 'meta.content.cvw'));
});

test('a void element does not open content', async () => {
    const lines = await tokenize('<br /> x');
    assert.ok(!has(scopesOf(lines, 'x'), 'meta.content.cvw'));
});

test('an unknown tag inside an element is marked invalid', async () => {
    const lines = await tokenize('<box><blink /></box>');
    assert.ok(has(scopesOf(lines, 'blink'), 'invalid.illegal.unknown-element.cvw'));
});

test('a longer element name is not taken for a shorter one', async () => {
    const lines = await tokenize('<bar-chart><bar label="a" value="1" /></bar-chart>');
    assert.ok(has(scopesOf(lines, 'bar-chart', 0), 'entity.name.tag.cvw'));
    assert.ok(has(scopesOf(lines, 'bar'), 'entity.name.tag.cvw'));
});

test('the template\'s views end where they started: nothing is left open', async () => {
    for (const name of ['FeaturesView.cvw', 'StatusView.cvw', 'GreetView.cvw', '_Layout.cvw']) {
        const lines = await tokenize(fixture(name) + '\n<text>end</text>');
        const end = scopesOf(lines, 'end');
        assert.deepEqual(end.filter(s => s !== 'meta.content.cvw'), ['source.cvw'], name);
        assert.ok(!lines.flat().some(t => has(t.scopes, 'invalid')), name);
    }
});
