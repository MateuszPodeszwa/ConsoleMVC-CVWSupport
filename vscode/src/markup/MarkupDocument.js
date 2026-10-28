'use strict';

/**
 * An attribute of a start tag, with where its name and value are in the text.
 *
 * @example
 * // <box border="double">
 * attribute.name;        // 'border'
 * attribute.value;       // 'double'
 * attribute.valueStart;  // the offset of the d, just after the quote
 */
class MarkupAttribute {
    /**
     * @param {string} name The attribute's name.
     * @param {number} start The offset of its first character.
     * @param {number} nameEnd The offset just after its name.
     * @example
     * const attribute = new MarkupAttribute('border', 5, 11);
     */
    constructor(name, start, nameEnd) {
        /** @type {string} */
        this.name = name;
        /** @type {number} */
        this.start = start;
        /** @type {number} */
        this.nameEnd = nameEnd;
        /** @type {string | null} The value without quotes; null for a flag such as `bold`. */
        this.value = null;
        /** @type {number} The offset of the value's first character (after the quote), or -1 for a flag. */
        this.valueStart = -1;
        /** @type {number} The offset just after the value (before the closing quote), or -1 for a flag. */
        this.valueEnd = -1;
        /** @type {string | null} The quote used, `"` or `'`, or null for an unquoted value or a flag. */
        this.quote = null;
        /** @type {boolean} Whether the closing quote is missing. */
        this.unterminated = false;
    }

    /**
     * Whether the value contains an @expression, which only the build can check.
     *
     * @type {boolean}
     * @example
     * attribute.hasExpression; // true for title="Hi @Model.Name"
     */
    get hasExpression() {
        return this.value !== null && /(^|[^\p{L}\p{Nd}@])@(?=[\p{L}_(])/u.test(this.value.replace(/@@/g, ''));
    }
}

/**
 * An element of a view: its tag, attributes, children and where each part is in the text. Elements whose closing tag
 * is missing have `closeStart` -1 and end where the parser stopped.
 *
 * @example
 * // <box title="A"><text>x</text></box>
 * box.name;            // 'box'
 * box.children[0].name; // 'text'
 * text.slice(box.contentStart, box.closeStart); // '<text>x</text>'
 */
class MarkupElement {
    /**
     * @param {string} name The tag name.
     * @param {boolean} known Whether the schema has this element.
     * @param {number} start The offset of the `<`.
     * @param {MarkupElement | null} parent The enclosing element, or null at the top level.
     * @example
     * const element = new MarkupElement('box', true, 0, null);
     */
    constructor(name, known, start, parent) {
        /** @type {string} */
        this.name = name;
        /** @type {boolean} */
        this.known = known;
        /** @type {number} */
        this.start = start;
        /** @type {number} The offset just after the tag name. */
        this.nameEnd = start + 1 + name.length;
        /** @type {MarkupElement | null} */
        this.parent = parent;
        /** @type {MarkupAttribute[]} */
        this.attributes = [];
        /** @type {MarkupElement[]} */
        this.children = [];
        /** @type {number} The offset just after the start tag's `>` or `/>`, or -1 when the start tag is unfinished. */
        this.startTagEnd = -1;
        /** @type {boolean} Whether the start tag ends with `/>`. */
        this.selfClosing = false;
        /** @type {number} The offset where the content begins, or -1 when there is none. */
        this.contentStart = -1;
        /** @type {number} The offset of the closing tag's `<`, or -1 when it is missing. */
        this.closeStart = -1;
        /** @type {number} The offset just after the element. */
        this.end = start;
        /** @type {{start: number, end: number}[]} Runs of text and @expressions written directly in the content. */
        this.textRanges = [];
    }

    /**
     * Whether the element is finished: self-closing, or with its closing tag.
     *
     * @type {boolean}
     * @example
     * element.isClosed; // false while the user is still typing <box>…
     */
    get isClosed() {
        return this.selfClosing || this.closeStart >= 0;
    }

    /**
     * Finds an attribute by name.
     *
     * @param {string} name The attribute's name.
     * @returns {MarkupAttribute | undefined} The first attribute with that name.
     * @example
     * element.attribute('title')?.value;
     */
    attribute(name) {
        return this.attributes.find(a => a.name === name);
    }
}

/**
 * A parsed view: the elements in a tree, and the ranges of comments and C# strings, where no markup is.
 * Built by {@link MarkupParser}; read by completion, hover and checks.
 *
 * @example
 * const document = MarkupParser.parse(text, schema);
 * document.openElementsAt(offset).map(e => e.name); // ['table', 'row'] inside a row
 */
class MarkupDocument {
    /**
     * @param {string} text The view's text.
     * @param {MarkupElement[]} roots The top-level elements.
     * @param {{start: number, end: number}[]} opaqueRanges Comments and C# strings, in order.
     * @example
     * const document = new MarkupDocument(text, [], []);
     */
    constructor(text, roots, opaqueRanges) {
        /** @type {string} */
        this.text = text;
        /** @type {MarkupElement[]} */
        this.roots = roots;
        /** @type {{start: number, end: number}[]} */
        this.opaqueRanges = opaqueRanges;
    }

    /**
     * Every element, parents before their children, in source order.
     *
     * @returns {MarkupElement[]} The elements.
     * @example
     * document.allElements().filter(e => !e.known);
     */
    allElements() {
        const all = [];
        const visit = element => {
            all.push(element);
            element.children.forEach(visit);
        };
        this.roots.forEach(visit);
        return all;
    }

    /**
     * The elements whose content holds the offset, outermost first.
     *
     * @param {number} offset An offset in the text.
     * @returns {MarkupElement[]} The open elements; empty at the top level.
     * @example
     * document.openElementsAt(text.indexOf('x')); // [box, text] for <box><text>x</text></box>
     */
    openElementsAt(offset) {
        const chain = [];
        let candidates = this.roots;
        for (;;) {
            const inside = candidates.find(e => e.contentStart >= 0 && e.contentStart <= offset
                && (e.closeStart < 0 ? offset <= e.end : offset <= e.closeStart));
            if (!inside) {
                return chain;
            }
            chain.push(inside);
            candidates = inside.children;
        }
    }

    /**
     * The element whose start tag holds the offset, between its name and its closing `>`.
     *
     * @param {number} offset An offset in the text.
     * @returns {MarkupElement | undefined} The element, or undefined outside any start tag.
     * @example
     * document.startTagAt(text.indexOf('border')).name; // 'box' for <box border="double">
     */
    startTagAt(offset) {
        return this.allElements().find(e => {
            const tagEnd = e.startTagEnd < 0 ? e.end : e.startTagEnd - (e.selfClosing ? 2 : 1);
            return offset >= e.nameEnd && offset <= tagEnd;
        });
    }

    /**
     * Whether the offset is inside a comment or a C# string, where completion and checks do not apply.
     *
     * @param {number} offset An offset in the text.
     * @returns {boolean} True inside a comment or string.
     * @example
     * document.isOpaque(text.indexOf('ignored')); // true for @* ignored *@
     */
    isOpaque(offset) {
        return this.opaqueRanges.some(r => offset > r.start && offset < r.end);
    }
}

module.exports = { MarkupAttribute, MarkupElement, MarkupDocument };
