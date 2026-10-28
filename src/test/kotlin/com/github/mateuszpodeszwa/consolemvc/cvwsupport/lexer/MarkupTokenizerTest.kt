package com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.TestViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupTokenizerTest {
    private val tokenizer = MarkupTokenizer(TestViews.schema)

    /** Each token's text and type, for short views. */
    private fun tokens(text: String) = tokenizer.tokenize(text).map { text.substring(it.start, it.end) to it.type }

    private fun typeOf(text: String, token: String, nth: Int = 0) =
        tokens(text).filter { it.first == token }.getOrNull(nth)?.second ?: error("No token '$token' in ${tokens(text).map { it.first }}")

    @Test
    fun `tags, attributes, values and text`() {
        assertEquals(listOf(
            "<" to CvwTokenTypes.TAG_DELIMITER, "box" to CvwTokenTypes.TAG_NAME, " " to CvwTokenTypes.TAG_SPACE,
            "border" to CvwTokenTypes.ATTRIBUTE_NAME, "=" to CvwTokenTypes.ATTRIBUTE_EQ, "\"double\"" to CvwTokenTypes.ATTRIBUTE_VALUE,
            ">" to CvwTokenTypes.TAG_DELIMITER, "Hi" to CvwTokenTypes.MARKUP_TEXT,
            "</" to CvwTokenTypes.TAG_DELIMITER, "box" to CvwTokenTypes.TAG_NAME, ">" to CvwTokenTypes.TAG_DELIMITER,
        ), tokens("<box border=\"double\">Hi</box>"))
    }

    @Test
    fun `expressions are a transition then C#`() {
        val text = "<text color=\"@Model.Colour\">Hi @Model.Name!</text>"
        assertEquals(CvwTokenTypes.TRANSITION, typeOf(text, "@", 0))
        assertEquals(CvwTokenTypes.CS_IDENTIFIER, typeOf(text, "Colour"))
        assertEquals(CvwTokenTypes.TRANSITION, typeOf(text, "@", 1))
        assertEquals(CvwTokenTypes.CS_IDENTIFIER, typeOf(text, "Name"))
        assertEquals(CvwTokenTypes.MARKUP_TEXT, typeOf(text, "!"))
    }

    @Test
    fun `directives, comments, escapes and entities`() {
        val text = "@model Shop.Models.Cart\n@layout none\n@* note *@\n<text>@@ &lt;</text>"
        assertEquals(CvwTokenTypes.MODEL_KEYWORD, typeOf(text, "@model"))
        assertEquals(CvwTokenTypes.DIRECTIVE_ARGUMENT, typeOf(text, "Shop.Models.Cart"))
        assertEquals(CvwTokenTypes.DIRECTIVE_KEYWORD, typeOf(text, "@layout"))
        assertEquals(CvwTokenTypes.MARKUP_COMMENT, typeOf(text, "@* note *@"))
        assertEquals(CvwTokenTypes.ESCAPE, typeOf(text, "@@"))
        assertEquals(CvwTokenTypes.ENTITY, typeOf(text, "&lt;"))
    }

    @Test
    fun `markup inside @foreach is markup and the loop is C#`() {
        val text = "<table>\n@foreach (var r in Model.Rows)\n{\n<row><cell>@r</cell></row>\n}\n</table>"
        assertEquals(CvwTokenTypes.CS_KEYWORD, typeOf(text, "foreach"))
        assertEquals(CvwTokenTypes.TAG_NAME, typeOf(text, "row"))
        assertEquals(CvwTokenTypes.CS_RBRACE, typeOf(text, "}"))
    }

    @Test
    fun `top-level C# stays C#, and unknown tags inside elements are marked`() {
        assertEquals(CvwTokenTypes.CS_KEYWORD, typeOf("return NavigationResult.Quit();", "return"))
        assertEquals(CvwTokenTypes.UNKNOWN_TAG_NAME, typeOf("<box><blink /></box>", "blink"))
    }

    @Test
    fun `tokens cover every character of the template's views, in order`() {
        for (name in TestViews.templateViews) {
            val text = TestViews.read(name)
            val tokens = tokenizer.tokenize(text)
            var expected = 0
            for (token in tokens) {
                assertEquals(name, expected, token.start)
                assertTrue(name, token.end > token.start)
                expected = token.end
            }
            assertEquals(name, text.length, expected)
            assertTrue(name, tokens.none { it.type == CvwTokenTypes.UNKNOWN_TAG_NAME || it.type == CvwTokenTypes.BAD_CHARACTER })
        }
    }
}
