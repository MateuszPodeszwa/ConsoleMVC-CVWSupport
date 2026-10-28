'use strict';

/**
 * Copies the C-View Markup schema from a ConsoleMVC checkout into schema/cvw-schema.json, then rebuilds the grammar.
 * The schema is ConsoleMVC's schema/cvw-schema.json, written from the source generator, so the extension knows exactly
 * the elements of that framework version.
 *
 * @example
 * node scripts/sync-schema.js ../../ConsoleMVC                 # a checkout next to this repository
 * node scripts/sync-schema.js ../../ConsoleMVC/schema/cvw-schema.json
 */

const fs = require('node:fs');
const path = require('node:path');
const { MarkupSchema } = require('../src/schema/MarkupSchema');

const source = process.argv[2];
if (!source) {
    console.error('Usage: node scripts/sync-schema.js <ConsoleMVC checkout or cvw-schema.json>');
    process.exit(1);
}

const file = fs.statSync(source).isDirectory() ? path.join(source, 'schema', 'cvw-schema.json') : source;
const text = fs.readFileSync(file, 'utf8');
new MarkupSchema(JSON.parse(text)); // refuses a file that is not a schema, or of a newer format

const target = path.join(__dirname, '..', 'schema', 'cvw-schema.json');
fs.writeFileSync(target, text);
console.log(`Copied ${file} to ${path.relative(process.cwd(), target)}`);
require('./build-grammar');
