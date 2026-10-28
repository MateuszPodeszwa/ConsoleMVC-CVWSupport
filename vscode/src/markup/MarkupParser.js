'use strict';

const { MarkupAttribute, MarkupElement, MarkupDocument } = require('./MarkupDocument');

/**
 * The C# keywords that start a block of code inside markup, such as `@foreach (…) { … }`.
 *
 * @example
 * CODE_KEYWORDS.has('foreach'); // true
 */
const CODE_KEYWORDS = new Set(['if', 'foreach', 'for', 'while', 'switch', 'lock', 'using', 'do', 'try']);

/**
 * Characters after which a C# statement may begin, so `<tag` there is markup (as in the generator's MarkupParser).
 *
 * @example
 * STATEMENT_ENDS.has(';'); // true: `x = 1; <text>…` starts markup
 */
const STATEMENT_ENDS = new Set(['\0', ';', '{', '}', ':']);

/**
 * A directive at the start of a line, such as `@model Shop.Models.Cart`.
 *
 * @example
 * DIRECTIVE.test('@layout none'); // true
 */
const DIRECTIVE = /^@(model|using|inject|layout|refresh)\b/;

/** The deepest nesting followed, as in the generator; deeper markup is left unparsed. */
const MAX_DEPTH = 64;

/**
 * Parses a `.cvw` view into a {@link MarkupDocument}, following the generator's rules closely enough for editing:
 * at the top level and in `@{ }` blocks the text is C#, and `<tag` starts markup only where a statement may begin and
 * the tag is known; inside an element every `<tag` is markup, `@` starts an expression or code, and `</` closes.
 * It never throws, and keeps going after mistakes, since the text is usually half-typed.
 *
 * Pseudocode:
 * ```text
 * code(stopAtBrace):  skip whitespace, comments, strings; '<' + known tag at a statement start → element
 *                     '{' depth++; '}' at depth 0 and stopAtBrace → return
 * element:            '<' name attributes ('/>' | '>' content '</name>')
 * content:            '</' → close (or return to an ancestor that has that name); '<' name → child element
 *                     '@{' → code(true); '@if (…) {' → code(true) [else …]; '@expr', '@(…)' → skip; '@*…*@' → comment
 * ```
 *
 * @example
 * const document = MarkupParser.parse('<box><text>Hi @Model.Name</text></box>', schema);
 * document.roots[0].children[0].name; // 'text'
 */
class MarkupParser {
    /**
     * Parses a view.
     *
     * @param {string} text The view's text.
     * @param {import('../schema/MarkupSchema').MarkupSchema} schema The markup schema, which says which tags are known.
     * @returns {MarkupDocument} The parsed view.
     * @example
     * MarkupParser.parse(text, schema).allElements().length;
     */
    static parse(text, schema) {
        return new MarkupParser(text, schema)._parse();
    }

    /**
     * @param {string} text The view's text.
     * @param {import('../schema/MarkupSchema').MarkupSchema} schema The markup schema.
     * @private
     */
    constructor(text, schema) {
        this._text = text;
        this._schema = schema;
        this._position = 0;
        this._depth = 0;
        /** @type {{start: number, end: number}[]} */
        this._opaque = [];
    }

    /** @private */
    _parse() {
        const roots = [];
        this._code(null, roots, false);
        return new MarkupDocument(this._text, roots, this._opaque);
    }

    // ---------------------------------------------------------------- C#

    /**
     * Reads C# until the end (or an unmatched `}` when `stopAtBrace`), parsing the markup in it.
     *
     * @param {MarkupElement | null} parent The element the code is inside, which receives its elements.
     * @param {MarkupElement[]} output Where top-level elements go when there is no parent.
     * @param {boolean} stopAtBrace Whether an unmatched `}` ends the code (it is not consumed).
     * @private
     */
    _code(parent, output, stopAtBrace) {
        const text = this._text;
        let last = '\0';
        let depth = 0;

        while (this._position < text.length) {
            const c = text[this._position];

            if (/\s/.test(c)) {
                this._position++;
                continue;
            }
            // A directive line (@model, @using …) is not C#, and does not change whether a statement may begin
            if (c === '@' && parent === null && this._isFirstOnLine(this._position)
                && DIRECTIVE.test(text.slice(this._position, this._position + 16))) {
                this._skipTo('\n', false);
                continue;
            }
            if (c === '#' && this._isFirstOnLine(this._position)) {
                this._skipTo('\n', false);
                continue;
            }
            if (this._skipCommentOrString()) {
                if (text[this._position - 1] === '"' || text[this._position - 1] === '\'') {
                    last = '"';
                }
                continue;
            }
            if (c === '<' && STATEMENT_ENDS.has(last)) {
                if (text.startsWith('<!--', this._position)) {
                    this._skipDelimited('<!--', '-->');
                    continue;
                }
                const name = this._peekTagName(this._position);
                if (name && this._schema.element(name)) {
                    const element = this._element(name, parent);
                    (parent ? parent.children : output).push(element);
                    last = ';';
                    continue;
                }
            }
            if (c === '{') {
                depth++;
            } else if (c === '}') {
                if (depth === 0 && stopAtBrace) {
                    return;
                }
                depth--;
            }

            last = c;
            this._position++;
        }
    }

    /**
     * Skips a comment (`@* *@`, `//`, `/* *\/`) or a C# string or character literal at the position.
     *
     * @returns {boolean} Whether something was skipped.
     * @private
     */
    _skipCommentOrString() {
        const text = this._text;
        const at = this._position;
        const c = text[at];
        const next = text[at + 1];

        if (c === '@' && next === '*') {
            this._skipDelimited('@*', '*@');
            return true;
        }
        if (c === '/' && next === '/') {
            this._opaqueFrom(at, () => this._skipTo('\n', false));
            return true;
        }
        if (c === '/' && next === '*') {
            this._skipDelimited('/*', '*/');
            return true;
        }

        // "…", @"…", $"…", $@"…", @$"…" and '…'
        const literal = /^(\$@|@\$|\$|@)?(["'])/.exec(text.slice(at, at + 3));
        if (literal && !(literal[1] && literal[2] === '\'')) {
            const prefix = literal[1] ?? '';
            this._opaqueFrom(at, () => {
                this._position = at + prefix.length + 1;
                this._skipStringBody(literal[2], prefix.includes('@'));
            });
            return true;
        }

        return false;
    }

    /**
     * Skips the rest of a string up to and including its closing quote.
     *
     * @param {string} quote The quote character.
     * @param {boolean} verbatim Whether `""` escapes a quote and lines may break.
     * @private
     */
    _skipStringBody(quote, verbatim) {
        const text = this._text;
        while (this._position < text.length) {
            const c = text[this._position];
            if (!verbatim && c === '\\') {
                this._position += 2;
                continue;
            }
            if (!verbatim && c === '\n') {
                return; // unterminated: stop at the end of the line
            }
            if (c === quote) {
                if (verbatim && text[this._position + 1] === quote) {
                    this._position += 2;
                    continue;
                }
                this._position++;
                return;
            }
            this._position++;
        }
    }

    // ---------------------------------------------------------------- markup

    /**
     * Parses an element whose `<` is at the position.
     *
     * @param {string} name The tag name, already read by {@link _peekTagName}.
     * @param {MarkupElement | null} parent The enclosing element.
     * @returns {MarkupElement} The element.
     * @private
     */
    _element(name, parent) {
        const element = new MarkupElement(name, Boolean(this._schema.element(name)), this._position, parent);
        this._position = element.nameEnd;
        this._depth++;
        try {
            if (this._depth > MAX_DEPTH) {
                this._position = this._text.length;
            } else if (this._startTag(element) && !element.selfClosing) {
                element.contentStart = this._position;
                if (this._schema.element(name)?.content !== 'void') {
                    this._content(element);
                }
            }
        } finally {
            this._depth--;
        }
        element.end = Math.max(this._position, element.nameEnd);
        return element;
    }

    /**
     * Reads attributes up to `>` or `/>`.
     *
     * @param {MarkupElement} element The element.
     * @returns {boolean} False when the start tag is unfinished (another `<` or the end came first).
     * @private
     */
    _startTag(element) {
        const text = this._text;
        while (this._position < text.length) {
            const c = text[this._position];
            if (/\s/.test(c)) {
                this._position++;
            } else if (text.startsWith('/>', this._position)) {
                this._position += 2;
                element.selfClosing = true;
                element.startTagEnd = this._position;
                return true;
            } else if (c === '>') {
                this._position++;
                element.startTagEnd = this._position;
                return true;
            } else if (c === '<') {
                return false;
            } else {
                const nameMatch = /^[A-Za-z_][\w\-:.]*/.exec(text.slice(this._position, this._position + 128));
                if (!nameMatch) {
                    this._position++;
                    continue;
                }
                const attribute = new MarkupAttribute(nameMatch[0], this._position, this._position + nameMatch[0].length);
                this._position = attribute.nameEnd;
                this._attributeValue(attribute);
                element.attributes.push(attribute);
            }
        }
        return false;
    }

    /**
     * Reads `= "value"` after an attribute name, if present.
     *
     * @param {MarkupAttribute} attribute The attribute.
     * @private
     */
    _attributeValue(attribute) {
        const text = this._text;
        let at = this._position;
        while (at < text.length && /[ \t]/.test(text[at])) {
            at++;
        }
        if (text[at] !== '=') {
            return;
        }
        at++;
        while (at < text.length && /[ \t]/.test(text[at])) {
            at++;
        }

        const quote = text[at];
        if (quote === '"' || quote === '\'') {
            attribute.quote = quote;
            attribute.valueStart = at + 1;
            const close = this._closingQuote(at + 1, quote);
            attribute.unterminated = close < 0;
            attribute.valueEnd = close < 0 ? this._lineEnd(at + 1) : close;
            this._position = close < 0 ? attribute.valueEnd : close + 1;
        } else {
            // Unquoted, such as title=@Model.Title: up to whitespace or the end of the tag
            const match = /^[^\s>]*/.exec(text.slice(at));
            let value = match[0];
            if (value.endsWith('/') && text[at + value.length] === '>') {
                value = value.slice(0, -1);
            }
            attribute.valueStart = at;
            attribute.valueEnd = at + value.length;
            this._position = attribute.valueEnd;
        }
        attribute.value = text.slice(attribute.valueStart, attribute.valueEnd);
    }

    /**
     * Finds the quote closing an attribute value, skipping `@( … )` and `@Name["…"]` so quotes inside an expression
     * do not end it.
     *
     * @returns {number} The offset of the quote, or -1 when the line or text ends first.
     * @private
     */
    _closingQuote(from, quote) {
        const text = this._text;
        let at = from;
        while (at < text.length) {
            const c = text[at];
            if (c === quote) {
                return at;
            }
            if (c === '\n' && text.slice(from, at).indexOf('<') >= 0) {
                return -1; // a new tag on the next line: the quote was forgotten
            }
            if (c === '@' && text[at + 1] === '(') {
                at = this._balancedEnd(at + 1, '(', ')');
                continue;
            }
            if (c === '@' && /[A-Za-z_]/.test(text[at + 1] ?? '')) {
                // title="@L["Title"]": the quotes of an expression's string do not end the value
                at = this._expressionEnd(at + 1);
                continue;
            }
            at += c === '@' && text[at + 1] === '@' ? 2 : 1;
        }
        return -1;
    }

    /**
     * Reads an element's content up to its closing tag.
     *
     * @param {MarkupElement} element The element.
     * @private
     */
    _content(element) {
        const text = this._text;
        while (this._position < text.length) {
            const c = text[this._position];

            if (c === '<' && text[this._position + 1] === '/') {
                const name = this._peekTagName(this._position + 1) ?? '';
                if (name === element.name) {
                    element.closeStart = this._position;
                    const close = text.indexOf('>', this._position);
                    this._position = close < 0 ? text.length : close + 1;
                    return;
                }
                if (this._hasAncestor(element, name)) {
                    return; // this element was left open; the ancestor closes here
                }
                const close = text.indexOf('>', this._position);
                this._position = close < 0 ? text.length : close + 1; // a stray closing tag
                continue;
            }
            if (c === '<' && text.startsWith('<!--', this._position)) {
                this._skipDelimited('<!--', '-->');
                continue;
            }
            if (c === '<') {
                const name = this._peekTagName(this._position);
                if (name) {
                    element.children.push(this._element(name, element));
                    continue;
                }
            }
            if (c === '@') {
                const start = this._position;
                if (this._at(element)) {
                    this._markText(element, start, this._position);
                }
                continue;
            }
            if (!/\s/.test(c)) {
                this._markText(element, this._position, this._position + 1);
            }
            this._position++;
        }
    }

    /**
     * Records text (or an expression) written directly in an element's content, joining it to the run before.
     *
     * @private
     */
    _markText(element, start, end) {
        const last = element.textRanges[element.textRanges.length - 1];
        if (last && /^\s*$/.test(this._text.slice(last.end, start))) {
            last.end = end;
        } else {
            element.textRanges.push({ start, end });
        }
    }

    /**
     * Handles an `@` inside content: an escape, a comment, an expression or a block of code.
     *
     * @param {MarkupElement} element The element whose content this is.
     * @returns {boolean} Whether it was text: an expression or the escape `@@`.
     * @private
     */
    _at(element) {
        const text = this._text;
        const at = this._position;
        const next = text[at + 1];

        if (next === '@') {
            this._position += 2;
            return true;
        }
        if (next === '*') {
            this._skipDelimited('@*', '*@');
            return false;
        }
        if (next === '{') {
            this._position += 2;
            this._codeBlockBody(element);
            return false;
        }
        if (next === '(') {
            this._position = this._balancedEnd(at + 1, '(', ')');
            return true;
        }

        // The @ of an e-mail address (me@example.com) is text, as in the generator
        if (at > 0 && /[\p{L}\p{Nd}]/u.test(text[at - 1])) {
            this._position++;
            return true;
        }

        const word = /^[A-Za-z_]\w*/.exec(text.slice(at + 1, at + 65));
        if (word && CODE_KEYWORDS.has(word[0])) {
            this._position = at + 1 + word[0].length;
            this._controlFlow(element);
            return false;
        }
        if (word) {
            this._position = this._expressionEnd(at + 1);
            return true;
        }
        this._position++;
        return true;
    }

    /**
     * Reads `(…) { … }` after `@if`, `@foreach` and the like, with any `else` / `else if` / `catch` that follows.
     *
     * @param {MarkupElement} element The element the code is inside.
     * @private
     */
    _controlFlow(element) {
        const text = this._text;
        for (;;) {
            // Up to the block's {, over a condition in parentheses
            while (this._position < text.length && text[this._position] !== '{') {
                if (text[this._position] === '(') {
                    this._position = this._balancedEnd(this._position, '(', ')');
                } else if (text[this._position] === '<' || text[this._position] === '\n' && text[this._position - 1] === '\n') {
                    return; // no block: the user is still typing
                } else {
                    this._position++;
                }
            }
            if (this._position >= text.length) {
                return;
            }
            this._position++;
            this._codeBlockBody(element);

            const after = /^\s*(else\s+if|else|catch|finally|while)\b/.exec(text.slice(this._position, this._position + 64));
            if (!after) {
                return;
            }
            this._position += after[0].length;
            if (after[1] === 'while') {
                this._position = this._balancedEnd(text.indexOf('(', this._position), '(', ')');
                return;
            }
        }
    }

    /**
     * Reads code up to and including the `}` that closes the block whose `{` was just passed.
     *
     * @param {MarkupElement} element The element the code is inside.
     * @private
     */
    _codeBlockBody(element) {
        this._code(element, element.children, true);
        if (this._text[this._position] === '}') {
            this._position++;
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The tag name after a `<` (or the `/` of `</`), or null when none follows.
     *
     * @private
     */
    _peekTagName(at) {
        const match = /^[A-Za-z][\w-]*/.exec(this._text.slice(at + 1, at + 65));
        return match ? match[0] : null;
    }

    /** @private */
    _hasAncestor(element, name) {
        for (let current = element.parent; current; current = current.parent) {
            if (current.name === name) {
                return true;
            }
        }
        return false;
    }

    /**
     * The end of an implicit expression such as `Model.Items[0].Name` or `L["Title"]` starting at `from`.
     *
     * @private
     */
    _expressionEnd(from) {
        const text = this._text;
        let at = from + /^[A-Za-z_]\w*/.exec(text.slice(from))[0].length;
        for (;;) {
            const rest = text.slice(at, at + 66);
            const member = /^\??\.[A-Za-z_]\w*/.exec(rest);
            if (member) {
                at += member[0].length;
            } else if (rest[0] === '(') {
                at = this._balancedEnd(at, '(', ')');
            } else if (rest[0] === '[') {
                at = this._balancedEnd(at, '[', ']');
            } else {
                return at;
            }
        }
    }

    /**
     * The offset just after the bracket closing the one at `from`, skipping strings; the end of the text if unclosed.
     *
     * @private
     */
    _balancedEnd(from, open, close) {
        if (from < 0) {
            return this._text.length;
        }
        const saved = this._position;
        let depth = 0;
        this._position = from;
        try {
            while (this._position < this._text.length) {
                const c = this._text[this._position];
                if ((c === '"' || c === '\'' || c === '@' || c === '$') && this._skipCommentOrString()) {
                    continue;
                }
                this._position++;
                if (c === open) {
                    depth++;
                } else if (c === close && --depth === 0) {
                    return this._position;
                }
            }
            return this._text.length;
        } finally {
            // The caller moves to the returned offset itself
            this._position = saved;
        }
    }

    /** Skips from an opening delimiter to after the closing one (or the end), recording the range. @private */
    _skipDelimited(open, close) {
        const start = this._position;
        const end = this._text.indexOf(close, start + open.length);
        this._position = end < 0 ? this._text.length : end + close.length;
        this._opaque.push({ start, end: this._position });
    }

    /** Runs a skip and records the skipped range as opaque. @private */
    _opaqueFrom(start, skip) {
        skip();
        this._opaque.push({ start, end: this._position });
    }

    /** Moves to the next `character` (past it when `inclusive`), or the end. @private */
    _skipTo(character, inclusive) {
        const at = this._text.indexOf(character, this._position);
        this._position = at < 0 ? this._text.length : at + (inclusive ? 1 : 0);
    }

    /** @private */
    _lineEnd(from) {
        const at = this._text.indexOf('\n', from);
        return at < 0 ? this._text.length : at;
    }

    /** @private */
    _isFirstOnLine(at) {
        for (let i = at - 1; i >= 0 && this._text[i] !== '\n'; i--) {
            if (!/\s/.test(this._text[i])) {
                return false;
            }
        }
        return true;
    }
}

module.exports = { MarkupParser, CODE_KEYWORDS, DIRECTIVE };
