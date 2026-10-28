'use strict';

/**
 * Stands in for cvw-preview in tests. FAKE_MODE chooses what it does:
 * echo (default): draws the input as the screen, lists one error and one note, and echoes its arguments;
 * fail: exits with code 2; garbage: writes something that is not a preview; slow: never finishes;
 * huge: writes more than the runner reads.
 *
 * @example
 * spawn(process.execPath, [fakePreviewPath, 'IndexView.cvw', '--stdin'], { env: { FAKE_MODE: 'fail' } });
 */
const mode = process.env.FAKE_MODE || 'echo';
const escape = text => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/'/g, '&#39;');

let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => { input += chunk; });
process.stdin.on('end', () => {
    switch (mode) {
        case 'fail':
            process.stderr.write("cvw-preview: cannot read 'x.cvw': not found\n");
            process.exit(2);
            break;
        case 'garbage':
            process.stdout.write('Usage: something else\n');
            break;
        case 'slow':
            setInterval(() => {}, 1000);
            break;
        case 'huge':
            process.stdout.write('<pre class="cvw-screen">' + 'x'.repeat(9 * 1024 * 1024) + '</pre>\n');
            break;
        default:
            process.stdout.write(`<pre class="cvw-screen">${escape(input)}</pre>\n`);
            process.stdout.write('<ul class="cvw-problems">\n');
            process.stdout.write('<li class="cvw-error" data-line="2" data-column="3">Line 2, column 3: CVW105 Unknown element &lt;blink&gt;. It&#39;s wrong</li>\n');
            process.stdout.write('<li class="cvw-note">The partial view &#39;Header&#39; was not found.</li>\n');
            process.stdout.write(`<li class="cvw-note">args ${escape(JSON.stringify(process.argv.slice(2)))}</li>\n`);
            process.stdout.write('</ul>\n');
    }
});
