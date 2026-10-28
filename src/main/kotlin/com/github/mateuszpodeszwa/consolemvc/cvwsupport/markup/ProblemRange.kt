package com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup

/**
 * Turns the line and column of a reported problem into a range of the text to underline: the tag, attribute or word
 * that starts there, or one character.
 *
 * Example: in `<box>\n  <blink />`, line 2 column 3 is the range of `<blink`.
 */
object ProblemRange {
    private val TOKEN = Regex("^(?:</?[\\w-]+|@?[\\w.-]+|\\S)")

    /** The range for a line and column, both counted from 1. */
    fun fromLineColumn(text: String, line: Int, column: Int): TextSpan {
        val start = offsetOf(text, line, column)
        val token = TOKEN.find(text.substring(start, minOf(text.length, start + 256)))
        return TextSpan(start, start + (token?.value?.length ?: 0))
    }

    /** The offset of a line and column, clamped to the text. Example: `offsetOf("a\nbc", 2, 2)` is 3. */
    fun offsetOf(text: String, line: Int, column: Int): Int {
        var offset = 0
        for (current in 1 until line) {
            val next = text.indexOf('\n', offset)
            if (next < 0) return text.length
            offset = next + 1
        }
        val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
        return minOf(offset + maxOf(0, column - 1), lineEnd)
    }
}
