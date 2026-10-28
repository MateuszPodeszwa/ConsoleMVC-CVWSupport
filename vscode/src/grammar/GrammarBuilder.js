'use strict';

/**
 * The TextMate scope of a view, used by the grammar, the language configuration and tests.
 *
 * @example
 * registry.loadGrammar(SCOPE_NAME);
 */
const SCOPE_NAME = 'source.cvw';

/** The C# keywords that start a block of code inside markup, such as `@foreach (…) { … }`. */
const CONTROL_KEYWORDS = ['if', 'foreach', 'for', 'while', 'switch', 'lock', 'using', 'do', 'try'];

/** An @ that starts code: not the @ of an e-mail address such as me@example.com. */
const TRANSITION = '(?<![\\p{L}\\p{Nd}@])(@)';

/**
 * Builds the TextMate grammar of `.cvw` views from the markup schema, so highlighting knows exactly the elements the
 * generator knows. Run by scripts/build-grammar.js; a test checks that syntaxes/cvw.tmLanguage.json is current.
 *
 * Structure:
 * ```text
 * view          → directive | csharp-region
 * csharp-region → comments | elements | @-constructs | { csharp-region } | C# (from source.cs)
 * element       → <name attributes ( /> | > content </name> )     one rule per element, from the schema
 * content       → comments | elements | @expression | @(…) | @{ csharp-region } | @if (…) { csharp-region } | entities | text
 * ```
 * C# outside markup uses the C# grammar's expressions and comments, but braces are the grammar's own, so markup
 * inside `if (…) { … }` is still highlighted as markup.
 *
 * @example
 * const grammar = GrammarBuilder.build(MarkupSchema.load());
 * fs.writeFileSync('syntaxes/cvw.tmLanguage.json', GrammarBuilder.toJson(grammar));
 */
class GrammarBuilder {
    /**
     * Builds the grammar.
     *
     * @param {import('../schema/MarkupSchema').MarkupSchema} schema The markup schema.
     * @returns {object} The grammar, ready to be written as JSON.
     * @example
     * GrammarBuilder.build(schema).repository['element-box'].begin; // '(<)(box)(?![\\w-])'
     */
    static build(schema) {
        const repository = {
            'csharp-region': {
                patterns: [
                    { include: '#cvw-comment' },
                    { include: '#markup-comment' },
                    { include: '#elements' },
                    { include: '#at-constructs' },
                    { include: '#csharp-block' },
                    { include: '#csharp-keywords' },
                    { include: 'source.cs#comment' },
                    { include: 'source.cs#preprocessor' },
                    { include: 'source.cs#expression' },
                    { include: 'source.cs#punctuation-semicolon' }
                ]
            },
            'csharp-block': {
                begin: '\\{',
                beginCaptures: { 0: { name: 'punctuation.curlybrace.open.cs' } },
                end: '\\}',
                endCaptures: { 0: { name: 'punctuation.curlybrace.close.cs' } },
                patterns: [{ include: '#csharp-region' }]
            },
            'csharp-parens': {
                begin: '\\(',
                beginCaptures: { 0: { name: 'punctuation.parenthesis.open.cs' } },
                end: '\\)',
                endCaptures: { 0: { name: 'punctuation.parenthesis.close.cs' } },
                patterns: [{ include: '#csharp-parens' }, { include: '#csharp-keywords' }, { include: 'source.cs#expression' }]
            },
            'csharp-keywords': {
                patterns: [
                    {
                        match: '\\b(if|else|switch|case|default|for|foreach|in|while|do|break|continue|return|goto|try|catch|finally|throw|yield|lock|using)\\b',
                        name: 'keyword.control.flow.cs'
                    },
                    { match: '\\b(var|const|static|readonly|ref|out)\\b', name: 'storage.modifier.cs' }
                ]
            },
            directives: {
                patterns: [
                    {
                        match: '^\\s*(@)(model|using)\\b\\s*(.*)$',
                        captures: GrammarBuilder.captures('keyword.control.cvw.transition', 'keyword.control.directive.cvw', 'entity.name.type.cvw')
                    },
                    {
                        match: '^\\s*(@)(inject)\\b\\s*(?:(.+?)\\s+([\\p{L}_][\\p{L}\\p{Nd}_]*)\\s*$|(.*)$)',
                        captures: GrammarBuilder.captures('keyword.control.cvw.transition', 'keyword.control.directive.cvw',
                            'entity.name.type.cvw', 'variable.other.readwrite.cvw', 'entity.name.type.cvw')
                    },
                    {
                        match: '^\\s*(@)(layout)\\b\\s*(.*)$',
                        captures: GrammarBuilder.captures('keyword.control.cvw.transition', 'keyword.control.directive.cvw', 'entity.name.section.layout.cvw')
                    },
                    {
                        match: '^\\s*(@)(refresh)\\b\\s*(.*)$',
                        captures: GrammarBuilder.captures('keyword.control.cvw.transition', 'keyword.control.directive.cvw', 'constant.numeric.duration.cvw')
                    }
                ]
            },
            'cvw-comment': {
                begin: '@\\*',
                beginCaptures: { 0: { name: 'punctuation.definition.comment.begin.cvw' } },
                end: '\\*@',
                endCaptures: { 0: { name: 'punctuation.definition.comment.end.cvw' } },
                name: 'comment.block.cvw'
            },
            'markup-comment': {
                begin: '<!--',
                beginCaptures: { 0: { name: 'punctuation.definition.comment.begin.cvw' } },
                end: '-->',
                endCaptures: { 0: { name: 'punctuation.definition.comment.end.cvw' } },
                name: 'comment.block.markup.cvw'
            },
            'at-constructs': {
                patterns: [
                    { match: '@@', name: 'constant.character.escape.cvw' },
                    { include: '#code-block' },
                    { include: '#control-flow' },
                    { include: '#explicit-expression' },
                    { include: '#implicit-expression' }
                ]
            },
            'code-block': {
                begin: TRANSITION + '(\\{)',
                beginCaptures: GrammarBuilder.captures('keyword.control.cvw.transition', 'punctuation.curlybrace.open.cs'),
                end: '\\}',
                endCaptures: { 0: { name: 'punctuation.curlybrace.close.cs' } },
                contentName: 'meta.embedded.block.cs',
                patterns: [{ include: '#csharp-region' }]
            },
            'control-flow': {
                begin: `${TRANSITION}(${CONTROL_KEYWORDS.join('|')})\\b`,
                beginCaptures: GrammarBuilder.captures('keyword.control.cvw.transition', 'keyword.control.flow.cs'),
                end: '(?<=\\})(?!\\s*(?:else|catch|finally|while)\\b)',
                patterns: [
                    { match: '\\b(else|if|catch|finally|while)\\b', name: 'keyword.control.flow.cs' },
                    { include: '#csharp-parens' },
                    { include: '#csharp-block' }
                ]
            },
            'else-continuation': {
                begin: '^\\s*(else|catch|finally)\\b',
                beginCaptures: { 1: { name: 'keyword.control.flow.cs' } },
                end: '(?<=\\})',
                patterns: [
                    { match: '\\bif\\b', name: 'keyword.control.flow.cs' },
                    { include: '#csharp-parens' },
                    { include: '#csharp-block' }
                ]
            },
            'explicit-expression': {
                begin: TRANSITION + '(\\()',
                beginCaptures: GrammarBuilder.captures('keyword.control.cvw.transition', 'punctuation.parenthesis.open.cs'),
                end: '\\)',
                endCaptures: { 0: { name: 'punctuation.parenthesis.close.cs' } },
                contentName: 'meta.embedded.expression.cs',
                patterns: [{ include: '#csharp-parens' }, { include: 'source.cs#expression' }]
            },
            'implicit-expression': {
                match: TRANSITION + '([\\p{L}_][\\p{L}\\p{Nd}_]*(?:\\??\\.[\\p{L}_][\\p{L}\\p{Nd}_]*|\\[[^\\]\\n]*\\]|\\([^)\\n]*\\))*)',
                captures: {
                    1: { name: 'keyword.control.cvw.transition' },
                    2: { name: 'meta.embedded.expression.cs', patterns: [{ include: 'source.cs#expression' }] }
                }
            },
            entity: {
                match: '&(?:[A-Za-z][A-Za-z0-9]*|#[0-9]+|#x[0-9A-Fa-f]+);',
                name: 'constant.character.entity.cvw'
            },
            attributes: {
                patterns: [
                    {
                        match: '([A-Za-z_][\\w\\-:.]*)\\s*(=)?',
                        captures: GrammarBuilder.captures('entity.other.attribute-name.cvw', 'punctuation.separator.key-value.cvw')
                    },
                    GrammarBuilder.attributeString('"', 'double'),
                    GrammarBuilder.attributeString('\'', 'single'),
                    { include: '#implicit-expression' },
                    { include: '#explicit-expression' }
                ]
            },
            content: {
                patterns: [
                    { include: '#cvw-comment' },
                    { include: '#markup-comment' },
                    { include: '#elements' },
                    { include: '#unknown-tag' },
                    { include: '#else-continuation' },
                    { include: '#at-constructs' },
                    { include: '#entity' }
                ]
            },
            'unknown-tag': {
                match: '(</?)([A-Za-z][\\w-]*)',
                captures: GrammarBuilder.captures('punctuation.definition.tag.begin.cvw', 'invalid.illegal.unknown-element.cvw')
            },
            elements: {
                patterns: schema.elements.map(e => ({ include: '#' + GrammarBuilder.elementRuleName(e.name) }))
            }
        };

        for (const element of schema.elements) {
            repository[GrammarBuilder.elementRuleName(element.name)] = GrammarBuilder.elementRule(element);
        }

        return {
            $schema: 'https://raw.githubusercontent.com/martinring/tmlanguage/master/tmlanguage.json',
            name: 'C-View Markup',
            scopeName: SCOPE_NAME,
            comment: 'Generated from schema/cvw-schema.json by scripts/build-grammar.js. Do not edit by hand.',
            patterns: [{ include: '#directives' }, { include: '#csharp-region' }],
            repository
        };
    }

    /**
     * The rule for one element: its start tag, and for elements with content, the content up to the closing tag.
     *
     * @param {object} element An element from the schema.
     * @returns {object} The TextMate rule.
     * @example
     * GrammarBuilder.elementRule(schema.element('br')).end; // '(/?>)'
     */
    static elementRule(element) {
        const name = GrammarBuilder.escapeRegex(element.name);
        const begin = `(<)(${name})(?![\\w-])`;
        const beginCaptures = GrammarBuilder.captures('punctuation.definition.tag.begin.cvw', 'entity.name.tag.cvw');

        if (element.content === 'void') {
            return {
                name: 'meta.tag.cvw',
                begin,
                beginCaptures,
                end: '(/?>)',
                endCaptures: { 1: { name: 'punctuation.definition.tag.end.cvw' } },
                patterns: [{ include: '#attributes' }]
            };
        }

        return {
            begin,
            beginCaptures: { 1: beginCaptures[1], 2: beginCaptures[2] },
            end: `(/>)|(</)(${name})\\s*(>)`,
            endCaptures: GrammarBuilder.captures('punctuation.definition.tag.end.cvw', 'punctuation.definition.tag.begin.cvw',
                'entity.name.tag.cvw', 'punctuation.definition.tag.end.cvw'),
            patterns: [
                { include: '#attributes' },
                {
                    begin: '>',
                    beginCaptures: { 0: { name: 'punctuation.definition.tag.end.cvw' } },
                    end: `(?=</${name}\\s*>)`,
                    contentName: 'meta.content.cvw',
                    patterns: [{ include: '#content' }]
                }
            ]
        };
    }

    /**
     * The repository name of an element's rule.
     *
     * @param {string} name The tag name.
     * @returns {string} Such as "element-bar-chart".
     * @example
     * GrammarBuilder.elementRuleName('bar-chart'); // 'element-bar-chart'
     */
    static elementRuleName(name) {
        return 'element-' + name;
    }

    /**
     * Writes a grammar as stable, readable JSON (4-space indented, ending with a newline).
     *
     * @param {object} grammar The grammar.
     * @returns {string} The JSON text.
     * @example
     * fs.writeFileSync(file, GrammarBuilder.toJson(grammar));
     */
    static toJson(grammar) {
        return JSON.stringify(grammar, null, 4) + '\n';
    }

    /** A quoted attribute value, which may contain @expressions. @private */
    static attributeString(quote, kind) {
        return {
            begin: quote,
            beginCaptures: { 0: { name: 'punctuation.definition.string.begin.cvw' } },
            end: quote,
            endCaptures: { 0: { name: 'punctuation.definition.string.end.cvw' } },
            name: `string.quoted.${kind}.cvw`,
            patterns: [
                { match: '@@', name: 'constant.character.escape.cvw' },
                { include: '#explicit-expression' },
                { include: '#implicit-expression' },
                { include: '#entity' }
            ]
        };
    }

    /** Numbered captures from scope names (1, 2, …). @private */
    static captures(...names) {
        return Object.fromEntries(names.map((name, i) => [i + 1, { name }]));
    }

    /** Escapes regular expression characters in a tag name. @private */
    static escapeRegex(text) {
        return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }
}

module.exports = { GrammarBuilder, SCOPE_NAME };
