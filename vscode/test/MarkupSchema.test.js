'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { MarkupSchema, SUPPORTED_FORMAT_VERSION } = require('../src/schema/MarkupSchema');
const { schema } = require('./helpers');

test('the bundled schema loads and describes the elements', () => {
    assert.ok(schema.elements.length > 20);
    assert.equal(schema.element('box').content, 'block');
    assert.deepEqual(schema.attribute('box', 'border').values, ['single', 'double', 'rounded', 'heavy', 'ascii', 'none']);
    assert.equal(schema.element('nope'), undefined);
});

test('directives are found with or without the @', () => {
    assert.equal(schema.directive('layout').name, '@layout');
    assert.equal(schema.directive('@model').name, '@model');
});

test('diagnostics are found by id', () => {
    assert.equal(schema.diagnosticById('CVW105').severity, 'error');
});

test('the top level allows elements without a required parent, including top-level-only ones', () => {
    const names = schema.elementsAllowedIn(null).map(e => e.name);
    assert.ok(names.includes('menu'));
    assert.ok(names.includes('box'));
    assert.ok(!names.includes('row'));
    assert.ok(!names.includes('option'));
});

test('a block element allows other elements but not top-level-only ones', () => {
    const names = schema.elementsAllowedIn('box').map(e => e.name);
    assert.ok(names.includes('text'));
    assert.ok(names.includes('table'));
    assert.ok(!names.includes('menu'));
    assert.ok(!names.includes('row'));
});

test('an element with listed children allows only those', () => {
    assert.deepEqual(schema.elementsAllowedIn('table').map(e => e.name), ['row']);
    assert.deepEqual(schema.elementsAllowedIn('select').map(e => e.name), ['choice']);
});

test('inline content allows only line breaks; void content nothing', () => {
    assert.deepEqual(schema.elementsAllowedIn('text').map(e => e.name), ['br']);
    assert.deepEqual(schema.elementsAllowedIn('br'), []);
});

test('a schema of a newer format is refused', () => {
    assert.throws(() => new MarkupSchema({ formatVersion: SUPPORTED_FORMAT_VERSION + 1, elements: [] }), /newer/);
});

test('something that is not a schema is refused', () => {
    assert.throws(() => new MarkupSchema({ hello: 1 }), /Not a C-View Markup schema/);
    assert.throws(() => new MarkupSchema(null), /Not a C-View Markup schema/);
});
