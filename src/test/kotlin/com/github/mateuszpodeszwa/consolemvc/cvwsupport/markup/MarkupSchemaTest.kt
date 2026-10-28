package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.TestViews.schema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupSchemaTest {
    @Test
    fun `the bundled schema describes the elements`() {
        assertTrue(schema.elements.size > 20)
        assertEquals("block", schema.element("box")?.content)
        assertEquals(listOf("single", "double", "rounded", "heavy", "ascii", "none"), schema.attribute("box", "border")?.values)
        assertNull(schema.element("nope"))
        assertEquals("@layout", schema.directive("layout")?.name)
    }

    @Test
    fun `the top level allows elements without a required parent`() {
        val names = schema.elementsAllowedIn(null).map { it.name }
        assertTrue("menu" in names)
        assertFalse("row" in names)
    }

    @Test
    fun `placement follows parents, children and top-level-only rules`() {
        assertEquals(listOf("row"), schema.elementsAllowedIn("table").map { it.name })
        assertEquals(listOf("br"), schema.elementsAllowedIn("text").map { it.name })
        val inBox = schema.elementsAllowedIn("box").map { it.name }
        assertTrue("table" in inBox)
        assertFalse("menu" in inBox)
    }

    @Test
    fun `a newer format or something else is refused`() {
        assertThrows(IllegalArgumentException::class.java) { MarkupSchema.parse("""{"formatVersion": 2, "elements": []}""") }
        assertThrows(IllegalArgumentException::class.java) { MarkupSchema.parse("""{"hello": 1}""") }
        assertThrows(IllegalArgumentException::class.java) { MarkupSchema.parse("not json") }
    }
}
