'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { MarkupSchema } = require('../src/schema/MarkupSchema');

/**
 * Shared test helpers: the bundled schema, and the cursor marker `|` for completion and hover tests.
 *
 * @example
 * const { text, offset } = cursor('<box bor|');
 */
const schema = MarkupSchema.load();

/**
 * Splits text at its `|` marker.
 *
 * @param {string} marked Text with one `|` where the cursor is.
 * @returns {{text: string, offset: number}} The text without the marker, and the cursor's offset.
 * @example
 * cursor('<te|'); // { text: '<te', offset: 3 }
 */
function cursor(marked) {
    const offset = marked.indexOf('|');
    if (offset < 0) {
        throw new Error('No | cursor marker in: ' + marked);
    }
    return { text: marked.slice(0, offset) + marked.slice(offset + 1), offset };
}

/**
 * Reads a test fixture.
 *
 * @param {string} name The file name in test/fixtures.
 * @returns {string} Its text.
 * @example
 * fixture('IndexView.cvw');
 */
function fixture(name) {
    return fs.readFileSync(path.join(__dirname, 'fixtures', name), 'utf8');
}

module.exports = { schema, cursor, fixture };
