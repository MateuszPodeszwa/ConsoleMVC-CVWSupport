package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

/**
 * A range of text: from [start] (inclusive) to [end] (exclusive).
 *
 * Example: `TextSpan(5, 11)` covers `border` in `<box border="double">`.
 */
data class TextSpan(val start: Int, val end: Int) {
    /** Whether [offset] is strictly inside, as for a comment around a cursor. */
    fun containsStrictly(offset: Int): Boolean = offset > start && offset < end
}

/**
 * An attribute of a start tag, with where its name and value are in the text.
 *
 * Example: for `<box border="double">`, [name] is `border`, [value] is `double` and [valueStart] the offset of `d`.
 *
 * @property name The attribute's name.
 * @property start The offset of its first character.
 * @property nameEnd The offset just after its name.
 */
class MarkupAttribute(val name: String, val start: Int, val nameEnd: Int) {
    /** The value without quotes; null for a flag such as `bold`. */
    var value: String? = null

    /** The offset of the value's first character (after the quote), or -1 for a flag. */
    var valueStart: Int = -1

    /** The offset just after the value (before the closing quote), or -1 for a flag. */
    var valueEnd: Int = -1

    /** The quote used, or null for an unquoted value or a flag. */
    var quote: Char? = null

    /** Whether the closing quote is missing. */
    var unterminated: Boolean = false

    /** The `@expressions` in the value, each from its `@` to its end. */
    val expressionSpans: MutableList<TextSpan> = mutableListOf()
}

/**
 * An element of a view: its tag, attributes, children and where each part is in the text. An element whose closing
 * tag is missing has [closeStart] -1 and ends where the parser stopped.
 *
 * Example: for `<box><text>x</text></box>`, `box.children[0].name` is `text`.
 *
 * @property name The tag name.
 * @property known Whether the schema has this element.
 * @property start The offset of the `<`.
 * @property parent The enclosing element, or null at the top level.
 */
class MarkupElement(val name: String, val known: Boolean, val start: Int, val parent: MarkupElement?) {
    /** The offset just after the tag name. */
    val nameEnd: Int = start + 1 + name.length

    /** The attributes, in order. */
    val attributes: MutableList<MarkupAttribute> = mutableListOf()

    /** The child elements, in order. */
    val children: MutableList<MarkupElement> = mutableListOf()

    /** Runs of text and @expressions written directly in the content. */
    val textSpans: MutableList<TextSpan> = mutableListOf()

    /** The `@expressions` in the content, each from its `@` to its end, such as `@Model.Name` or `@(a + b)`. */
    val expressionSpans: MutableList<TextSpan> = mutableListOf()

    /** The blocks of C# in the content: `@{ … }` and `@if (…) { … } else { … }`, each from its `@`. */
    val codeSpans: MutableList<TextSpan> = mutableListOf()

    /** The `@@` escapes in the content. */
    val escapeSpans: MutableList<TextSpan> = mutableListOf()

    /** The offset just after the start tag's `>` or `/>`, or -1 when the start tag is unfinished. */
    var startTagEnd: Int = -1

    /** Whether the start tag ends with `/>`. */
    var selfClosing: Boolean = false

    /** The offset where the content begins, or -1 when there is none. */
    var contentStart: Int = -1

    /** The offset of the closing tag's `<`, or -1 when it is missing. */
    var closeStart: Int = -1

    /** The offset just after the element. */
    var end: Int = start

    /** Whether the element is finished: self-closing, or with its closing tag. */
    val isClosed: Boolean get() = selfClosing || closeStart >= 0

    /** The first attribute with [name], or null. */
    fun attribute(name: String): MarkupAttribute? = attributes.firstOrNull { it.name == name }
}

/**
 * A parsed view: the elements in a tree, and the ranges of comments and C# strings, where no markup is.
 * Built by [MarkupParser]; read by completion, documentation and checks.
 *
 * Example:
 * ```kotlin
 * val document = MarkupParser.parse(text, MarkupSchema.bundled)
 * document.openElementsAt(offset).map { it.name } // [table, row] inside a row
 * ```
 */
class MarkupDocument(val text: String, val roots: List<MarkupElement>, val opaqueSpans: List<TextSpan>) {
    /** Every element, parents before their children, in source order. */
    fun allElements(): List<MarkupElement> {
        val all = mutableListOf<MarkupElement>()
        fun visit(element: MarkupElement) {
            all += element
            element.children.forEach(::visit)
        }
        roots.forEach(::visit)
        return all
    }

    /**
     * The elements whose content holds [offset], outermost first; empty at the top level.
     *
     * Example: for `<box><text>x</text></box>` at the `x`, `[box, text]`.
     */
    fun openElementsAt(offset: Int): List<MarkupElement> {
        val chain = mutableListOf<MarkupElement>()
        var candidates: List<MarkupElement> = roots
        while (true) {
            val inside = candidates.firstOrNull {
                it.contentStart in 0..offset && offset <= (if (it.closeStart < 0) it.end else it.closeStart)
            } ?: return chain
            chain += inside
            candidates = inside.children
        }
    }

    /**
     * The element whose start tag holds [offset], between its name and its closing `>`, or null.
     *
     * Example: in `<box border="double">` at `border`, the box.
     */
    fun startTagAt(offset: Int): MarkupElement? = allElements().firstOrNull {
        val tagEnd = if (it.startTagEnd < 0) it.end else it.startTagEnd - (if (it.selfClosing) 2 else 1)
        offset >= it.nameEnd && offset <= tagEnd
    }

    /** Whether [offset] is inside a comment or a C# string, where completion and checks do not apply. */
    fun isOpaque(offset: Int): Boolean = opaqueSpans.any { it.containsStrictly(offset) }
}
