'use strict';

/**
 * Builds language-configuration.json from the markup schema: comments, brackets, and the indentation, Enter and
 * folding rules, which must know the void elements (written `<name />`, never indented after).
 * Run by scripts/build-grammar.js together with the grammar.
 *
 * @example
 * const configuration = LanguageConfigurationBuilder.build(MarkupSchema.load());
 * configuration.comments.blockComment; // ['@*', '*@']
 */
class LanguageConfigurationBuilder {
    /**
     * Builds the configuration.
     *
     * @param {import('../schema/MarkupSchema').MarkupSchema} schema The markup schema.
     * @returns {object} The configuration, ready to be written as JSON.
     * @example
     * LanguageConfigurationBuilder.build(schema).onEnterRules.length; // 2
     */
    static build(schema) {
        const voids = schema.elements.filter(e => e.content === 'void').map(e => e.name).join('|');
        // A start tag that opens content: not a void element, not self-closing, not a closing tag
        const openTag = `<(?!/)(?!(?:${voids})(?![\\w-]))[\\w-]+(?:[^/>"]|"[^"]*")*>\\s*$`;

        return {
            comments: { blockComment: ['@*', '*@'] },
            brackets: [['{', '}'], ['[', ']'], ['(', ')']],
            autoClosingPairs: [
                { open: '{', close: '}' },
                { open: '[', close: ']' },
                { open: '(', close: ')' },
                { open: '"', close: '"', notIn: ['string', 'comment'] },
                { open: '@*', close: ' *@', notIn: ['string', 'comment'] }
            ],
            surroundingPairs: [['{', '}'], ['[', ']'], ['(', ')'], ['"', '"'], ['\'', '\''], ['<', '>']],
            wordPattern: '(-?\\d*\\.\\d\\w*)|([^\\`\\~\\!\\%\\^\\&\\*\\(\\)\\=\\+\\[\\{\\]\\}\\\\\\|\;\\:\\\'\\"\\,\\.\\<\\>\\/\\?\\s]+)',
            onEnterRules: [
                { beforeText: openTag, afterText: '^\\s*</[\\w-]+\\s*>', action: { indent: 'indentOutdent' } },
                { beforeText: openTag, action: { indent: 'indent' } }
            ],
            indentationRules: {
                increaseIndentPattern: `(\\{[^}"']*$)|(${openTag})`,
                decreaseIndentPattern: '^\\s*(\\}|</[\\w-]+\\s*>)'
            },
            folding: {
                markers: {
                    start: `^\\s*(${openTag}|@\\{|@\\*)`,
                    end: '^\\s*(</[\\w-]+\\s*>|\\}|\\*@)'
                }
            }
        };
    }
}

module.exports = { LanguageConfigurationBuilder };
