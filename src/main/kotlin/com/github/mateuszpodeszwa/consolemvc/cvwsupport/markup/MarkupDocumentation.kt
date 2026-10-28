package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

/**
 * Formats schema entries as HTML for quick documentation and completion, so both show the same text. All schema text
 * is escaped.
 *
 * Example: `MarkupDocumentation.element(schema.element("box")!!)` starts with `<b>&lt;box&gt;</b> A bordered panel`.
 */
object MarkupDocumentation {
    /** Describes an element: what it is, what it may contain and its attributes. */
    fun element(element: SchemaElement): String = buildString {
        append("<p><b><code>&lt;").append(escape(element.name)).append("&gt;</code></b> ").append(escape(element.description)).append("</p>")
        append("<p><i>Content:</i> ").append(contentText(element.content))
        element.parents?.let { append("<br/><i>Only inside:</i> ").append(it.joinToString(", ") { name -> "<code>&lt;${escape(name)}&gt;</code>" }) }
        element.children?.let { append("<br/><i>Holds:</i> ").append(it.joinToString(", ") { name -> "<code>&lt;${escape(name)}&gt;</code>" }) }
        if (element.topLevelOnly) append("<br/><i>Only at the top level of a view</i>, not inside another element.")
        append("</p>")
        if (element.attributes.isNotEmpty()) {
            append("<table>")
            for (attribute in element.attributes) {
                append("<tr><td><code>").append(escape(attribute.name)).append("</code>")
                if (attribute.required) append(" <i>(required)</i>")
                append("</td><td>").append(escape(attribute.description)).append("</td></tr>")
            }
            append("</table>")
        }
    }

    /** Describes an attribute: what it does and what it accepts. */
    fun attribute(attribute: SchemaAttribute): String = buildString {
        append("<p><b><code>").append(escape(attribute.name)).append("</code></b> ").append(escape(attribute.description)).append("</p>")
        if (attribute.flag) {
            append("<p><i>A flag:</i> write it without a value, such as <code>").append(escape(attribute.name)).append("</code>.</p>")
        } else {
            append("<p><i>Accepts:</i> ").append(escape(attribute.accepts)).append("</p>")
        }
        if (attribute.required) append("<p><i>Required.</i></p>")
        if (attribute.interpolation) append("<p><i>May mix text and <code>@expressions</code>.</i></p>")
    }

    /** Describes a directive. */
    fun directive(directive: SchemaDirective): String =
        "<p><b><code>${escape(directive.syntax)}</code></b></p><p>${escape(directive.description)}</p>"

    /** Words for a content model, such as "none: write it self-closing" for void. */
    fun contentText(content: String): String = when (content) {
        "void" -> "none: write it self-closing, as <code>&lt;name /&gt;</code>"
        "inline" -> "text, <code>@expressions</code> and <code>&lt;br /&gt;</code>"
        "block" -> "elements; plain text becomes a <code>&lt;text&gt;</code>"
        "elements" -> "only its child elements"
        else -> escape(content)
    }

    /** Escapes text for HTML. Example: `escape("<b> & \"x\"")` is `&lt;b&gt; &amp; &quot;x&quot;`. */
    fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
