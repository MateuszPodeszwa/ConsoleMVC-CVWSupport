package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

/** The kinds of suggestion, mapped to icons by the completion contributor. */
enum class CompletionKind { ELEMENT, ATTRIBUTE, VALUE, DIRECTIVE, CONSTRUCT, CLOSE_TAG }

/**
 * A suggestion: [text] replaces the range from [replaceStart] to [replaceEnd], and the caret goes to [caretOffset]
 * within [text] (its end when there is nothing to fill in).
 *
 * Example: the `<box>` element gives text `box>\n\t\n</box>` with the caret on the empty line.
 */
data class MarkupSuggestion(
    val label: String,
    val kind: CompletionKind,
    val detail: String,
    val documentationHtml: String,
    val text: String,
    val caretOffset: Int,
    val replaceStart: Int,
    val replaceEnd: Int,
    val priority: Int,
)

/**
 * Suggests what may be typed at a position of a view: elements after `<` (only those allowed there), the closing tag
 * after `</`, attributes inside a start tag (those not written yet), values inside an attribute's quotes, and
 * directives and constructs after `@`. Everything comes from the schema, so suggestions match what the build accepts.
 * The same rules as the VS Code extension's CompletionService.js.
 *
 * Example:
 * ```kotlin
 * MarkupCompletion(MarkupSchema.bundled).complete("<box bor", 8).map { it.label } // [border]
 * ```
 */
class MarkupCompletion(private val schema: MarkupSchema) {
    private companion object {
        val WORD_AFTER = Regex("^[\\w-]*")
        val TYPED_WORD = Regex("[\\w-]*$")
        val CLOSE_TAG = Regex("</([\\w-]*)$")
        val OPEN_TAG = Regex("<([\\w-]*)$")
        val AT = Regex("@([\\w{*]*)$")
        val LINE_START_AT = Regex("(^|\\n)[ \\t]*@[\\w{*]*$")

        /** Code constructs after `@`: label, text (with `|` where the caret goes), and what it is. */
        val CONSTRUCTS = listOf(
            Triple("@if", "if (|condition)\n{\n\t\n}", "Markup shown only when a condition holds"),
            Triple("@foreach", "foreach (var item in |Model.Items)\n{\n\t\n}", "Markup repeated for each item"),
            Triple("@{ }", "{\n\t|\n}", "A block of C# code"),
            Triple("@* *@", "* | *@", "A comment, removed from the view"),
        )
    }

    /**
     * The suggestions at [offset] of [text].
     *
     * Example: `complete("<box border=\"", 13)` offers `single`, `double` and the other borders.
     */
    fun complete(text: String, offset: Int): List<MarkupSuggestion> {
        val document = MarkupParser.parse(text, schema)
        if (document.isOpaque(offset)) {
            return emptyList()
        }

        val before = text.substring(maxOf(0, offset - 256), offset)
        val wordEnd = offset + (WORD_AFTER.find(text.substring(offset))?.value?.length ?: 0)

        // At the end of the tag name itself, the name is being typed: that is element completion below
        val startTag = document.startTagAt(offset)
        if (startTag != null && offset > startTag.nameEnd) {
            val value = startTag.attributes.firstOrNull { it.quote != null && offset >= it.valueStart && offset <= it.valueEnd }
            if (value != null) {
                return values(startTag, value, offset)
            }
            val typed = TYPED_WORD.find(before)?.value ?: ""
            val beforeWord = before.substring(0, before.length - typed.length)
            return if (beforeWord.lastOrNull()?.isWhitespace() == true) attributes(startTag, offset - typed.length, wordEnd) else emptyList()
        }

        CLOSE_TAG.find(before)?.let {
            return closeTag(document, offset - it.value.length, offset, text)
        }

        OPEN_TAG.find(before)?.let { match ->
            val chain = document.openElementsAt(offset - match.value.length)
            return elements(chain.lastOrNull()?.name, offset - match.groupValues[1].length, wordEnd)
        }

        val at = AT.find(before)
        if (at != null && !before.substring(0, before.length - at.groupValues[1].length).endsWith("@@")) {
            val lineStart = LINE_START_AT.containsMatchIn(before)
            val insideElement = document.openElementsAt(offset).isNotEmpty()
            return atItems(lineStart && !insideElement, offset - at.groupValues[1].length, wordEnd)
        }

        return emptyList()
    }

    private fun elements(parentName: String?, replaceStart: Int, replaceEnd: Int): List<MarkupSuggestion> =
        schema.elementsAllowedIn(parentName).mapIndexed { index, element ->
            val (snippet, caret) = elementSnippet(element)
            MarkupSuggestion(element.name, CompletionKind.ELEMENT, element.description, MarkupDocumentation.element(element),
                snippet, caret, replaceStart, replaceEnd, 1000 - index)
        }

    private fun attributes(element: MarkupElement, replaceStart: Int, replaceEnd: Int): List<MarkupSuggestion> {
        val schemaElement = schema.element(element.name) ?: return emptyList()
        val written = element.attributes.filter { it.start < replaceStart || it.start > replaceEnd }.map { it.name }.toSet()
        return schemaElement.attributes.filter { it.name !in written }.mapIndexed { index, attribute ->
            val text = if (attribute.flag) attribute.name else "${attribute.name}=\"\""
            val caret = if (attribute.flag) text.length else text.length - 1
            MarkupSuggestion(attribute.name, CompletionKind.ATTRIBUTE, attribute.description, MarkupDocumentation.attribute(attribute),
                text, caret, replaceStart, replaceEnd, (if (attribute.required) 2000 else 1000) - index)
        }
    }

    private fun values(element: MarkupElement, attribute: MarkupAttribute, offset: Int): List<MarkupSuggestion> {
        val schemaAttribute = schema.attribute(element.name, attribute.name) ?: return emptyList()
        val values = schemaAttribute.values ?: if (schemaAttribute.kind == "boolean") listOf("true", "false") else emptyList()
        return values.mapIndexed { index, value ->
            MarkupSuggestion(value.ifEmpty { "\"\"" }, CompletionKind.VALUE, schemaAttribute.accepts, MarkupDocumentation.attribute(schemaAttribute),
                value, value.length, attribute.valueStart, maxOf(offset, attribute.valueEnd), 1000 - index)
        }
    }

    private fun closeTag(document: MarkupDocument, tagStart: Int, offset: Int, text: String): List<MarkupSuggestion> {
        val open = document.openElementsAt(tagStart).lastOrNull { !it.isClosed } ?: return emptyList()
        val rest = Regex("^[\\w-]*>?").find(text.substring(offset))?.value ?: ""
        val closing = "</${open.name}>"
        return listOf(MarkupSuggestion(closing, CompletionKind.CLOSE_TAG, "Closes the <${open.name}> opened above", "",
            closing, closing.length, tagStart, offset + rest.length, 3000))
    }

    private fun atItems(directivesAllowed: Boolean, replaceStart: Int, replaceEnd: Int): List<MarkupSuggestion> {
        val items = mutableListOf<MarkupSuggestion>()
        if (directivesAllowed) {
            for (directive in schema.directives) {
                val text = directive.name.substring(1) + " "
                items += MarkupSuggestion(directive.name, CompletionKind.DIRECTIVE, directive.syntax, MarkupDocumentation.directive(directive),
                    text, text.length, replaceStart, replaceEnd, 2000)
            }
        }
        for ((label, template, detail) in CONSTRUCTS) {
            val caret = template.indexOf('|')
            items += MarkupSuggestion(label, CompletionKind.CONSTRUCT, detail, "", template.replace("|", ""), caret, replaceStart, replaceEnd, 1000)
        }
        return items
    }

    /**
     * The text that writes an element after `<`: its required attributes, then `/>` or its content and closing tag,
     * with where the caret goes.
     *
     * Example: `elementSnippet(partial)` is `partial name="" />` with the caret between the quotes.
     */
    fun elementSnippet(element: SchemaElement): Pair<String, Int> {
        val required = element.attributes
            .filter { it.required && !it.flag }
            .distinctBy { it.description } // one of each alias, such as color and colour
        val attributes = required.joinToString("") { " ${it.name}=\"\"" }
        val head = element.name + attributes
        val firstValue = if (required.isNotEmpty()) element.name.length + 1 + required[0].name.length + 2 else -1
        return when (element.content) {
            "void" -> "$head />".let { it to if (firstValue >= 0) firstValue else it.length }
            "inline" -> "$head></${element.name}>".let { it to if (firstValue >= 0) firstValue else head.length + 1 }
            else -> "$head>\n\t\n</${element.name}>".let { it to if (firstValue >= 0) firstValue else head.length + 3 }
        }
    }
}
