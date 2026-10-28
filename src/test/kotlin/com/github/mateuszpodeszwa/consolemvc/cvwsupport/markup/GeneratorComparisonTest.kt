package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.TestViews.schema
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Compares [MarkupValidator] with ConsoleMVC's own generator, run through `cvw-preview`: thousands of one-line views
 * (every attribute of every element with valid, invalid and edge values) are checked by both, and every line where
 * one reports an error and the other does not is a failure. Set CVW_PREVIEW to the tool's path to run it; it is
 * skipped otherwise. The same views as the VS Code extension's scripts/compare-with-generator.js.
 */
class GeneratorComparisonTest {
    private val tool: String? = System.getenv("CVW_PREVIEW")?.takeIf { it.isNotBlank() }
    private val validator = MarkupValidator(schema)

    private val commonValues = listOf("", "junk", "0", "1", "-1", "5", "40", "50%", "100%", "150%", "*", "auto", "2*", "1.5",
        "#ff8800", "#FFF", "#12345", "red", "Red", "DARKGREY", "true", "false", "yes", "2026-01-01", "2026-02-30", "today+7",
        "1, 2, 3", "1 2", "1,,2", "Home/Index", "Admin/Users/Index", "A/B/C/D", "Orders/Details?id=42", "x?=1")

    private fun requiredAttributes(element: SchemaElement, except: SchemaAttribute?): String = element.attributes
        .filter { it.required && !it.flag && it.description != except?.description }
        .distinctBy { it.description }
        .joinToString("") {
            val value = when (it.kind) {
                "route" -> "Index"
                "choice" -> it.values!!.first()
                "number-list" -> "1"
                else -> "k"
            }
            " ${it.name}=\"$value\""
        }

    private fun sample(name: String): String {
        val element = schema.element(name)!!
        val attributes = requiredAttributes(element, null) + if (name == "option") " quit" else ""
        if (element.content == "void") return "<$name$attributes />"
        return "<$name$attributes>${element.children?.let { sample(it.first()) } ?: "x"}</$name>"
    }

    @Test
    fun `the validator reports the same lines as the generator`() {
        assumeTrue("Set CVW_PREVIEW to run the comparison with the generator", tool != null)

        val lines = mutableListOf<String>()
        val labels = mutableListOf<String>()
        for (element in schema.elements.filter { it.name != "partial" && it.name != "body" }) {
            for (attribute in element.attributes.filter { !it.flag }) {
                val values = (commonValues + attribute.values.orEmpty() +
                    listOfNotNull(attribute.minimum, attribute.minimum?.minus(1), attribute.maximum, attribute.maximum?.plus(1)).map { it.toString() })
                    .distinct()
                for (value in values) {
                    val extra = requiredAttributes(element, attribute) + if (element.name == "option" && attribute.name != "nav") " quit" else ""
                    val content = if (element.content == "void") null else element.children?.let { sample(it.first()) } ?: "x"
                    var markup = if (content == null) "<${element.name} ${attribute.name}=\"$value\"$extra />"
                    else "<${element.name} ${attribute.name}=\"$value\"$extra>$content</${element.name}>"
                    element.parents?.first()?.let { markup = "<$it>$markup</$it>" }
                    lines += markup
                    labels += "<${element.name} ${attribute.name}=\"$value\">"
                }
            }
        }

        // The tool stops reporting after many errors, so views are checked 40 lines at a time
        val generatorLines = mutableSetOf<Int>()
        val validatorLines = mutableSetOf<Int>()
        for (first in lines.indices step 40) {
            val text = lines.subList(first, minOf(lines.size, first + 40)).joinToString("\n")
            val process = ProcessBuilder(tool, "--stdin", "--width", "200").start()
            process.outputStream.use { it.write(text.toByteArray()) }
            val errors = process.errorStream.bufferedReader().readText()
            process.inputStream.readAllBytes()
            process.waitFor(30, TimeUnit.SECONDS)
            Regex("(?m)^(\\d+):\\d+: error").findAll(errors).forEach { generatorLines += it.groupValues[1].toInt() + first }
            validator.validate(text).forEach { problem -> validatorLines += text.substring(0, problem.start).count { it == '\n' } + 1 + first }
        }

        val differences = (1..lines.size).filter { (it in generatorLines) != (it in validatorLines) }
            .map { (if (it in generatorLines) "generator only: " else "validator only: ") + labels[it - 1] }
        assertEquals("${lines.size} views compared", emptyList<String>(), differences)
    }
}
