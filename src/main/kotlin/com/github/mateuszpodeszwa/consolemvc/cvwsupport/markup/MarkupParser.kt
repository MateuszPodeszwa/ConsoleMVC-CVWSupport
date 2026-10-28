package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

/**
 * Parses a `.cvw` view into a [MarkupDocument], following ConsoleMVC's generator closely enough for editing: at the top
 * level and in `@{ }` blocks the text is C#, and `<tag` starts markup only where a statement may begin and the tag is
 * known; inside an element every `<tag` is markup, `@` starts an expression or code, and `</` closes. It never
 * throws, and keeps going after mistakes, since the text is usually half-typed. The same rules as the VS Code
 * extension's MarkupParser.js.
 *
 * ```text
 * code(stopAtBrace):  skip whitespace, directive lines, comments, strings
 *                     '<' + known tag where a statement may begin (after ; { } : or at the start) → element
 *                     '{' depth++; '}' at depth 0 and stopAtBrace → return
 * element:            '<' name attributes ('/>' | '>' content '</name>')
 * content:            '</' → close (or return to an ancestor that has that name); '<' name → child element
 *                     '@{' → code(true); '@if (…) {' → code(true) [else …]; '@expr', '@(…)', '@@' → text
 * ```
 *
 * Example:
 * ```kotlin
 * val document = MarkupParser.parse("<box><text>Hi @Model.Name</text></box>", MarkupSchema.bundled)
 * document.roots[0].children[0].name // "text"
 * ```
 */
class MarkupParser private constructor(private val text: String, private val schema: MarkupSchema) {
    private var position = 0
    private var depth = 0
    private val opaque = mutableListOf<TextSpan>()

    companion object {
        /** The C# keywords that start a block of code inside markup, such as `@foreach (…) { … }`. */
        val CODE_KEYWORDS = setOf("if", "foreach", "for", "while", "switch", "lock", "using", "do", "try")

        /** A directive at the start of a line, such as `@model Shop.Models.Cart`. */
        val DIRECTIVE = Regex("^@(model|using|inject|layout|refresh)\\b")

        /** Characters after which a C# statement may begin, so `<tag` there is markup. */
        private val STATEMENT_ENDS = setOf('\u0000', ';', '{', '}', ':')

        /** The deepest nesting followed, as in the generator; deeper markup is left unparsed. */
        private const val MAX_DEPTH = 64

        private val TAG_NAME = Regex("^[A-Za-z][\\w-]*")
        private val ATTRIBUTE_NAME = Regex("^[A-Za-z_][\\w\\-:.]*")
        private val WORD = Regex("^[A-Za-z_]\\w*")
        private val MEMBER = Regex("^\\??\\.[A-Za-z_]\\w*")
        private val STRING_START = Regex("^(\\$@|@\\$|\\$|@)?([\"'])")
        private val CONTINUATION = Regex("^\\s*(else\\s+if|else|catch|finally|while)\\b")

        /**
         * Parses a view.
         *
         * Example: `MarkupParser.parse(text, MarkupSchema.bundled).allElements().size`.
         */
        fun parse(text: String, schema: MarkupSchema): MarkupDocument = MarkupParser(text, schema).parseDocument()
    }

    private fun parseDocument(): MarkupDocument {
        val roots = mutableListOf<MarkupElement>()
        code(null, roots, stopAtBrace = false)
        return MarkupDocument(text, roots, opaque)
    }

    private fun at(index: Int): Char = if (index in text.indices) text[index] else '\u0000'

    private fun slice(from: Int, length: Int): String = text.substring(from.coerceAtMost(text.length), (from + length).coerceAtMost(text.length))

    // ---------------------------------------------------------------- C#

    /** Reads C# until the end (or an unmatched `}` when [stopAtBrace]), parsing the markup in it. */
    private fun code(parent: MarkupElement?, output: MutableList<MarkupElement>, stopAtBrace: Boolean) {
        var last = '\u0000'
        var braces = 0

        while (position < text.length) {
            val c = text[position]

            if (c.isWhitespace()) {
                position++
                continue
            }
            // A directive line (@model, @using …) is not C#, and does not change whether a statement may begin
            if (c == '@' && parent == null && isFirstOnLine(position) && DIRECTIVE.containsMatchIn(slice(position, 16))) {
                skipTo('\n')
                continue
            }
            if (c == '#' && isFirstOnLine(position)) {
                skipTo('\n')
                continue
            }
            if (skipCommentOrString()) {
                if (at(position - 1) == '"' || at(position - 1) == '\'') {
                    last = '"'
                }
                continue
            }
            if (c == '<' && last in STATEMENT_ENDS) {
                if (text.startsWith("<!--", position)) {
                    skipDelimited("<!--", "-->")
                    continue
                }
                val name = peekTagName(position)
                if (name != null && schema.element(name) != null) {
                    val element = element(name, parent)
                    (parent?.children ?: output) += element
                    last = ';'
                    continue
                }
            }
            if (c == '{') {
                braces++
            } else if (c == '}') {
                if (braces == 0 && stopAtBrace) {
                    return
                }
                braces--
            }

            last = c
            position++
        }
    }

    /** Skips a comment (`@* *@`, `//`, `/* */`) or a C# string or character literal; returns whether it did. */
    private fun skipCommentOrString(): Boolean {
        val start = position
        val c = at(start)
        val next = at(start + 1)

        if (c == '@' && next == '*') {
            skipDelimited("@*", "*@")
            return true
        }
        if (c == '/' && next == '/') {
            skipTo('\n')
            opaque += TextSpan(start, position)
            return true
        }
        if (c == '/' && next == '*') {
            skipDelimited("/*", "*/")
            return true
        }

        // "…", @"…", $"…", $@"…", @$"…" and '…'
        val literal = STRING_START.find(slice(start, 3)) ?: return false
        val prefix = literal.groupValues[1]
        val quote = literal.groupValues[2][0]
        if (prefix.isNotEmpty() && quote == '\'') {
            return false
        }
        position = start + prefix.length + 1
        skipStringBody(quote, verbatim = '@' in prefix)
        opaque += TextSpan(start, position)
        return true
    }

    /** Skips the rest of a string up to and including its closing quote. */
    private fun skipStringBody(quote: Char, verbatim: Boolean) {
        while (position < text.length) {
            val c = text[position]
            if (!verbatim && c == '\\') {
                position += 2
                continue
            }
            if (!verbatim && c == '\n') {
                return // unterminated: stop at the end of the line
            }
            if (c == quote) {
                if (verbatim && at(position + 1) == quote) {
                    position += 2
                    continue
                }
                position++
                return
            }
            position++
        }
        position = position.coerceAtMost(text.length)
    }

    // ---------------------------------------------------------------- markup

    /** Parses an element whose `<` is at the position. */
    private fun element(name: String, parent: MarkupElement?): MarkupElement {
        val element = MarkupElement(name, schema.element(name) != null, position, parent)
        position = element.nameEnd
        depth++
        try {
            if (depth > MAX_DEPTH) {
                position = text.length
            } else if (startTag(element) && !element.selfClosing) {
                element.contentStart = position
                if (schema.element(name)?.content != "void") {
                    content(element)
                }
            }
        } finally {
            depth--
        }
        element.end = maxOf(position, element.nameEnd).coerceAtMost(text.length)
        return element
    }

    /** Reads attributes up to `>` or `/>`; false when the start tag is unfinished. */
    private fun startTag(element: MarkupElement): Boolean {
        while (position < text.length) {
            val c = text[position]
            when {
                c.isWhitespace() -> position++
                text.startsWith("/>", position) -> {
                    position += 2
                    element.selfClosing = true
                    element.startTagEnd = position
                    return true
                }
                c == '>' -> {
                    position++
                    element.startTagEnd = position
                    return true
                }
                c == '<' -> return false
                else -> {
                    val name = ATTRIBUTE_NAME.find(slice(position, 128))?.value
                    if (name == null) {
                        position++
                    } else {
                        val attribute = MarkupAttribute(name, position, position + name.length)
                        position = attribute.nameEnd
                        attributeValue(attribute)
                        element.attributes += attribute
                    }
                }
            }
        }
        return false
    }

    /** Reads `= "value"` after an attribute name, if present. */
    private fun attributeValue(attribute: MarkupAttribute) {
        var index = position
        while (at(index) == ' ' || at(index) == '\t') index++
        if (at(index) != '=') {
            return
        }
        index++
        while (at(index) == ' ' || at(index) == '\t') index++

        val quote = at(index)
        if (quote == '"' || quote == '\'') {
            attribute.quote = quote
            attribute.valueStart = index + 1
            val close = closingQuote(index + 1, quote, attribute.expressionSpans)
            attribute.unterminated = close < 0
            attribute.valueEnd = if (close < 0) lineEnd(index + 1) else close
            position = if (close < 0) attribute.valueEnd else close + 1
        } else {
            // Unquoted, such as title=@Model.Title: up to whitespace or the end of the tag
            var end = index
            while (end < text.length && !text[end].isWhitespace() && text[end] != '>') end++
            if (end > index && text[end - 1] == '/' && at(end) == '>') end--
            if (at(index) == '@' && end > index + 1) attribute.expressionSpans += TextSpan(index, end)
            attribute.valueStart = index
            attribute.valueEnd = end
            position = end
        }
        attribute.value = text.substring(attribute.valueStart, attribute.valueEnd)
    }

    /**
     * Finds the quote closing an attribute value, skipping `@( … )` and `@Name["…"]` so quotes inside an expression
     * do not end it; -1 when the line or text ends first.
     */
    private fun closingQuote(from: Int, quote: Char, expressions: MutableList<TextSpan>): Int {
        var index = from
        while (index < text.length) {
            val c = text[index]
            when {
                c == quote -> return index
                c == '\n' && text.substring(from, index).contains('<') -> return -1 // a new tag: the quote was forgotten
                c == '@' && at(index + 1) == '(' -> {
                    val end = balancedEnd(index + 1, '(', ')')
                    expressions += TextSpan(index, end)
                    index = end
                }
                c == '@' && (at(index + 1).isLetter() || at(index + 1) == '_') && !at(index - 1).isLetterOrDigit() -> {
                    val end = expressionEnd(index + 1)
                    expressions += TextSpan(index, end)
                    index = end
                }
                c == '@' && at(index + 1) == '@' -> index += 2
                else -> index++
            }
        }
        return -1
    }

    /** Reads an element's content up to its closing tag. */
    private fun content(element: MarkupElement) {
        while (position < text.length) {
            val c = text[position]

            if (c == '<' && at(position + 1) == '/') {
                val name = peekTagName(position + 1) ?: ""
                if (name == element.name) {
                    element.closeStart = position
                    val close = text.indexOf('>', position)
                    position = if (close < 0) text.length else close + 1
                    return
                }
                if (hasAncestor(element, name)) {
                    return // this element was left open; the ancestor closes here
                }
                val close = text.indexOf('>', position)
                position = if (close < 0) text.length else close + 1 // a stray closing tag
                continue
            }
            if (c == '<' && text.startsWith("<!--", position)) {
                skipDelimited("<!--", "-->")
                continue
            }
            if (c == '<') {
                val name = peekTagName(position)
                if (name != null) {
                    element.children += element(name, element)
                    continue
                }
            }
            if (c == '@') {
                val start = position
                if (atSign(element)) {
                    markText(element, start, position)
                }
                continue
            }
            if (!c.isWhitespace()) {
                markText(element, position, position + 1)
            }
            position++
        }
    }

    /** Records text written directly in an element's content, joining it to the run before. */
    private fun markText(element: MarkupElement, start: Int, end: Int) {
        val last = element.textSpans.lastOrNull()
        if (last != null && text.substring(last.end, start).isBlank()) {
            element.textSpans[element.textSpans.size - 1] = TextSpan(last.start, end)
        } else {
            element.textSpans += TextSpan(start, end)
        }
    }

    /** Handles an `@` inside content; returns whether it was text (an expression or `@@`). */
    private fun atSign(element: MarkupElement): Boolean {
        val start = position
        val next = at(start + 1)

        when (next) {
            '@' -> {
                position += 2
                element.escapeSpans += TextSpan(start, position)
                return true
            }
            '*' -> {
                skipDelimited("@*", "*@")
                return false
            }
            '{' -> {
                position += 2
                codeBlockBody(element)
                element.codeSpans += TextSpan(start, position)
                return false
            }
            '(' -> {
                position = balancedEnd(start + 1, '(', ')')
                element.expressionSpans += TextSpan(start, position)
                return true
            }
        }

        // The @ of an e-mail address (me@example.com) is text, as in the generator
        if (start > 0 && text[start - 1].isLetterOrDigit()) {
            position++
            return true
        }

        val word = WORD.find(slice(start + 1, 64))?.value
        return when {
            word != null && word in CODE_KEYWORDS -> {
                position = start + 1 + word.length
                controlFlow(element)
                element.codeSpans += TextSpan(start, position)
                false
            }
            word != null -> {
                position = expressionEnd(start + 1)
                element.expressionSpans += TextSpan(start, position)
                true
            }
            else -> {
                position++
                true
            }
        }
    }

    /** Reads `(…) { … }` after `@if`, `@foreach` and the like, with any `else` / `catch` that follows. */
    private fun controlFlow(element: MarkupElement) {
        while (true) {
            // Up to the block's {, over a condition in parentheses
            while (position < text.length && text[position] != '{') {
                when {
                    text[position] == '(' -> position = balancedEnd(position, '(', ')')
                    text[position] == '<' || (text[position] == '\n' && at(position - 1) == '\n') -> return // still being typed
                    else -> position++
                }
            }
            if (position >= text.length) {
                return
            }
            position++
            codeBlockBody(element)

            val after = CONTINUATION.find(slice(position, 64)) ?: return
            position += after.value.length
            if (after.groupValues[1] == "while") {
                position = balancedEnd(text.indexOf('(', position), '(', ')')
                return
            }
        }
    }

    /** Reads code up to and including the `}` that closes the block whose `{` was just passed. */
    private fun codeBlockBody(element: MarkupElement) {
        code(element, element.children, stopAtBrace = true)
        if (at(position) == '}') {
            position++
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The tag name after a `<` (or the `/` of `</`), or null when none follows. */
    private fun peekTagName(index: Int): String? = TAG_NAME.find(slice(index + 1, 64))?.value

    private fun hasAncestor(element: MarkupElement, name: String): Boolean {
        var current = element.parent
        while (current != null) {
            if (current.name == name) return true
            current = current.parent
        }
        return false
    }

    /** The end of an implicit expression such as `Model.Items[0].Name` or `L["Title"]` starting at [from]. */
    private fun expressionEnd(from: Int): Int {
        var index = from + (WORD.find(slice(from, 64))?.value?.length ?: 0)
        while (true) {
            val member = MEMBER.find(slice(index, 66))
            index = when {
                member != null -> index + member.value.length
                at(index) == '(' -> balancedEnd(index, '(', ')')
                at(index) == '[' -> balancedEnd(index, '[', ']')
                else -> return index
            }
        }
    }

    /** The offset just after the bracket closing the one at [from], skipping strings; the end of the text if unclosed. */
    private fun balancedEnd(from: Int, open: Char, close: Char): Int {
        if (from < 0) {
            return text.length
        }
        val saved = position
        var nesting = 0
        position = from
        try {
            while (position < text.length) {
                val c = text[position]
                if ((c == '"' || c == '\'' || c == '@' || c == '$') && skipCommentOrString()) {
                    continue
                }
                position++
                if (c == open) {
                    nesting++
                } else if (c == close && --nesting == 0) {
                    return position
                }
            }
            return text.length
        } finally {
            position = saved // the caller moves to the returned offset itself
        }
    }

    /** Skips from an opening delimiter to after the closing one (or the end), recording the range. */
    private fun skipDelimited(open: String, close: String) {
        val start = position
        val end = text.indexOf(close, start + open.length)
        position = if (end < 0) text.length else end + close.length
        opaque += TextSpan(start, position)
    }

    /** Moves to the next [character] (not past it), or the end. */
    private fun skipTo(character: Char) {
        val index = text.indexOf(character, position)
        position = if (index < 0) text.length else index
    }

    private fun lineEnd(from: Int): Int {
        val index = text.indexOf('\n', from)
        return if (index < 0) text.length else index
    }

    private fun isFirstOnLine(index: Int): Boolean {
        var i = index - 1
        while (i >= 0 && text[i] != '\n') {
            if (!text[i].isWhitespace()) return false
            i--
        }
        return true
    }
}
