'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { ToolLocator, DEFAULT_COMMAND } = require('../src/preview/ToolLocator');
const { ProblemRange } = require('../src/language/ProblemRange');

test('the default command is also looked for in the .NET tools folder', () => {
    assert.deepEqual(ToolLocator.candidates(DEFAULT_COMMAND, { platform: 'linux', home: '/home/me' }),
        ['cvw-preview', '/home/me/.dotnet/tools/cvw-preview']);
    assert.deepEqual(ToolLocator.candidates('', { platform: 'win32', home: 'C:\\Users\\me' }),
        ['cvw-preview', 'C:\\Users\\me\\.dotnet\\tools\\cvw-preview.exe']);
});

test('a configured path is used alone', () => {
    assert.deepEqual(ToolLocator.candidates(' /opt/cvw-preview '), ['/opt/cvw-preview']);
});

test('a problem\'s range covers the tag, attribute or word at its position', () => {
    const text = '<box>\n  <blink />\n<text bold="x">';
    const tag = ProblemRange.fromLineColumn(text, 2, 3);
    assert.equal(text.slice(tag.start, tag.end), '<blink');
    const attribute = ProblemRange.fromLineColumn(text, 3, 7);
    assert.equal(text.slice(attribute.start, attribute.end), 'bold');
});

test('positions past the text are clamped', () => {
    assert.equal(ProblemRange.offsetOf('ab\ncd', 9, 1), 5);
    assert.equal(ProblemRange.offsetOf('ab\ncd', 1, 99), 2);
    const range = ProblemRange.fromLineColumn('', 1, 1);
    assert.deepEqual(range, { start: 0, end: 0 });
});
