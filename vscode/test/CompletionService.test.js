'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { CompletionService, CompletionKind } = require('../src/language/CompletionService');
const { schema, cursor } = require('./helpers');

const service = new CompletionService(schema);
const complete = marked => {
    const { text, offset } = cursor(marked);
    return service.complete(text, offset);
};
const labels = marked => complete(marked).map(i => i.label);

test('after < at the top level, top-level elements are offered', () => {
    const items = labels('<|');
    assert.ok(items.includes('box'));
    assert.ok(items.includes('menu'));
    assert.ok(!items.includes('row'));
});

test('inside an element, only the elements allowed there are offered', () => {
    assert.deepEqual(labels('<table>\n  <|\n</table>'), ['row']);
    assert.deepEqual(labels('<menu><|</menu>'), ['option']);
    assert.ok(!labels('<box><|</box>').includes('menu'));
});

test('a partly typed tag name is replaced', () => {
    const { text, offset } = cursor('<box><te|</box>');
    const item = service.complete(text, offset).find(i => i.label === 'text');
    assert.equal(text.slice(item.replaceStart, item.replaceEnd), 'te');
    assert.equal(item.kind, CompletionKind.Element);
});

test('element snippets close the element or self-close a void one', () => {
    const items = complete('<|');
    assert.equal(items.find(i => i.label === 'br').insertText, 'br />$0');
    assert.equal(items.find(i => i.label === 'text').insertText, 'text>$1</text>$0');
    assert.equal(items.find(i => i.label === 'box').insertText, 'box>\n\t$1\n</box>$0');
});

test('element snippets include required attributes once, not their aliases', () => {
    const partial = complete('<|').find(i => i.label === 'partial');
    assert.equal(partial.insertText, 'partial name="$1" />$0');
    const input = complete('<|').find(i => i.label === 'input');
    assert.match(input.insertText, /^input name="\$1"/);
});

test('in a start tag, attributes not written yet are offered', () => {
    const items = labels('<box border="double" |');
    assert.ok(items.includes('title'));
    assert.ok(!items.includes('border'));
});

test('a partly typed attribute is replaced, and flags have no value', () => {
    const { text, offset } = cursor('<text bo|');
    const bold = service.complete(text, offset).find(i => i.label === 'bold');
    assert.equal(bold.insertText, 'bold');
    assert.equal(text.slice(bold.replaceStart, bold.replaceEnd), 'bo');
    const border = complete('<box bo|').find(i => i.label === 'border');
    assert.equal(border.insertText, 'border="$1"$0');
});

test('required attributes sort first', () => {
    const items = complete('<input |');
    assert.equal(items[0].label, 'name');
});

test('inside quotes, the attribute\'s values are offered', () => {
    assert.deepEqual(labels('<box border="|"'), ['single', 'double', 'rounded', 'heavy', 'ascii', 'none']);
    assert.ok(labels('<text color="|">').includes('cyan'));
    assert.deepEqual(labels('<text wrap="|">'), ['true', 'false']);
});

test('a value replaces what is between the quotes', () => {
    const { text, offset } = cursor('<box border="dou|ble">');
    const item = service.complete(text, offset)[0];
    assert.equal(text.slice(item.replaceStart, item.replaceEnd), 'double');
});

test('values of text attributes offer nothing', () => {
    assert.deepEqual(labels('<box title="|"'), []);
});

test('after </ the innermost open element is closed', () => {
    const items = complete('<box><text>Hi</|');
    assert.deepEqual(items.map(i => i.label), ['</text>']);
    const { text, offset } = cursor('<box><text>Hi</te|></box>');
    const item = service.complete(text, offset)[0];
    assert.equal(text.slice(item.replaceStart, item.replaceEnd), '</te>');
});

test('after </ with everything closed, nothing is offered', () => {
    assert.deepEqual(labels('<text>x</text></|'), []);
});

test('directives are offered after @ at the start of a top-level line', () => {
    const items = labels('@|');
    assert.ok(items.includes('@model'));
    assert.ok(items.includes('@layout'));
    assert.ok(items.includes('@foreach'));
});

test('inside an element, @ offers constructs but not directives', () => {
    const items = labels('<box>\n@|\n</box>');
    assert.ok(items.includes('@foreach'));
    assert.ok(!items.includes('@model'));
});

test('nothing is offered inside comments, strings or after @@', () => {
    assert.deepEqual(labels('@* <| *@'), []);
    assert.deepEqual(labels('var s = "<|";'), []);
    assert.deepEqual(labels('<text>@@|</text>'), []);
});
