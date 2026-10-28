'use strict';

const { MarkupParser, DIRECTIVE } = require('../markup/MarkupParser');
const { Documentation } = require('./Documentation');

/**
 * Explains what is under the cursor: an element's tag name, an attribute's name, or a directive, with the
 * documentation from the schema.
 *
 * @example
 * const service = new HoverService(schema);
 * service.hover('<box border="double" />', 6); // { markdown: '**`border`** The border style. …', start: 5, end: 11 }
 */
class HoverService {
    /**
     * @param {import('../schema/MarkupSchema').MarkupSchema} schema The markup schema.
     * @example
     * const service = new HoverService(MarkupSchema.load());
     */
    constructor(schema) {
        this._schema = schema;
    }

    /**
     * The documentation at an offset.
     *
     * @param {string} text The view's text.
     * @param {number} offset Where the pointer is.
     * @returns {{markdown: string, start: number, end: number} | null} The documentation and the range it describes,
     *     or null when there is nothing to explain.
     * @example
     * service.hover('@layout none', 2).markdown; // '**`@layout Name`** …'
     */
    hover(text, offset) {
        const directive = this._directive(text, offset);
        if (directive) {
            return directive;
        }

        const document = MarkupParser.parse(text, this._schema);
        if (document.isOpaque(offset)) {
            return null;
        }

        for (const element of document.allElements()) {
            const schemaElement = this._schema.element(element.name);
            if (!schemaElement) {
                continue;
            }
            if (offset >= element.start + 1 && offset <= element.nameEnd) {
                return { markdown: Documentation.element(schemaElement), start: element.start + 1, end: element.nameEnd };
            }
            if (element.closeStart >= 0 && offset >= element.closeStart + 2 && offset <= element.closeStart + 2 + element.name.length) {
                return {
                    markdown: Documentation.element(schemaElement),
                    start: element.closeStart + 2,
                    end: element.closeStart + 2 + element.name.length
                };
            }
            const attribute = element.attributes.find(a => offset >= a.start && offset <= a.nameEnd);
            const schemaAttribute = attribute && this._schema.attribute(element.name, attribute.name);
            if (schemaAttribute) {
                return { markdown: Documentation.attribute(schemaAttribute), start: attribute.start, end: attribute.nameEnd };
            }
        }
        return null;
    }

    /** A directive keyword at the start of the offset's line. @private */
    _directive(text, offset) {
        const lineStart = text.lastIndexOf('\n', offset - 1) + 1;
        const indent = /^[ \t]*/.exec(text.slice(lineStart))[0].length;
        const match = DIRECTIVE.exec(text.slice(lineStart + indent, lineStart + indent + 16));
        if (!match) {
            return null;
        }
        const start = lineStart + indent;
        const end = start + match[0].length;
        const directive = this._schema.directive(match[1]);
        return directive && offset >= start && offset <= end
            ? { markdown: Documentation.directive(directive), start, end }
            : null;
    }
}

module.exports = { HoverService };
