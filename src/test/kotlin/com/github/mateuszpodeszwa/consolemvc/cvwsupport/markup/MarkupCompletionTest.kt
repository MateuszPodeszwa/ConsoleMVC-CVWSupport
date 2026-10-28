package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.TestViews.cursor
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.TestViews.schema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupCompletionTest {
    private val completion = MarkupCompletion(schema)

    private fun complete(marked: String): List<MarkupSuggestion> {
        val (text, offset) = cursor(marked)
        return completion.complete(text, offset)
    }

    private fun labels(marked: String) = complete(marked).map { it.label }

    @Test
    fun `elements are offered where they are allowed`() {
        assertTrue("menu" in labels("<|"))
        assertEquals(listOf("row"), labels("<table>\n  <|\n</table>"))
        assertFalse("menu" in labels("<box><|</box>"))
    }

    @Test
    fun `an element writes its required attributes and closing tag, with the caret placed`() {
        val partial = complete("<|").first { it.label == "partial" }
        assertEquals("partial name=\"\" />", partial.text)
        assertEquals("partial name=\"".length, partial.caretOffset)
        val text = complete("<|").first { it.label == "text" }
        assertEquals("text></text>", text.text)
        assertEquals("text>".length, text.caretOffset)
        val box = complete("<|").first { it.label == "box" }
        assertEquals("box>\n\t\n</box>", box.text)
        assertEquals("box>\n\t".length, box.caretOffset)
    }

    @Test
    fun `a partly typed name is replaced`() {
        val (text, offset) = cursor("<box><te|</box>")
        val item = completion.complete(text, offset).first { it.label == "text" }
        assertEquals("te", text.substring(item.replaceStart, item.replaceEnd))
    }

    @Test
    fun `attributes not written yet are offered, flags without a value`() {
        val items = complete("<text bold |")
        assertFalse(items.any { it.label == "bold" })
        assertEquals("color=\"\"", items.first { it.label == "color" }.text)
        assertEquals("italic", complete("<text it|").first { it.label == "italic" }.text)
    }

    @Test
    fun `values are offered inside quotes and replace the value`() {
        assertEquals(listOf("single", "double", "rounded", "heavy", "ascii", "none"), labels("<box border=\"|\""))
        val (text, offset) = cursor("<box border=\"dou|ble\">")
        val item = completion.complete(text, offset).first()
        assertEquals("double", text.substring(item.replaceStart, item.replaceEnd))
    }

    @Test
    fun `the innermost open element is closed after a slash`() {
        assertEquals(listOf("</text>"), labels("<box><text>Hi</|"))
        assertTrue(labels("<text>x</text></|").isEmpty())
    }

    @Test
    fun `directives only at the top level, constructs anywhere, nothing in comments`() {
        assertTrue("@model" in labels("@|"))
        assertFalse("@model" in labels("<box>\n@|\n</box>"))
        assertTrue("@foreach" in labels("<box>\n@|\n</box>"))
        assertTrue(labels("@* <| *@").isEmpty())
        assertTrue(labels("<text>@@|</text>").isEmpty())
    }
}
