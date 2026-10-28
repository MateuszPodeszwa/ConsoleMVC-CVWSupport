package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * An attribute of a C-View Markup element, as described by the schema.
 *
 * Example: `schema.attribute("box", "border")?.values` is `[single, double, rounded, heavy, ascii, none]`.
 *
 * @property name The attribute's name, such as `border`.
 * @property description What it does.
 * @property required Whether the element needs it.
 * @property kind One of text, integer, number, number-list, date, boolean, colour, length, choice, route or model.
 * @property accepts A description of the values it accepts.
 * @property flag Whether it is written without a value, such as `bold`.
 * @property interpolation Whether it may mix text and `@expressions`.
 * @property values The values to offer, if there is a fixed or suggested set.
 * @property minimum The smallest whole number allowed, if limited.
 * @property maximum The largest whole number allowed, if limited.
 */
data class SchemaAttribute(
    val name: String,
    val description: String,
    val required: Boolean,
    val kind: String,
    val accepts: String,
    val flag: Boolean,
    val interpolation: Boolean,
    val values: List<String>?,
    val minimum: Long?,
    val maximum: Long?,
)

/**
 * An element of C-View Markup, as described by the schema.
 *
 * Example: `schema.element("table")?.children` is `[row]`.
 *
 * @property name The tag name, such as `box`.
 * @property description What it draws.
 * @property content One of void, inline, block or elements.
 * @property topLevelOnly Whether it may only be written at the top level of a view.
 * @property parents The only elements it may be inside, or null for any.
 * @property children The only elements it may hold, or null for any.
 * @property attributes Its attributes, in the generator's order.
 */
data class SchemaElement(
    val name: String,
    val description: String,
    val content: String,
    val topLevelOnly: Boolean,
    val parents: List<String>?,
    val children: List<String>?,
    val attributes: List<SchemaAttribute>,
)

/**
 * A directive such as `@model`.
 *
 * @property name The name with its @, such as `@model`.
 * @property syntax How it is written, such as `@model Namespace.Type`.
 * @property description What it does.
 */
data class SchemaDirective(val name: String, val syntax: String, val description: String)

/**
 * A diagnostic the generator reports, such as CVW105.
 *
 * @property id The identifier.
 * @property severity error or warning.
 * @property title A short description.
 */
data class SchemaDiagnostic(val id: String, val severity: String, val title: String)

/**
 * The C-View Markup schema (ConsoleMVC's `schema/cvw-schema.json`, written by its MarkupSchemaExport): elements,
 * attributes, directives and diagnostics, with lookups by name. Every editor feature asks it what is valid, so the
 * plugin knows exactly the markup of the ConsoleMVC version it was built with.
 *
 * Example:
 * ```kotlin
 * val schema = MarkupSchema.bundled
 * schema.elementsAllowedIn("table").map { it.name } // [row]
 * ```
 */
class MarkupSchema(
    /** Every element, in the generator's order. */
    val elements: List<SchemaElement>,
    /** The directives, such as @model. */
    val directives: List<SchemaDirective>,
    /** The CVWxxx diagnostics. */
    val diagnostics: List<SchemaDiagnostic>,
) {
    private val elementsByName = elements.associateBy { it.name }

    /**
     * Finds an element.
     *
     * Example: `schema.element("text")?.content` is `inline`.
     *
     * @param name The tag name.
     * @return The element, or null when the markup has no such element.
     */
    fun element(name: String): SchemaElement? = elementsByName[name]

    /**
     * Finds an attribute of an element.
     *
     * Example: `schema.attribute("progress", "value")?.kind` is `number`.
     */
    fun attribute(elementName: String, attributeName: String): SchemaAttribute? =
        element(elementName)?.attributes?.firstOrNull { it.name == attributeName }

    /**
     * Finds a directive, with or without its @.
     *
     * Example: `schema.directive("layout")?.syntax`.
     */
    fun directive(name: String): SchemaDirective? {
        val wanted = if (name.startsWith("@")) name else "@$name"
        return directives.firstOrNull { it.name == wanted }
    }

    /**
     * The elements that may be written inside a parent, or at the top level of a view.
     *
     * Example: `schema.elementsAllowedIn("box")` has `text` and `table` but not `menu`, which is top-level only.
     *
     * @param parentName The parent's tag name, or null for the top level (also inside @if and @foreach there).
     * @return The elements allowed there, in the generator's order.
     */
    fun elementsAllowedIn(parentName: String?): List<SchemaElement> {
        if (parentName == null) {
            return elements.filter { it.parents == null }
        }
        val parent = element(parentName) ?: return emptyList()
        return when {
            parent.content == "void" -> emptyList()
            parent.content == "inline" -> elements.filter { it.name == "br" } // inline content holds text and <br />
            parent.children != null -> parent.children.mapNotNull { element(it) }
            else -> elements.filter { !it.topLevelOnly && (it.parents == null || parentName in it.parents) }
        }
    }

    companion object {
        /** The newest schema format this plugin understands; a newer one changed the meaning of a field. */
        const val SUPPORTED_FORMAT_VERSION = 1

        /** The resource holding the schema shipped with the plugin. */
        const val RESOURCE = "/schema/cvw-schema.json"

        /**
         * The schema shipped with the plugin, read once.
         *
         * Example: `MarkupSchema.bundled.element("box")`.
         */
        val bundled: MarkupSchema by lazy {
            val stream = MarkupSchema::class.java.getResourceAsStream(RESOURCE)
                ?: error("The plugin's $RESOURCE is missing")
            parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }

        /**
         * Reads schema JSON.
         *
         * Example: `MarkupSchema.parse(File("cvw-schema.json").readText())`.
         *
         * @throws IllegalArgumentException When the text is not a schema, or of a newer format.
         */
        fun parse(json: String): MarkupSchema {
            val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
                ?: throw IllegalArgumentException("Not a C-View Markup schema: expected a JSON object.")
            val version = root.get("formatVersion")?.takeIf { it.isJsonPrimitive }?.asInt
            if (version == null || root.get("elements")?.isJsonArray != true) {
                throw IllegalArgumentException("Not a C-View Markup schema: expected formatVersion and elements.")
            }
            if (version > SUPPORTED_FORMAT_VERSION) {
                throw IllegalArgumentException(
                    "The schema's format $version is newer than this plugin understands ($SUPPORTED_FORMAT_VERSION); update the plugin."
                )
            }

            val elements = root.getAsJsonArray("elements").map { node ->
                val element = node.asJsonObject
                SchemaElement(
                    name = element.string("name"),
                    description = element.string("description"),
                    content = element.string("content"),
                    topLevelOnly = element.boolean("topLevelOnly"),
                    parents = element.stringList("parents"),
                    children = element.stringList("children"),
                    attributes = element.getAsJsonArray("attributes").map { attributeNode ->
                        val attribute = attributeNode.asJsonObject
                        SchemaAttribute(
                            name = attribute.string("name"),
                            description = attribute.string("description"),
                            required = attribute.boolean("required"),
                            kind = attribute.string("kind"),
                            accepts = attribute.string("accepts"),
                            flag = attribute.boolean("flag"),
                            interpolation = attribute.boolean("interpolation"),
                            values = attribute.stringList("values"),
                            minimum = attribute.long("minimum"),
                            maximum = attribute.long("maximum"),
                        )
                    },
                )
            }
            val directives = root.getAsJsonArray("directives")?.map {
                val directive = it.asJsonObject
                SchemaDirective(directive.string("name"), directive.string("syntax"), directive.string("description"))
            } ?: emptyList()
            val diagnostics = root.getAsJsonArray("diagnostics")?.map {
                val diagnostic = it.asJsonObject
                SchemaDiagnostic(diagnostic.string("id"), diagnostic.string("severity"), diagnostic.string("title"))
            } ?: emptyList()
            return MarkupSchema(elements, directives, diagnostics)
        }

        private fun JsonObject.string(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: ""

        private fun JsonObject.boolean(name: String): Boolean = get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false

        private fun JsonObject.long(name: String): Long? = get(name)?.takeIf { it.isJsonPrimitive }?.asLong

        private fun JsonObject.stringList(name: String): List<String>? =
            get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }
    }
}
