package com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer

import com.intellij.psi.tree.IElementType

/**
 * Splits C# into tokens for highlighting: keywords, identifiers, strings, numbers, comments, operators and
 * punctuation, with one token type per bracket so brackets can be matched. Used by [CvwLexer] for the C# parts of a
 * view: code outside markup, `@{ }` blocks, `@if` conditions and `@expressions`.
 *
 * Example: `CSharpTokenizer.next("var x = 1;", 0, 10)` is `3 to CS_KEYWORD` (the token `var`).
 */
object CSharpTokenizer {
    private val KEYWORDS = setOf(
        "abstract", "as", "base", "bool", "break", "byte", "case", "catch",
        "char", "checked", "class", "const", "continue", "decimal", "default",
        "delegate", "do", "double", "else", "enum", "event", "explicit",
        "extern", "false", "finally", "fixed", "float", "for", "foreach",
        "goto", "if", "implicit", "in", "int", "interface", "internal",
        "is", "lock", "long", "namespace", "new", "null", "object",
        "operator", "out", "override", "params", "private", "protected",
        "public", "readonly", "ref", "return", "sbyte", "sealed", "short",
        "sizeof", "stackalloc", "static", "string", "struct", "switch",
        "this", "throw", "true", "try", "typeof", "uint", "ulong",
        "unchecked", "unsafe", "ushort", "using", "virtual", "void",
        "volatile", "while", "var", "dynamic", "yield", "async", "await",
        "when", "nameof", "record", "init", "required", "global"
    )

    /**
     * The token starting at [pos], which must be before [end].
     *
     * @return The token's end and type.
     */
    fun next(buffer: CharSequence, pos: Int, end: Int): Pair<Int, IElementType> {
        val ch = buffer[pos]
        fun at(index: Int): Char = if (index < end) buffer[index] else '\u0000'

        when {
            ch == '\n' -> return pos + 1 to CvwTokenTypes.NEWLINE
            ch == '\r' -> return (if (at(pos + 1) == '\n') pos + 2 else pos + 1) to CvwTokenTypes.NEWLINE
            ch == ' ' || ch == '\t' -> {
                var e = pos + 1
                while (e < end && (buffer[e] == ' ' || buffer[e] == '\t')) e++
                return e to CvwTokenTypes.WHITE_SPACE
            }
            ch == '/' && at(pos + 1) == '/' -> return lineEnd(buffer, pos, end) to CvwTokenTypes.CS_LINE_COMMENT
            ch == '/' && at(pos + 1) == '*' -> {
                var e = pos + 2
                while (e < end - 1 && !(buffer[e] == '*' && buffer[e + 1] == '/')) e++
                return minOf(end, e + 2) to CvwTokenTypes.CS_BLOCK_COMMENT
            }
            ch == '"' -> return scanString(buffer, pos, end, '"') to CvwTokenTypes.CS_STRING
            ch == '\'' -> return scanString(buffer, pos, end, '\'') to CvwTokenTypes.CS_STRING
            ch == '$' && at(pos + 1) == '"' -> return scanInterpolated(buffer, pos + 1, end) to CvwTokenTypes.CS_STRING
            (ch == '$' && at(pos + 1) == '@' && at(pos + 2) == '"') || (ch == '@' && at(pos + 1) == '"') ||
                (ch == '@' && at(pos + 1) == '$' && at(pos + 2) == '"') -> return scanVerbatim(buffer, pos, end) to CvwTokenTypes.CS_STRING
            ch == '@' && (at(pos + 1).isLetter() || at(pos + 1) == '_') -> {
                // A verbatim identifier such as @class
                var e = pos + 2
                while (e < end && (buffer[e].isLetterOrDigit() || buffer[e] == '_')) e++
                return e to CvwTokenTypes.CS_IDENTIFIER
            }
            ch == '#' && isAtLineStart(buffer, pos) -> return lineEnd(buffer, pos, end) to CvwTokenTypes.CS_LINE_COMMENT
            ch.isDigit() || (ch == '.' && at(pos + 1).isDigit()) -> {
                var e = pos + 1
                while (e < end && (buffer[e].isLetterOrDigit() || buffer[e] == '.' || buffer[e] == '_')) e++
                return e to CvwTokenTypes.CS_NUMBER
            }
            ch.isLetter() || ch == '_' -> {
                var e = pos + 1
                while (e < end && (buffer[e].isLetterOrDigit() || buffer[e] == '_')) e++
                val word = buffer.subSequence(pos, e).toString()
                return e to if (word in KEYWORDS) CvwTokenTypes.CS_KEYWORD else CvwTokenTypes.CS_IDENTIFIER
            }
            ch == '.' -> return pos + 1 to CvwTokenTypes.CS_DOT
            ch in "+-*/%=<>!&|^~?:" -> {
                val pair = "$ch${at(pos + 1)}"
                val double = pair in setOf("==", "!=", "<=", ">=", "&&", "||", "++", "--", "=>", "->", "??", "?.")
                return (if (double) pos + 2 else pos + 1) to CvwTokenTypes.CS_OPERATOR
            }
            ch == '{' -> return pos + 1 to CvwTokenTypes.CS_LBRACE
            ch == '}' -> return pos + 1 to CvwTokenTypes.CS_RBRACE
            ch == '(' -> return pos + 1 to CvwTokenTypes.CS_LPAREN
            ch == ')' -> return pos + 1 to CvwTokenTypes.CS_RPAREN
            ch == '[' -> return pos + 1 to CvwTokenTypes.CS_LBRACKET
            ch == ']' -> return pos + 1 to CvwTokenTypes.CS_RBRACKET
            ch == ';' || ch == ',' -> return pos + 1 to CvwTokenTypes.CS_PUNCTUATION
            else -> return pos + 1 to CvwTokenTypes.BAD_CHARACTER
        }
    }

    private fun lineEnd(buffer: CharSequence, pos: Int, end: Int): Int {
        var e = pos
        while (e < end && buffer[e] != '\n' && buffer[e] != '\r') e++
        return e
    }

    private fun isAtLineStart(buffer: CharSequence, pos: Int): Boolean {
        var p = pos - 1
        while (p >= 0 && (buffer[p] == ' ' || buffer[p] == '\t')) p--
        return p < 0 || buffer[p] == '\n' || buffer[p] == '\r'
    }

    private fun scanString(buffer: CharSequence, pos: Int, end: Int, quote: Char): Int {
        var e = pos + 1
        while (e < end) {
            when (buffer[e]) {
                '\\' -> e += 2
                quote -> return e + 1
                '\n', '\r' -> return e // unterminated
                else -> e++
            }
        }
        return end
    }

    private fun scanInterpolated(buffer: CharSequence, quoteAt: Int, end: Int): Int {
        var e = quoteAt + 1
        var braces = 0
        while (e < end) {
            when (buffer[e]) {
                '\\' -> e += 2
                '{' -> { braces++; e++ }
                '}' -> { braces--; e++ }
                '"' -> if (braces <= 0) return e + 1 else e++
                '\n', '\r' -> return e
                else -> e++
            }
        }
        return end
    }

    private fun scanVerbatim(buffer: CharSequence, pos: Int, end: Int): Int {
        var e = pos
        while (e < end && buffer[e] != '"') e++
        e++
        while (e < end) {
            if (buffer[e] == '"') {
                if (e + 1 < end && buffer[e + 1] == '"') e += 2 else return e + 1
            } else {
                e++
            }
        }
        return end
    }
}
