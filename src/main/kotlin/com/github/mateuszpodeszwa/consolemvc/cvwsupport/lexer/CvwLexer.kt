package com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Hand-written lexer for .cvw files.
 *
 * Two modes:
 * 1. DIRECTIVE mode (at file start): tokenizes @model/@using keywords and their arguments.
 *    Blank lines are allowed between directives. The first non-blank, non-directive line
 *    transitions to CODE mode.
 * 2. CODE mode: tokenizes the C# code body with basic token types for syntax highlighting
 *    (keywords, strings, numbers, comments, identifiers, operators, punctuation).
 */
class CvwLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset: Int = 0
    private var endOffset: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null
    private var inCodeMode: Boolean = false

    companion object {
        private val CS_KEYWORDS = setOf(
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
    }

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.inCodeMode = initialState == 1
        advance()
    }

    override fun getState(): Int = if (inCodeMode) 1 else 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= endOffset) {
            tokenType = null
            return
        }

        if (!inCodeMode) {
            advanceDirectiveMode()
        } else {
            advanceCodeMode()
        }
    }

    private fun advanceDirectiveMode() {
        val pos = tokenStart

        // Skip to see what's on this line
        if (pos < endOffset && buffer[pos] == '\n') {
            tokenEnd = pos + 1
            tokenType = CvwTokenTypes.NEWLINE
            return
        }

        if (pos < endOffset && buffer[pos] == '\r') {
            tokenEnd = if (pos + 1 < endOffset && buffer[pos + 1] == '\n') pos + 2 else pos + 1
            tokenType = CvwTokenTypes.NEWLINE
            return
        }

        // Check for whitespace
        if (pos < endOffset && (buffer[pos] == ' ' || buffer[pos] == '\t')) {
            var end = pos
            while (end < endOffset && (buffer[end] == ' ' || buffer[end] == '\t')) end++
            tokenEnd = end
            tokenType = CvwTokenTypes.WHITE_SPACE

            // Check if we're at the start of a line - peek ahead to decide if we should stay in directive mode
            // If after whitespace we see a non-directive, switch to code mode
            if (end < endOffset && buffer[end] != '\n' && buffer[end] != '\r') {
                val restOfLine = peekRestOfLine(end)
                val trimmed = restOfLine.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("@model ") && !trimmed.startsWith("@using ")) {
                    // This whitespace is part of the code body — switch to code mode
                    inCodeMode = true
                    tokenEnd = tokenStart // Reset and re-lex in code mode
                    advanceCodeMode()
                    return
                }
            }
            return
        }

        // Check for @model directive
        if (lookingAt(pos, "@model ")) {
            tokenEnd = pos + "@model".length
            tokenType = CvwTokenTypes.MODEL_KEYWORD
            return
        }

        // Check for @using directive
        if (lookingAt(pos, "@using ")) {
            tokenEnd = pos + "@using".length
            tokenType = CvwTokenTypes.USING_KEYWORD
            return
        }

        // If we're right after a directive keyword, read the argument (rest of line)
        if (tokenStart > startOffset) {
            val prevType = tokenType
            // We just read the keyword; now skip whitespace and read the argument
        }

        // Check if this is a directive argument (preceded by a directive keyword + space)
        // Look at what the previous token was — if we're after whitespace following a keyword,
        // read to end of line as the argument
        val lineStart = findLineStart(pos)
        val lineContent = getLineContent(lineStart)
        val trimmedLine = lineContent.trim()
        if (trimmedLine.startsWith("@model ") || trimmedLine.startsWith("@using ")) {
            // We're somewhere in a directive line. The current position should be the argument.
            val lineEnd = findLineEnd(pos)
            tokenEnd = lineEnd
            tokenType = CvwTokenTypes.DIRECTIVE_ARGUMENT
            return
        }

        // Not a directive — switch to code mode
        inCodeMode = true
        advanceCodeMode()
    }

    private fun advanceCodeMode() {
        val pos = tokenStart
        if (pos >= endOffset) {
            tokenType = null
            return
        }

        val ch = buffer[pos]

        // Newline
        if (ch == '\n') {
            tokenEnd = pos + 1
            tokenType = CvwTokenTypes.NEWLINE
            return
        }
        if (ch == '\r') {
            tokenEnd = if (pos + 1 < endOffset && buffer[pos + 1] == '\n') pos + 2 else pos + 1
            tokenType = CvwTokenTypes.NEWLINE
            return
        }

        // Whitespace
        if (ch == ' ' || ch == '\t') {
            var end = pos + 1
            while (end < endOffset && (buffer[end] == ' ' || buffer[end] == '\t')) end++
            tokenEnd = end
            tokenType = CvwTokenTypes.WHITE_SPACE
            return
        }

        // Line comment //
        if (ch == '/' && pos + 1 < endOffset && buffer[pos + 1] == '/') {
            var end = pos + 2
            while (end < endOffset && buffer[end] != '\n' && buffer[end] != '\r') end++
            tokenEnd = end
            tokenType = CvwTokenTypes.CS_LINE_COMMENT
            return
        }

        // Block comment /* */
        if (ch == '/' && pos + 1 < endOffset && buffer[pos + 1] == '*') {
            var end = pos + 2
            while (end < endOffset - 1) {
                if (buffer[end] == '*' && buffer[end + 1] == '/') {
                    end += 2
                    tokenEnd = end
                    tokenType = CvwTokenTypes.CS_BLOCK_COMMENT
                    return
                }
                end++
            }
            tokenEnd = endOffset
            tokenType = CvwTokenTypes.CS_BLOCK_COMMENT
            return
        }

        // String literals
        if (ch == '"') {
            // Check for verbatim string @"..."
            // Check for interpolated string $"..."
            // Check for raw/interpolated verbatim $@"..." or @$"..."
            tokenEnd = scanString(pos)
            tokenType = CvwTokenTypes.CS_STRING
            return
        }
        if (ch == '\'' ) {
            tokenEnd = scanCharLiteral(pos)
            tokenType = CvwTokenTypes.CS_STRING
            return
        }
        if (ch == '$' && pos + 1 < endOffset && buffer[pos + 1] == '"') {
            tokenEnd = scanInterpolatedString(pos)
            tokenType = CvwTokenTypes.CS_STRING
            return
        }
        if (ch == '$' && pos + 1 < endOffset && buffer[pos + 1] == '@' && pos + 2 < endOffset && buffer[pos + 2] == '"') {
            tokenEnd = scanVerbatimString(pos + 2)
            tokenType = CvwTokenTypes.CS_STRING
            return
        }
        if (ch == '@' && pos + 1 < endOffset && buffer[pos + 1] == '"') {
            tokenEnd = scanVerbatimString(pos + 1)
            tokenType = CvwTokenTypes.CS_STRING
            return
        }
        if (ch == '@' && pos + 1 < endOffset && buffer[pos + 1] == '$' && pos + 2 < endOffset && buffer[pos + 2] == '"') {
            tokenEnd = scanInterpolatedString(pos + 1)
            tokenType = CvwTokenTypes.CS_STRING
            return
        }

        // Numbers
        if (ch.isDigit() || (ch == '.' && pos + 1 < endOffset && buffer[pos + 1].isDigit())) {
            var end = pos + 1
            while (end < endOffset && (buffer[end].isDigit() || buffer[end] == '.' || buffer[end] == '_'
                        || buffer[end] == 'x' || buffer[end] == 'X'
                        || buffer[end] == 'b' || buffer[end] == 'B'
                        || buffer[end] in 'a'..'f' || buffer[end] in 'A'..'F'
                        || buffer[end] == 'L' || buffer[end] == 'l'
                        || buffer[end] == 'U' || buffer[end] == 'u'
                        || buffer[end] == 'F' || buffer[end] == 'f'
                        || buffer[end] == 'D' || buffer[end] == 'd'
                        || buffer[end] == 'M' || buffer[end] == 'm')) end++
            tokenEnd = end
            tokenType = CvwTokenTypes.CS_NUMBER
            return
        }

        // Identifiers and keywords
        if (ch.isLetter() || ch == '_') {
            var end = pos + 1
            while (end < endOffset && (buffer[end].isLetterOrDigit() || buffer[end] == '_')) end++
            val word = buffer.subSequence(pos, end).toString()
            tokenEnd = end
            tokenType = if (word in CS_KEYWORDS) CvwTokenTypes.CS_KEYWORD else CvwTokenTypes.CS_IDENTIFIER
            return
        }

        // Dot (separate for member access highlighting)
        if (ch == '.') {
            tokenEnd = pos + 1
            tokenType = CvwTokenTypes.CS_DOT
            return
        }

        // Operators
        if (ch in "+-*/%=<>!&|^~?:") {
            var end = pos + 1
            // Handle multi-character operators
            if (end < endOffset) {
                val next = buffer[end]
                if ((ch == '=' && next == '=') || (ch == '!' && next == '=') ||
                    (ch == '<' && next == '=') || (ch == '>' && next == '=') ||
                    (ch == '&' && next == '&') || (ch == '|' && next == '|') ||
                    (ch == '+' && next == '+') || (ch == '-' && next == '-') ||
                    (ch == '=' && next == '>') || (ch == '-' && next == '>') ||
                    (ch == '?' && next == '?') || (ch == '?' && next == '.')) {
                    end++
                }
            }
            tokenEnd = end
            tokenType = CvwTokenTypes.CS_OPERATOR
            return
        }

        // Braces, brackets, parens (with individual token types for brace matching)
        when (ch) {
            '{' -> { tokenEnd = pos + 1; tokenType = CvwTokenTypes.CS_LBRACE; return }
            '}' -> { tokenEnd = pos + 1; tokenType = CvwTokenTypes.CS_RBRACE; return }
            '(' -> { tokenEnd = pos + 1; tokenType = CvwTokenTypes.CS_LPAREN; return }
            ')' -> { tokenEnd = pos + 1; tokenType = CvwTokenTypes.CS_RPAREN; return }
            '[' -> { tokenEnd = pos + 1; tokenType = CvwTokenTypes.CS_LBRACKET; return }
            ']' -> { tokenEnd = pos + 1; tokenType = CvwTokenTypes.CS_RBRACKET; return }
        }

        // Other punctuation (semicolons, commas)
        if (ch in ";,") {
            tokenEnd = pos + 1
            tokenType = CvwTokenTypes.CS_PUNCTUATION
            return
        }

        // Bad character — anything else
        tokenEnd = pos + 1
        tokenType = CvwTokenTypes.BAD_CHARACTER
    }

    // --- Helper methods ---

    private fun lookingAt(pos: Int, text: String): Boolean {
        if (pos + text.length > endOffset) return false
        for (i in text.indices) {
            if (buffer[pos + i] != text[i]) return false
        }
        return true
    }

    private fun peekRestOfLine(from: Int): String {
        var end = from
        while (end < endOffset && buffer[end] != '\n' && buffer[end] != '\r') end++
        return buffer.subSequence(from, end).toString()
    }

    private fun findLineStart(pos: Int): Int {
        var p = pos - 1
        while (p >= startOffset && buffer[p] != '\n') p--
        return p + 1
    }

    private fun findLineEnd(pos: Int): Int {
        var p = pos
        while (p < endOffset && buffer[p] != '\n' && buffer[p] != '\r') p++
        return p
    }

    private fun getLineContent(lineStart: Int): String {
        return peekRestOfLine(lineStart)
    }

    private fun scanString(pos: Int): Int {
        // Regular string "..."
        var end = pos + 1
        while (end < endOffset) {
            when (buffer[end]) {
                '\\' -> end += 2 // Skip escaped character
                '"' -> return end + 1
                '\n', '\r' -> return end // Unterminated
                else -> end++
            }
        }
        return endOffset
    }

    private fun scanCharLiteral(pos: Int): Int {
        var end = pos + 1
        while (end < endOffset) {
            when (buffer[end]) {
                '\\' -> end += 2
                '\'' -> return end + 1
                '\n', '\r' -> return end
                else -> end++
            }
        }
        return endOffset
    }

    private fun scanInterpolatedString(pos: Int): Int {
        // $"..." — pos is at the $
        var end = pos + 2 // Skip $"
        var braceDepth = 0
        while (end < endOffset) {
            when (buffer[end]) {
                '\\' -> end += 2
                '{' -> { braceDepth++; end++ }
                '}' -> { braceDepth--; end++ }
                '"' -> if (braceDepth <= 0) return end + 1 else end++
                '\n', '\r' -> return end
                else -> end++
            }
        }
        return endOffset
    }

    private fun scanVerbatimString(pos: Int): Int {
        // @"..." — pos is at the @, so string starts at pos+1
        var end = pos + 1 // Skip opening "
        if (end < endOffset && buffer[end] == '"') end++ // Skip the " after @
        while (end < endOffset) {
            if (buffer[end] == '"') {
                if (end + 1 < endOffset && buffer[end + 1] == '"') {
                    end += 2 // Escaped quote ""
                } else {
                    return end + 1
                }
            } else {
                end++
            }
        }
        return endOffset
    }
}
