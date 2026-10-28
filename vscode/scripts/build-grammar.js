'use strict';

/**
 * Writes syntaxes/cvw.tmLanguage.json and language-configuration.json from schema/cvw-schema.json.
 * Run it after `npm run sync-schema`: `npm run build-grammar`. With --check it only reports whether they are current
 * (exit code 1 when not), for CI.
 *
 * @example
 * node scripts/build-grammar.js          # writes both files
 * node scripts/build-grammar.js --check  # fails when they are out of date
 */

const fs = require('node:fs');
const path = require('node:path');
const { MarkupSchema } = require('../src/schema/MarkupSchema');
const { GrammarBuilder } = require('../src/grammar/GrammarBuilder');
const { LanguageConfigurationBuilder } = require('../src/grammar/LanguageConfigurationBuilder');

const root = path.join(__dirname, '..');
const schema = MarkupSchema.load();
const outputs = [
    [path.join(root, 'syntaxes', 'cvw.tmLanguage.json'), GrammarBuilder.toJson(GrammarBuilder.build(schema))],
    [path.join(root, 'language-configuration.json'), JSON.stringify(LanguageConfigurationBuilder.build(schema), null, 4) + '\n']
];

const check = process.argv.includes('--check');
let stale = false;
for (const [file, text] of outputs) {
    const current = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : null;
    if (current === text) {
        continue;
    }
    if (check) {
        console.error(`${path.relative(root, file)} is out of date: run npm run build-grammar`);
        stale = true;
    } else {
        fs.writeFileSync(file, text);
        console.log(`Wrote ${path.relative(root, file)}`);
    }
}
process.exitCode = stale ? 1 : 0;
