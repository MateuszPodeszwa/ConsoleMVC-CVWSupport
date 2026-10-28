package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

import java.io.File

/** Shared test helpers: the template's views, and the `|` cursor marker. */
object TestViews {
    /** The schema shipped with the plugin. */
    val schema: MarkupSchema = MarkupSchema.bundled

    /** The views of ConsoleMVC's template, in src/test/testData/markup. */
    val templateViews = listOf("FeaturesView.cvw", "StatusView.cvw", "GreetView.cvw", "_Layout.cvw")

    /** Reads a view from src/test/testData/markup. */
    fun read(name: String): String = File("src/test/testData/markup/$name").readText()

    /** Splits text at its `|` marker: the text without it, and the cursor's offset. Example: `cursor("<te|")`. */
    fun cursor(marked: String): Pair<String, Int> {
        val offset = marked.indexOf('|')
        require(offset >= 0) { "No | cursor marker in: $marked" }
        return marked.removeRange(offset, offset + 1) to offset
    }
}
