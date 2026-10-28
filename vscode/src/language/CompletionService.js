'use strict';

const { MarkupParser } = require('../markup/MarkupParser');
const { Documentation } = require('./Documentation');

/**
 * The kinds of completion item, mapped to editor icons by the extension.
 *
 * @example
 * item.kind === CompletionKind.Element;
 */
const CompletionKind = Object.freeze({
    Element: 'element',
    Attribute: 'attribute',
    Value: 'value',
    Directive: 'directive',
    Construct: 'construct',
    CloseTag: 'closeTag'
});

/**
 * Snippets for the code constructs, keyed by the keyword typed after `@`.
 *
 * @example
 * CONSTRUCT_SNIPPETS.foreach.insertText; // 'foreach (var ${1:item} in ${2:Model.Items})\n{\n\t$0\n}'
 */
const CONSTRUCT_SNIPPETS = Object.freeze({
    if: { insertText: 'if (${1:condition})\n{\n\t$0\n}', detail: 'Markup shown only when a condition holds' },
    foreach: { insertText: 'foreach (var ${1:item} in ${2:Model.Items})\n{\n\t$0\n}', detail: 'Markup repeated for each item' },
    '{': { insertText: '{\n\t$0\n}', detail: 'A block of C# code', label: '@{ }' },
    '*': { insertText: '* $0 *@', detail: 'A comment, removed from the view', label: '@* *@' }
});

/**
 * Suggests what may be typed at a position of a view: elements after `<` (only those allowed there), the closing tag
 * after `</`, attributes inside a start tag (those not written yet), values inside an attribute's quotes, and
 * directives and constructs after `@`. Everything comes from the schema, so suggestions match what the build accepts.
 *
 * @example
 * const service = new CompletionService(schema);
 * service.complete('<box bor', 8).map(i => i.label); // ['border']
 */
class CompletionService {
    /**
     * @param {import('../schema/MarkupSchema').MarkupSchema} schema The markup schema.
     * @example
     * const service = new CompletionService(MarkupSchema.load());
     */
    constructor(schema) {
        this._schema = schema;
    }

    /**
     * The suggestions at an offset.
     *
     * @param {string} text The view's text.
     * @param {number} offset Where the cursor is.
     * @returns {{label: string, kind: string, detail: string, documentation: string, insertText: string,
     *            replaceStart: number, replaceEnd: number}[]} The suggestions; insertText uses snippet syntax.
     * @example
     * service.complete('<box border="', 13).map(i => i.label); // ['single', 'double', ...]
     */
    complete(text, offset) {
        const document = MarkupParser.parse(text, this._schema);
        if (document.isOpaque(offset)) {
            return [];
        }

        const before = text.slice(Math.max(0, offset - 256), offset);
        const wordEnd = offset + /^[\w-]*/.exec(text.slice(offset))[0].length;

        // At the end of the tag name itself, the name is being typed: that is element completion below
        const startTag = document.startTagAt(offset);
        if (startTag && offset > startTag.nameEnd) {
            const value = startTag.attributes.find(a => a.quote && offset >= a.valueStart && offset <= a.valueEnd);
            if (value) {
                return this._values(startTag, value, offset);
            }
            const typed = /[\w-]*$/.exec(before)[0];
            if (/\s$/.test(before.slice(0, before.length - typed.length))) {
                return this._attributes(startTag, offset - typed.length, wordEnd);
            }
            return [];
        }

        const close = /<\/([\w-]*)$/.exec(before);
        if (close) {
            return this._closeTag(document, offset - close[0].length, offset, text);
        }

        const open = /<([\w-]*)$/.exec(before);
        if (open) {
            const chain = document.openElementsAt(offset - open[0].length);
            const parent = chain.length > 0 ? chain[chain.length - 1].name : null;
            return this._elements(parent, offset - open[1].length, wordEnd);
        }

        const at = /@([\w{*]*)$/.exec(before);
        if (at && !/@@$/.test(before.slice(0, before.length - at[1].length))) {
            const lineStart = /(^|\n)[ \t]*@[\w{*]*$/.test(before);
            const insideElement = document.openElementsAt(offset).length > 0;
            return this._atItems(lineStart && !insideElement, offset - at[1].length, wordEnd);
        }

        return [];
    }

    /** Elements allowed in a parent. @private */
    _elements(parentName, replaceStart, replaceEnd) {
        return this._schema.elementsAllowedIn(parentName).map((element, index) => ({
            label: element.name,
            kind: CompletionKind.Element,
            detail: element.description,
            documentation: Documentation.element(element),
            insertText: CompletionService.elementSnippet(element),
            replaceStart,
            replaceEnd,
            sortText: String(index).padStart(3, '0')
        }));
    }

    /**
     * The snippet that writes an element: its required attributes, then `/>` or its content and closing tag.
     *
     * @param {object} element An element from the schema.
     * @returns {string} Snippet text, without the leading `<`.
     * @example
     * CompletionService.elementSnippet(schema.element('partial')); // 'partial name="$1" />$0'
     */
    static elementSnippet(element) {
        let tab = 1;
        const required = element.attributes
            .filter(a => a.required && !a.flag)
            .filter((a, i, all) => all.findIndex(b => b.description === a.description) === i) // one of each alias
            .map(a => ` ${a.name}="$${tab++}"`)
            .join('');
        if (element.content === 'void') {
            return `${element.name}${required} />$0`;
        }
        const body = element.content === 'inline' ? `$${tab}` : `\n\t$${tab}\n`;
        return `${element.name}${required}>${body}</${element.name}>$0`;
    }

    /** Attributes of an element not written yet. @private */
    _attributes(element, replaceStart, replaceEnd) {
        const schemaElement = this._schema.element(element.name);
        if (!schemaElement) {
            return [];
        }
        const written = new Set(element.attributes.filter(a => a.start < replaceStart || a.start > replaceEnd).map(a => a.name));
        return schemaElement.attributes
            .filter(a => !written.has(a.name))
            .map((attribute, index) => ({
                label: attribute.name,
                kind: CompletionKind.Attribute,
                detail: attribute.description,
                documentation: Documentation.attribute(attribute),
                insertText: attribute.flag ? attribute.name : `${attribute.name}="$1"$0`,
                replaceStart,
                replaceEnd,
                sortText: (attribute.required ? '0' : '1') + String(index).padStart(3, '0')
            }));
    }

    /** Values for an attribute, replacing what is between the quotes. @private */
    _values(element, attribute, offset) {
        const schemaAttribute = this._schema.attribute(element.name, attribute.name);
        if (!schemaAttribute) {
            return [];
        }
        const values = schemaAttribute.values ?? (schemaAttribute.kind === 'boolean' ? ['true', 'false'] : []);
        return values.map((value, index) => ({
            label: value === '' ? '""' : value,
            kind: CompletionKind.Value,
            detail: schemaAttribute.accepts,
            documentation: Documentation.attribute(schemaAttribute),
            insertText: value,
            replaceStart: attribute.valueStart,
            replaceEnd: Math.max(offset, attribute.valueEnd),
            sortText: String(index).padStart(3, '0')
        }));
    }

    /** The closing tag of the innermost open element. @private */
    _closeTag(document, tagStart, offset, text) {
        const chain = document.openElementsAt(tagStart);
        const open = [...chain].reverse().find(e => !e.isClosed);
        if (!open) {
            return [];
        }
        const end = offset + /^[\w-]*>?/.exec(text.slice(offset))[0].length; // replaces a > already typed too
        return [{
            label: `</${open.name}>`,
            kind: CompletionKind.CloseTag,
            detail: `Closes the <${open.name}> opened on this view`,
            documentation: '',
            insertText: `</${open.name}>`,
            replaceStart: tagStart,
            replaceEnd: end,
            sortText: '000'
        }];
    }

    /** Directives (at the start of a line outside elements) and code constructs after `@`. @private */
    _atItems(directivesAllowed, replaceStart, replaceEnd) {
        const items = [];
        if (directivesAllowed) {
            for (const directive of this._schema.directives) {
                const name = directive.name.slice(1);
                items.push({
                    label: directive.name,
                    kind: CompletionKind.Directive,
                    detail: directive.syntax,
                    documentation: Documentation.directive(directive),
                    insertText: `${name} $0`,
                    replaceStart,
                    replaceEnd,
                    sortText: '0' + name
                });
            }
        }
        for (const [keyword, snippet] of Object.entries(CONSTRUCT_SNIPPETS)) {
            items.push({
                label: snippet.label ?? '@' + keyword,
                kind: CompletionKind.Construct,
                detail: snippet.detail,
                documentation: '',
                insertText: snippet.insertText,
                replaceStart,
                replaceEnd,
                sortText: '1' + keyword
            });
        }
        return items;
    }
}

module.exports = { CompletionService, CompletionKind, CONSTRUCT_SNIPPETS };
