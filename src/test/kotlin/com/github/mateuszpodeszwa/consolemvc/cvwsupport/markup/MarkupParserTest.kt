package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.TestViews.schema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupParserTest {
    private fun parse(text: String) = MarkupParser.parse(text, schema)

    @Test
    fun `elements, attributes and children are read`() {
        val box = parse("<box title=\"A\" border=\"double\"><text bold>Hi</text></box>").roots.single()
        assertEquals(listOf("title" to "A", "border" to "double"), box.attributes.map { it.name to it.value })
        assertEquals("text", box.children.single().name)
        assertNull(box.children.single().attribute("bold")?.value)
        assertTrue(box.isClosed)
    }

    @Test
    fun `directives do not stop markup from starting`() {
        assertEquals(listOf("text"), parse("@model Shop.Models.Cart\n@layout none\n<text>x</text>").roots.map { it.name })
    }

    @Test
    fun `at the top level, markup starts only where a statement may begin`() {
        val document = parse("var list = new List<text>();\n<text>real</text>")
        assertEquals(document.text.indexOf("<text>real"), document.roots.single().start)
    }

    @Test
    fun `unknown tags are C# at the top level and elements inside elements`() {
        assertTrue(parse("<blink>x</blink>").roots.isEmpty())
        assertFalse(parse("<box><blink>x</blink></box>").roots.single().children.single().known)
    }

    @Test
    fun `markup inside @foreach and @if belongs to the enclosing element`() {
        val table = parse("<table>\n@foreach (var x in Model.Items)\n{\n  <row><cell>@x</cell></row>\n}\n@if (a) { <row /> } else { <row /> }\n</table>").roots.single()
        assertEquals(listOf("row", "row", "row"), table.children.map { it.name })
        assertTrue(table.isClosed)
    }

    @Test
    fun `quotes inside an attribute expression do not end the value`() {
        val box = parse("<box title=\"@L[\"Title\"]\" border=\"rounded\"><text>x</text></box>").roots.single()
        assertEquals("@L[\"Title\"]", box.attribute("title")?.value)
        assertEquals("rounded", box.attribute("border")?.value)
    }

    @Test
    fun `an unclosed element runs to its parent's closing tag, and unfinished tags are kept`() {
        val box = parse("<box><text>x</box><br />").roots.first()
        assertFalse(box.children.single().isClosed)
        assertTrue(box.isClosed)
        assertEquals(-1, parse("<box bor").roots.single().startTagEnd)
    }

    @Test
    fun `comments and strings are opaque`() {
        val text = "@* <text>no</text> *@\nvar s = \"<text>no</text>\";\n<text>yes</text>"
        val document = parse(text)
        assertEquals(1, document.roots.size)
        assertTrue(document.isOpaque(text.indexOf("no")))
        assertFalse(document.isOpaque(text.indexOf("yes")))
    }

    @Test
    fun `open elements and start tags are found at an offset`() {
        val text = "<table><row><cell>x</cell></row></table>"
        assertEquals(listOf("table", "row", "cell"), parse(text).openElementsAt(text.indexOf("x")).map { it.name })
        val tag = "<box border=\"double\"><text>x</text></box>"
        assertEquals("box", parse(tag).startTagAt(tag.indexOf("border"))?.name)
    }

    @Test
    fun `text in content is recorded, and an e-mail address is text`() {
        assertEquals(1, parse("<table>oops @x<row><cell>a</cell></row></table>").roots.single().textSpans.size)
        assertTrue(parse("<text>me@example.com</text>").roots.single().isClosed)
    }

    @Test
    fun `the template's views parse completely`() {
        for (name in TestViews.templateViews) {
            val elements = parse(TestViews.read(name)).allElements()
            assertTrue(name, elements.size > 3)
            assertTrue(name, elements.all { it.known && it.isClosed })
        }
    }

    @Test
    fun `deep nesting stops without overflowing`() {
        parse("<box>".repeat(5000))
    }
}
