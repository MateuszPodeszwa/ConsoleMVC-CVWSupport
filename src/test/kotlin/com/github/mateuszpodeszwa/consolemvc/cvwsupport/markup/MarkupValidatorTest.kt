package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.TestViews.schema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupValidatorTest {
    private val validator = MarkupValidator(schema)

    private fun only(text: String): MarkupProblem {
        val problems = validator.validate(text)
        assertEquals(problems.toString(), 1, problems.size)
        return problems.single()
    }

    @Test
    fun `the template's views have no problems`() {
        for (name in TestViews.templateViews) {
            assertEquals(name, emptyList<MarkupProblem>(), validator.validate(TestViews.read(name)))
        }
    }

    @Test
    fun `unknown elements and attributes have the generator's codes`() {
        val text = "<box><blink /></box>"
        val unknown = only(text)
        assertEquals("CVW105", unknown.code)
        assertEquals("<blink", text.substring(unknown.start, unknown.end))
        assertEquals("CVW100", only("<text size=\"2\">x</text>").code)
    }

    @Test
    fun `values are checked as the generator checks them`() {
        assertTrue(only("<box border=\"dotted\"><text>x</text></box>").message.contains("not a valid border"))
        assertTrue(only("<box padding=\"21\"><text>x</text></box>").message.contains("not a valid padding"))
        assertTrue(only("<box width=\"101%\"><text>x</text></box>").message.contains("not a valid width"))
        assertTrue(only("<date name=\"d\" default=\"2026-02-30\" />").message.contains("not a valid default"))
        val valid = listOf(
            "<box border=\"Double\" padding=\"20\" width=\"100%\" color=\"#f80\" title-colour=\"dark-grey\"><text>x</text></box>",
            "<text color=\"\" wrap=\"TRUE\">x</text>",
            "<date name=\"d\" min=\"today-30\" max=\"2026-02-28\" />",
            "<sparkline values=\"1, 2.5 -3\" />",
            "<menu><option key=\"1\" nav=\"Orders/Details?id=42\">x</option></menu>",
            "<box padding=\"@Model.Padding\"><text>x</text></box>",
        ).joinToString("\n")
        assertEquals(emptyList<MarkupProblem>(), validator.validate(valid))
    }

    @Test
    fun `rules beyond single attributes`() {
        assertEquals("CVW109", only("<menu><option key=\"1\">x</option></menu>").code)
        assertEquals("CVW110", only("<table>oops<row><cell>x</cell></row></table>").code)
        assertTrue(only("<row><cell>x</cell></row>").message.contains("must be inside <table>"))
        assertTrue(only("<box><text>x</text>").message.contains("is not closed"))
        assertTrue(only("<partial />").message.contains("needs the attribute 'name'"))
        assertTrue(only("<text color=\"red\" colour=\"blue\">x</text>").message.contains("set twice"))
    }
}
