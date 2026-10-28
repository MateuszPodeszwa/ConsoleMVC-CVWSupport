'use strict';

const fs = require('node:fs');
const path = require('node:path');
const textmate = require('vscode-textmate');
const oniguruma = require('vscode-oniguruma');
const { SCOPE_NAME } = require('../../src/grammar/GrammarBuilder');

/**
 * Tokenises views with the extension's grammar, as VS Code does (vscode-textmate with the Oniguruma engine).
 * C# comes from a stand-in grammar, or from the file named by CSHARP_GRAMMAR.
 *
 * @example
 * const lines = await tokenize('<box title="A" />');
 * scopesOf(lines, 'box'); // ['source.cvw', 'meta.tag.cvw', 'entity.name.tag.cvw']
 */
const root = path.join(__dirname, '..', '..');
let registryPromise;

/** The Oniguruma engine, loaded once. */
const onigLib = (async () => {
    const wasm = fs.readFileSync(require.resolve('vscode-oniguruma/release/onig.wasm'));
    await oniguruma.loadWASM(wasm.buffer.slice(wasm.byteOffset, wasm.byteOffset + wasm.byteLength));
    return {
        createOnigScanner: sources => new oniguruma.OnigScanner(sources),
        createOnigString: text => new oniguruma.OnigString(text)
    };
})();

function registry() {
    registryPromise ??= Promise.resolve(new textmate.Registry({
        onigLib,
        loadGrammar: async scopeName => {
            const files = {
                [SCOPE_NAME]: path.join(root, 'syntaxes', 'cvw.tmLanguage.json'),
                'source.cs': process.env.CSHARP_GRAMMAR || path.join(__dirname, '..', 'fixtures', 'csharp-stub.tmLanguage.json')
            };
            const file = files[scopeName];
            return file ? textmate.parseRawGrammar(fs.readFileSync(file, 'utf8'), file) : null;
        }
    }));
    return registryPromise;
}

/**
 * Tokenises text.
 *
 * @param {string} text A view.
 * @returns {Promise<{text: string, scopes: string[]}[][]>} The tokens of each line.
 * @example
 * const [first] = await tokenize('@model X');
 */
async function tokenize(text) {
    const grammar = await (await registry()).loadGrammar(SCOPE_NAME);
    let state = textmate.INITIAL;
    return text.split('\n').map(line => {
        const result = grammar.tokenizeLine(line, state);
        state = result.ruleStack;
        return result.tokens.map(t => ({ text: line.slice(t.startIndex, t.endIndex), scopes: t.scopes }));
    });
}

/**
 * The scopes of the first token whose text is exactly `text` (searching all lines).
 *
 * @param {{text: string, scopes: string[]}[][]} lines Tokenised lines.
 * @param {string} text The token's text.
 * @param {number} [nth] Which match, from 0.
 * @returns {string[]} The scopes.
 * @example
 * scopesOf(lines, 'title');
 */
function scopesOf(lines, text, nth = 0) {
    const matches = lines.flat().filter(t => t.text === text);
    if (!matches[nth]) {
        throw new Error(`No token '${text}' in: ${JSON.stringify(lines.flat().map(t => t.text))}`);
    }
    return matches[nth].scopes;
}

module.exports = { tokenize, scopesOf };
