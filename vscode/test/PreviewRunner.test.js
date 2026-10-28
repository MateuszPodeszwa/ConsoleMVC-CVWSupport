'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { PreviewRunner, PreviewStatus } = require('../src/preview/PreviewRunner');

const fake = path.join(__dirname, 'fakes', 'fake-preview.js');

/** A runner whose command is the fake tool, run by this Node, in a FAKE_MODE. */
const runner = mode => new PreviewRunner('cvw-preview', (command, args, options) =>
    spawn(process.execPath, [fake, ...args], { ...options, env: { ...process.env, FAKE_MODE: mode } }));

test('the arguments name the file, read standard input and ask for HTML', () => {
    assert.deepEqual(PreviewRunner.arguments({ file: '/a b/IndexView.cvw', width: 100 }),
        ['/a b/IndexView.cvw', '--stdin', '--format', 'html', '--width', '100']);
    assert.deepEqual(PreviewRunner.arguments({ width: 80, highContrast: true }),
        ['--stdin', '--format', 'html', '--width', '80', '--high-contrast']);
});

test('the width is kept within the tool\'s range', () => {
    assert.equal(PreviewRunner.arguments({ file: 'a', width: 5 }).at(-1), '20');
    assert.equal(PreviewRunner.arguments({ file: 'a', width: 9999 }).at(-1), '500');
    assert.equal(PreviewRunner.arguments({ file: 'a', width: 'wide' }).at(-1), '80');
});

test('a drawn preview has its screen and problems; the text goes through standard input', async () => {
    const result = await runner('echo').run({ file: 'Views/Home/IndexView.cvw', text: '<text>Hi & "bye"</text>', width: 60 });
    assert.equal(result.status, PreviewStatus.Drawn);
    assert.equal(PreviewRunner.screen(result.html), '<pre class="cvw-screen">&lt;text&gt;Hi &amp; "bye"&lt;/text&gt;</pre>');
    assert.deepEqual(result.problems[0], {
        severity: 'error', line: 2, column: 3, code: 'CVW105', message: 'Unknown element <blink>. It\'s wrong'
    });
    assert.deepEqual(result.problems[1], {
        severity: 'note', line: null, column: null, code: null, message: 'The partial view \'Header\' was not found.'
    });
    assert.match(result.problems[2].message, /"Views\/Home\/IndexView.cvw","--stdin","--format","html","--width","60"/);
});

test('a missing command is NotInstalled', async () => {
    const result = await new PreviewRunner('cvw-preview-that-does-not-exist').run({ file: 'a.cvw', text: '', width: 80 });
    assert.equal(result.status, PreviewStatus.NotInstalled);
});

test('a failing tool reports its error', async () => {
    const result = await runner('fail').run({ file: 'x.cvw', text: '', width: 80 });
    assert.equal(result.status, PreviewStatus.Failed);
    assert.match(result.error, /cannot read/);
});

test('output that is not a preview is refused', async () => {
    const result = await runner('garbage').run({ file: 'x.cvw', text: '', width: 80 });
    assert.equal(result.status, PreviewStatus.Failed);
    assert.match(result.error, /something other than a preview/);
});

test('too much output is refused', async () => {
    const result = await runner('huge').run({ file: 'x.cvw', text: '', width: 80 });
    assert.equal(result.status, PreviewStatus.Failed);
    assert.match(result.error, /too large/);
});

test('a spawn that throws is a failure, not an exception', async () => {
    const throwing = new PreviewRunner('x', () => { throw new Error('boom'); });
    assert.deepEqual(await throwing.run({ file: 'a', text: '', width: 80 }), { status: PreviewStatus.Failed, error: 'boom' });
});

test('HTML entities are decoded', () => {
    assert.equal(PreviewRunner.decode('&lt;a&gt; &amp; &quot;b&quot; &#39;c&#39; &#x41;'), '<a> & "b" \'c\' A');
});

test('output without problems has none', () => {
    assert.deepEqual(PreviewRunner.parseProblems('<pre class="cvw-screen">x</pre>\n'), []);
});
