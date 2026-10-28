package com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupElement
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupParser
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupSchema
import com.intellij.psi.tree.IElementType

/**
 * A token: [type] from [start] to [end].
 *
 * Example: `CvwToken(1, 4, CvwTokenTypes.TAG_NAME)` is `box` in `<box>`.
 */
data class CvwToken(val start: Int, val end: Int, val type: IElementType)

/**
 * Splits a whole view into highlighting tokens. The view is parsed with [MarkupParser]; each character is then given
 * a kind (tag, attribute, value, text, transition, escape, entity, comment, directive, or C#) by painting the parsed
 * parts over each other, outer elements first; finally runs of C# are split by [CSharpTokenizer].
 *
 * ```text
 * kinds ← C# everywhere
 * paint directive lines (top level):  @model → MODEL_KEYWORD, its argument → DIRECTIVE_ARGUMENT
 * for each element, parents first:
 *     <name … > and </name>           → TAG_DELIMITER, TAG_NAME (UNKNOWN_TAG_NAME), TAG_SPACE
 *     attributes                      → ATTRIBUTE_NAME, ATTRIBUTE_EQ, ATTRIBUTE_VALUE; @expr inside → TRANSITION + C#
 *     content                         → MARKUP_TEXT; &entity; → ENTITY; @@ → ESCAPE
 *     @expr, @(…), @{…}, @if … { … }  → TRANSITION + C# (child elements paint over the C# afterwards)
 * paint @* *@ and <!-- -->            → MARKUP_COMMENT
 * tokens ← runs of one kind; runs of C# split by CSharpTokenizer
 * ```
 *
 * Example: `MarkupTokenizer(MarkupSchema.bundled).tokenize("<text>Hi</text>").map { it.type }` is
 * `[TAG_DELIMITER, TAG_NAME, TAG_DELIMITER, MARKUP_TEXT, TAG_DELIMITER, TAG_NAME, TAG_DELIMITER]`.
 */
class MarkupTokenizer(private val schema: MarkupSchema) {
    private companion object {
        val ENTITY = Regex("&(?:[A-Za-z][A-Za-z0-9]*|#[0-9]+|#x[0-9A-Fa-f]+);")
        val DIRECTIVE_LINE = Regex("(?m)^([ \\t]*)(@(model|using|inject|layout|refresh))\\b([^\\r\\n]*)")
    }

    /** Tokenises [text] whole; the tokens cover every character, in order. */
    fun tokenize(text: CharSequence): List<CvwToken> {
        val source = text.toString()
        val document = MarkupParser.parse(source, schema)
        val kinds = arrayOfNulls<IElementType>(source.length) // null: C#

        fun paint(start: Int, end: Int, type: IElementType?) {
            for (i in maxOf(0, start) until minOf(source.length, end)) kinds[i] = type
        }

        // Directive lines at the top level
        val elements = document.allElements()
        for (match in DIRECTIVE_LINE.findAll(source)) {
            val keyword = match.groups[2]!!
            if (elements.any { keyword.range.first > it.start && keyword.range.first < it.end } || document.isOpaque(keyword.range.first)) continue
            paint(keyword.range.first, keyword.range.last + 1, when (match.groupValues[3]) {
                "model" -> CvwTokenTypes.MODEL_KEYWORD
                "using" -> CvwTokenTypes.USING_KEYWORD
                else -> CvwTokenTypes.DIRECTIVE_KEYWORD
            })
            val argument = match.groups[4]!!
            val trimmed = argument.value.trim()
            if (trimmed.isNotEmpty()) {
                val argumentStart = argument.range.first + argument.value.indexOf(trimmed)
                paint(argumentStart, argumentStart + trimmed.length, CvwTokenTypes.DIRECTIVE_ARGUMENT)
            }
        }

        for (element in elements) paintElement(element, source, ::paint)

        for (span in document.opaqueSpans) {
            if (source.startsWith("@*", span.start) || source.startsWith("<!--", span.start)) {
                paint(span.start, span.end, CvwTokenTypes.MARKUP_COMMENT)
            }
        }

        return merge(source, kinds)
    }

    private fun paintElement(element: MarkupElement, source: String, paint: (Int, Int, IElementType?) -> Unit) {
        val tagEnd = if (element.startTagEnd < 0) element.end else element.startTagEnd
        paint(element.start, tagEnd, CvwTokenTypes.TAG_SPACE)
        paint(element.start, element.start + 1, CvwTokenTypes.TAG_DELIMITER)
        paint(element.start + 1, element.nameEnd, if (element.known) CvwTokenTypes.TAG_NAME else CvwTokenTypes.UNKNOWN_TAG_NAME)
        if (element.startTagEnd >= 0) {
            paint(element.startTagEnd - (if (element.selfClosing) 2 else 1), element.startTagEnd, CvwTokenTypes.TAG_DELIMITER)
        }

        for (attribute in element.attributes) {
            paint(attribute.start, attribute.nameEnd, CvwTokenTypes.ATTRIBUTE_NAME)
            if (attribute.valueStart < 0) continue
            val equals = source.indexOf('=', attribute.nameEnd)
            if (equals in attribute.nameEnd until attribute.valueStart) paint(equals, equals + 1, CvwTokenTypes.ATTRIBUTE_EQ)
            val quoted = attribute.quote != null
            val valueFrom = if (quoted) attribute.valueStart - 1 else attribute.valueStart
            val valueTo = if (quoted && !attribute.unterminated) attribute.valueEnd + 1 else attribute.valueEnd
            paint(valueFrom, valueTo, CvwTokenTypes.ATTRIBUTE_VALUE)
            for (expression in attribute.expressionSpans) {
                paint(expression.start, expression.end, null)
                paint(expression.start, expression.start + 1, CvwTokenTypes.TRANSITION)
            }
        }

        if (element.contentStart >= 0) {
            val contentEnd = if (element.closeStart >= 0) element.closeStart else element.end
            paint(element.contentStart, contentEnd, CvwTokenTypes.MARKUP_TEXT)
            for (match in ENTITY.findAll(source.substring(element.contentStart, contentEnd))) {
                paint(element.contentStart + match.range.first, element.contentStart + match.range.last + 1, CvwTokenTypes.ENTITY)
            }
            for (span in element.codeSpans + element.expressionSpans) {
                paint(span.start, span.end, null)
                paint(span.start, span.start + 1, CvwTokenTypes.TRANSITION)
            }
            for (span in element.escapeSpans) paint(span.start, span.end, CvwTokenTypes.ESCAPE)
        }

        if (element.closeStart >= 0) {
            paint(element.closeStart, element.end, CvwTokenTypes.TAG_SPACE)
            paint(element.closeStart, element.closeStart + 2, CvwTokenTypes.TAG_DELIMITER)
            val nameEnd = minOf(element.end, element.closeStart + 2 + element.name.length)
            paint(element.closeStart + 2, nameEnd, if (element.known) CvwTokenTypes.TAG_NAME else CvwTokenTypes.UNKNOWN_TAG_NAME)
            if (source.getOrNull(element.end - 1) == '>') paint(element.end - 1, element.end, CvwTokenTypes.TAG_DELIMITER)
        }
    }

    /** Joins characters of one kind into tokens, splitting C# with [CSharpTokenizer]. */
    private fun merge(source: String, kinds: Array<IElementType?>): List<CvwToken> {
        val tokens = mutableListOf<CvwToken>()
        var index = 0
        while (index < source.length) {
            val kind = kinds[index]
            var end = index + 1
            while (end < source.length && kinds[end] == kind) end++
            if (kind != null) {
                tokens += CvwToken(index, end, kind)
            } else {
                var position = index
                while (position < end) {
                    val (tokenEnd, type) = CSharpTokenizer.next(source, position, end)
                    tokens += CvwToken(position, tokenEnd, type)
                    position = tokenEnd
                }
            }
            index = end
        }
        return tokens
    }
}
