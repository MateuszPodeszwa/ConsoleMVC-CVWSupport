package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * A problem found in a view, over [start] to [end].
 *
 * @property error Whether it is an error rather than a warning.
 * @property code The generator's diagnostic, such as CVW105, when one applies.
 */
data class MarkupProblem(val start: Int, val end: Int, val error: Boolean, val code: String?, val message: String)

/**
 * Checks the markup of a view against the schema while it is typed, when the `cvw-preview` tool (which reports the
 * build's own diagnostics) is not installed: unknown elements and attributes, elements in the wrong place, missing
 * required attributes, values outside their choices or range, text directly in elements that hold only elements, and
 * unclosed elements. Values with `@expressions` are left to the build. Each value check mirrors the generator's
 * AttributeType; the VS Code extension's scripts/compare-with-generator.js compares these rules with the generator.
 *
 * Example:
 * ```kotlin
 * MarkupValidator(MarkupSchema.bundled).validate("<box border=\"dotted\" />")[0].message
 * // 'dotted' is not a valid border: expected one of 'single', 'double', …
 * ```
 */
class MarkupValidator(private val schema: MarkupSchema) {
    private companion object {
        val HEX_COLOUR = Regex("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")
        val LENGTH = Regex("^(?:auto|\\*|(\\d{1,9})(%?))$", RegexOption.IGNORE_CASE)
        val INTEGER = Regex("^[+-]?\\d{1,10}$")
        val NUMBER = Regex("^[+-]?(?:\\d+\\.?\\d*|\\.\\d+)$")
        val TODAY = Regex("^today(?:[+-]\\d{1,9})?$")
        val IDENTIFIER = Regex("^[\\p{L}_][\\p{L}\\p{Nd}_]*$")
        val EXPRESSION = Regex("(^|[^\\p{L}\\p{Nd}@])@(?=[\\p{L}_(])")

        /** Attributes of which exactly one must be written, a rule the schema does not express. */
        val EXCLUSIVE = mapOf(
            "option" to Triple(listOf("nav", "quit", "back"), "CVW109",
                "<option> needs exactly one of 'nav' (e.g. nav=\"Home/Index\"), 'quit' or 'back'.")
        )
    }

    /**
     * Checks a view; the problems are in source order.
     *
     * Example: `validate(text).filter { it.error }`.
     */
    fun validate(text: String): List<MarkupProblem> {
        val problems = mutableListOf<MarkupProblem>()
        for (element in MarkupParser.parse(text, schema).allElements()) {
            checkElement(element, problems)
        }
        return problems.sortedBy { it.start }
    }

    private fun checkElement(element: MarkupElement, problems: MutableList<MarkupProblem>) {
        val schemaElement = schema.element(element.name)
        if (schemaElement == null) {
            problems += MarkupProblem(element.start, element.nameEnd, true, "CVW105",
                "Unknown element <${element.name}>. To show a literal '<', write &lt; instead.")
            return
        }

        checkPlacement(element, schemaElement, problems)

        if (element.startTagEnd < 0) {
            problems += MarkupProblem(element.start, element.nameEnd, true, null, "The start tag of <${element.name}> is not finished with > or />.")
        } else if (!element.isClosed && schemaElement.content != "void") {
            problems += MarkupProblem(element.start, element.nameEnd, true, null, "<${element.name}> is not closed: add </${element.name}>.")
        }

        val seen = mutableSetOf<String>()
        for (attribute in element.attributes) {
            val schemaAttribute = schemaElement.attributes.firstOrNull { it.name == attribute.name }
            if (schemaAttribute == null) {
                problems += MarkupProblem(attribute.start, attribute.nameEnd, true, "CVW100",
                    "<${element.name}> has no attribute '${attribute.name}'. Valid attributes: ${schemaElement.attributes.joinToString(", ") { it.name }}.")
                continue
            }
            if (!seen.add(schemaAttribute.description)) { // aliases (color, colour) share a description
                problems += MarkupProblem(attribute.start, attribute.nameEnd, true, null, "'${attribute.name}' is set twice on <${element.name}>.")
            }
            if (attribute.unterminated) {
                problems += MarkupProblem(attribute.start, attribute.nameEnd, true, null, "The value of '${attribute.name}' has no closing quote.")
                continue
            }
            checkValue(schemaAttribute, attribute.value)?.let { message ->
                val hasValue = attribute.valueStart >= 0 && attribute.valueEnd > attribute.valueStart
                problems += if (hasValue) MarkupProblem(attribute.valueStart, attribute.valueEnd, true, null, message)
                else MarkupProblem(attribute.start, attribute.nameEnd, true, null, message)
            }
        }

        if (schemaElement.content == "elements" && element.textSpans.isNotEmpty()) {
            val span = element.textSpans[0]
            val allowed = schemaElement.children.orEmpty().joinToString(", ") { "<$it>" }
            problems += MarkupProblem(span.start, span.end, true, "CVW110",
                "Text and expressions are not allowed directly inside <${element.name}>; it may only contain $allowed.")
        }

        EXCLUSIVE[element.name]?.let { (names, code, message) ->
            if (names.count { element.attribute(it) != null } != 1) {
                problems += MarkupProblem(element.start, element.nameEnd, true, code, message)
            }
        }

        schemaElement.attributes
            .filter { it.required }
            .distinctBy { it.description }
            .filter { required -> schemaElement.attributes.none { it.description == required.description && element.attribute(it.name) != null } }
            .forEach { problems += MarkupProblem(element.start, element.nameEnd, true, null, "<${element.name}> needs the attribute '${it.name}'.") }
    }

    private fun checkPlacement(element: MarkupElement, schemaElement: SchemaElement, problems: MutableList<MarkupProblem>) {
        val parentName = element.parent?.name
        if (parentName != null && schema.element(parentName) == null) {
            return // the unknown parent is reported already
        }
        if (schema.elementsAllowedIn(parentName).any { it.name == element.name }) {
            return
        }
        val message = when {
            schemaElement.parents != null && parentName !in schemaElement.parents ->
                "<${element.name}> must be inside ${schemaElement.parents.joinToString(" or ") { "<$it>" }}."
            schemaElement.topLevelOnly -> "<${element.name}> must be at the top level of the view, not inside <$parentName>."
            else -> "<${element.name}> cannot be inside <$parentName>."
        }
        problems += MarkupProblem(element.start, element.nameEnd, true, null, message)
    }

    /**
     * Checks a literal value against what an attribute accepts; null when it is fine or contains an expression.
     *
     * Example: `checkValue(schema.attribute("box", "padding")!!, "99")` is `'99' is not a valid padding: …`.
     */
    fun checkValue(attribute: SchemaAttribute, value: String?): String? {
        if (value == null) {
            return if (attribute.flag || attribute.kind == "boolean") null else "'${attribute.name}' needs a value."
        }
        if (hasExpression(value)) {
            return null // an expression: only the build can check it
        }
        val text = value.trim()
        val valid = when (attribute.kind) {
            "choice" -> attribute.values.orEmpty().any { it.equals(text, ignoreCase = true) }
            "boolean" -> text.equals("true", true) || text.equals("false", true)
            "integer" -> INTEGER.matches(text) && text.toLong().let {
                it >= (attribute.minimum ?: Int.MIN_VALUE.toLong()) && it <= (attribute.maximum ?: Int.MAX_VALUE.toLong())
            }
            "number" -> NUMBER.matches(text)
            "number-list" -> text.split(Regex("[,\\s]+")).filter { it.isNotEmpty() }.all { NUMBER.matches(it) }
            "colour" -> text.isEmpty() || text.equals("default", true) ||
                (if (text.startsWith("#")) HEX_COLOUR.matches(text)
                else text.replace(Regex("[-_ ]"), "").lowercase() in attribute.values.orEmpty())
            "length" -> LENGTH.find(text)?.let { it.groupValues[2].isEmpty() || it.groupValues[1].toLong() <= 100 } ?: false
            "date" -> isDate(text.lowercase())
            "route" -> isRoute(text)
            else -> true // text and models are checked by the build
        }
        return if (valid) null else "'$value' is not a valid ${attribute.name}: expected ${attribute.accepts}."
    }

    /** Whether a value has an @expression; `@@` is an escaped @, and the @ of an e-mail address is text. */
    fun hasExpression(value: String): Boolean = EXPRESSION.containsMatchIn(value.replace("@@", ""))

    private fun isDate(text: String): Boolean {
        if (TODAY.matches(text)) return true
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(text)) return false
        return try {
            LocalDate.parse(text)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }

    private fun isRoute(text: String): Boolean {
        val query = text.indexOf('?')
        val parts = (if (query < 0) text else text.substring(0, query)).split('/')
        if (parts.size > 3 || !parts.all { IDENTIFIER.matches(it) }) return false
        return query < 0 || text.substring(query + 1).split('&').all { it.isEmpty() || (it[0] != '=' && it.isNotBlank()) }
    }
}
