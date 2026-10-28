'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { PreviewPage } = require('../src/preview/PreviewPage');

const csp = 'https://file+.vscode-resource.vscode-cdn.net';

test('the page has a strict Content Security Policy and no scripts', () => {
    const page = PreviewPage.screen({ screenHtml: '<pre class="cvw-screen">x</pre>', problems: [], title: 'A', cspSource: csp });
    // The policy is an attribute value, so its quotes are encoded; the browser reads them as quotes
    const policy = /http-equiv="Content-Security-Policy" content="([^"]*)"/.exec(page)[1].replace(/&#39;/g, '\'');
    assert.equal(policy, `default-src 'none'; style-src 'unsafe-inline' ${csp}; img-src ${csp};`);
    assert.doesNotMatch(page, /<script/i);
});

test('only the tool\'s <pre> and its spans are kept', () => {
    const page = PreviewPage.screen({
        screenHtml: '<p>before</p><pre class="cvw-screen"><span style="color:#ff0000">red</span><img src=x onerror=alert(1)></pre><script>x</script>',
        problems: [], title: 'A', cspSource: csp
    });
    assert.match(page, /<pre class="cvw-screen"><span style="color:#ff0000">red<\/span>&lt;img src=x onerror=alert\(1\)><\/pre>/);
    assert.doesNotMatch(page, /<p>before|<script>x/);
});

test('a screen without a <pre> becomes an empty screen', () => {
    assert.equal(PreviewPage.onlyScreen('nothing'), '<pre class="cvw-screen"></pre>');
});

test('problems are listed and escaped', () => {
    const page = PreviewPage.screen({
        screenHtml: '<pre class="cvw-screen"></pre>', title: '<A>', cspSource: csp,
        problems: [
            { severity: 'error', line: 2, column: 3, code: 'CVW105', message: 'Unknown element <blink>' },
            { severity: 'note', line: null, column: null, code: null, message: 'A "note"' },
            { severity: 'evil" onclick="x', line: null, column: null, code: null, message: 'x' }
        ]
    });
    assert.match(page, /<li class="error">Line 2, column 3: CVW105 Unknown element &lt;blink&gt;<\/li>/);
    assert.match(page, /<li class="note">A &quot;note&quot;<\/li>/);
    assert.match(page, /<li class="note">x<\/li>/);
    assert.match(page, /<title>&lt;A&gt;<\/title>/);
});

test('a stale screen says so', () => {
    const page = PreviewPage.screen({ screenHtml: '<pre class="cvw-screen"></pre>', problems: [], title: 'A', cspSource: csp, stale: true });
    assert.match(page, /Showing the last preview/);
});

test('a message shows code between backticks and escapes the rest', () => {
    const page = PreviewPage.message({ title: 'A', cspSource: csp, heading: 'Not <installed>', lines: ['Run `dotnet tool install -g ConsoleMVC.Preview` & retry'] });
    assert.match(page, /<h2>Not &lt;installed&gt;<\/h2>/);
    assert.match(page, /<p>Run <code>dotnet tool install -g ConsoleMVC.Preview<\/code> &amp; retry<\/p>/);
});
