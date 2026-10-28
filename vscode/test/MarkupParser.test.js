'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { MarkupParser } = require('../src/markup/MarkupParser');
const { schema, fixture } = require('./helpers');

const parse = text => MarkupParser.parse(text, schema);
const tree = element => [element.name, element.children.map(tree)];

test('elements, attributes and children are read', () => {
    const document = parse('<box title="A" border="double"><text bold>Hi</text></box>');
    const [box] = document.roots;
    assert.equal(box.name, 'box');
    assert.deepEqual(box.attributes.map(a => [a.name, a.value]), [['title', 'A'], ['border', 'double']]);
    assert.equal(box.children[0].name, 'text');
    assert.equal(box.children[0].attribute('bold').value, null);
    assert.ok(box.isClosed);
});

test('self-closing elements end at />', () => {
    const [br, text] = parse('<br /><text>x</text>').roots;
    assert.ok(br.selfClosing);
    assert.equal(text.name, 'text');
});

test('directives do not stop markup from starting', () => {
    const document = parse('@model Shop.Models.Cart\n@layout none\n<text>x</text>');
    assert.deepEqual(document.roots.map(e => e.name), ['text']);
});

test('at the top level, markup starts only where a statement may begin', () => {
    const document = parse('var list = new List<text>();\nConsole.Write("x");\n<text>real</text>');
    assert.deepEqual(document.roots.map(e => e.name), ['text']);
    assert.equal(document.roots[0].start, document.text.indexOf('<text>real'));
});

test('unknown tags at the top level are C#, inside elements they are elements', () => {
    assert.deepEqual(parse('<blink>x</blink>').roots, []);
    const [box] = parse('<box><blink>x</blink></box>').roots;
    assert.equal(box.children[0].name, 'blink');
    assert.equal(box.children[0].known, false);
});

test('markup inside @foreach and @if belongs to the enclosing element', () => {
    const view = '<table>\n@foreach (var x in Model.Items)\n{\n  <row><cell>@x</cell></row>\n}\n@if (a) { <row /> } else { <row /> }\n</table>';
    const [table] = parse(view).roots;
    assert.deepEqual(table.children.map(e => e.name), ['row', 'row', 'row']);
    assert.ok(table.isClosed);
});

test('code blocks with braces and strings do not end early', () => {
    const view = '<box>@{ var s = "}"; if (x) { y(); } }<text>after</text></box>';
    const [box] = parse(view).roots;
    assert.deepEqual(box.children.map(e => e.name), ['text']);
});

test('quotes inside an attribute expression do not end the value', () => {
    const [box] = parse('<box title="@L["Title"]" border="rounded"><text>x</text></box>').roots;
    assert.equal(box.attribute('title').value, '@L["Title"]');
    assert.equal(box.attribute('border').value, 'rounded');
});

test('an unclosed element runs to its parent\'s closing tag', () => {
    const [box] = parse('<box><text>x</box><br />').roots;
    assert.equal(box.children[0].isClosed, false);
    assert.ok(box.isClosed);
});

test('an unfinished start tag is kept, for completion', () => {
    const [box] = parse('<box bor').roots;
    assert.equal(box.startTagEnd, -1);
    assert.deepEqual(box.attributes.map(a => a.name), ['bor']);
});

test('comments and strings are opaque', () => {
    const text = '@* <text>no</text> *@\nvar s = "<text>no</text>";\n<text>yes</text>';
    const document = parse(text);
    assert.deepEqual(document.roots.map(e => e.name), ['text']);
    assert.ok(document.isOpaque(text.indexOf('no')));
    assert.ok(document.isOpaque(text.indexOf('"<text>') + 2));
    assert.ok(!document.isOpaque(text.indexOf('yes')));
});

test('openElementsAt lists the elements around an offset', () => {
    const text = '<table><row><cell>x</cell></row></table>';
    const names = parse(text).openElementsAt(text.indexOf('x')).map(e => e.name);
    assert.deepEqual(names, ['table', 'row', 'cell']);
    assert.deepEqual(parse(text).openElementsAt(text.length), []);
});

test('startTagAt finds the start tag around an offset', () => {
    const text = '<box border="double"><text>x</text></box>';
    assert.equal(parse(text).startTagAt(text.indexOf('border')).name, 'box');
    assert.equal(parse(text).startTagAt(text.indexOf('x')), undefined);
});

test('text and expressions written directly in content are recorded', () => {
    const [table] = parse('<table>oops @x<row><cell>fine</cell></row></table>').roots;
    assert.equal(table.textRanges.length, 1);
    assert.equal(table.children[0].textRanges.length, 0);
});

test('an e-mail address is text, not an expression', () => {
    const [text] = parse('<text>me@example.com</text>').roots;
    assert.ok(text.isClosed);
    assert.equal(text.textRanges.length, 1);
});

test('the template\'s views parse completely', () => {
    for (const name of ['FeaturesView.cvw', 'StatusView.cvw', 'GreetView.cvw', '_Layout.cvw']) {
        const elements = parse(fixture(name)).allElements();
        assert.ok(elements.length > 3, name);
        assert.ok(elements.every(e => e.known && (e.isClosed || e.selfClosing)), name);
    }
    const [, , scroll] = parse(fixture('FeaturesView.cvw')).roots;
    assert.deepEqual(tree(scroll), ['scroll', [['table', [['row', [['cell', []], ['cell', []]]], ['row', [['cell', []], ['cell', []]]]]]]]);
});

test('deep nesting stops without overflowing', () => {
    const text = '<box>'.repeat(5000);
    assert.doesNotThrow(() => parse(text));
});
