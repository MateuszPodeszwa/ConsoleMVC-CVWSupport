'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { compare } = require('../scripts/compare-with-generator');

// The schema checks against ConsoleMVC's own generator, through cvw-preview: over 7,000 generated views, every line
// where only one of them reports an error is a failure. Set CVW_PREVIEW to the tool's path to run it.
const tool = process.env.CVW_PREVIEW;

test('the schema checks report the same lines as the generator', { skip: tool ? false : 'set CVW_PREVIEW to a cvw-preview' }, () => {
    const { compared, differences } = compare(tool);
    assert.ok(compared > 5000);
    assert.deepEqual(differences, []);
});
