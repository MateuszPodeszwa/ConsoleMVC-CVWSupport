'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { HoverService } = require('../src/language/HoverService');
const { schema, cursor } = require('./helpers');

const service = new HoverService(schema);
const hover = marked => {
    const { text, offset } = cursor(marked);
    return { text, found: service.hover(text, offset) };
};

test('a tag name shows the element\'s documentation and its attributes', () => {
    const { text, found } = hover('<b|ox title="A"><text>x</text></box>');
    assert.match(found.markdown, /^\*\*`<box>`\*\* A bordered panel/);
    assert.match(found.markdown, /\| `border` \| The border style\. \|/);
    assert.equal(text.slice(found.start, found.end), 'box');
});

test('a closing tag shows the element too', () => {
    const { text, found } = hover('<text>x</te|xt>');
    assert.equal(text.slice(found.start, found.end), 'text');
});

test('an attribute name shows what it accepts', () => {
    const { text, found } = hover('<box bor|der="double" />');
    assert.match(found.markdown, /The border style/);
    assert.match(found.markdown, /\*Accepts:\* one of 'single'/);
    assert.equal(text.slice(found.start, found.end), 'border');
});

test('a flag says it is written without a value', () => {
    assert.match(hover('<text bo|ld>x</text>').found.markdown, /A flag/);
});

test('a directive shows its syntax', () => {
    const { found } = hover('@lay|out none\n<text>x</text>');
    assert.match(found.markdown, /`@layout/);
});

test('element restrictions are explained', () => {
    assert.match(hover('<table><r|ow /></table>').found.markdown, /Only inside:\* `<table>`/);
    assert.match(hover('<m|enu />').found.markdown, /Only at the top level/);
});

test('text, comments and unknown elements show nothing', () => {
    assert.equal(hover('<text>he|llo</text>').found, null);
    assert.equal(hover('@* <bo|x> *@').found, null);
    assert.equal(hover('<box><bl|ink /></box>').found, null);
});
