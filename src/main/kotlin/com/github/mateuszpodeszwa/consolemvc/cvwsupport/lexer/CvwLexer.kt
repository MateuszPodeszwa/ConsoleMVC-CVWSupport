package com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupSchema
import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * The lexer of `.cvw` views: C-View Markup (tags, attributes, values, text, `@expressions`, comments), directives
 * (`@model`, `@using`, `@inject`, `@layout`, `@refresh`) and the C# around and inside the markup.
 *
 * Markup can only be told from C# with the whole view in hand (whether `<text` starts markup depends on what came
 * before), so the view is tokenised whole by [MarkupTokenizer] when lexing starts. Only the first token has the initial
 * state, so after an edit the editor lexes the view again from its start; views are small, so this stays fast.
 *
 * Example: `CvwLexer().apply { start("<box />") }.tokenType` is `TAG_DELIMITER`.
 */
class CvwLexer(private val schema: MarkupSchema = MarkupSchema.bundled) : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset = 0
    private var endOffset = 0
    private var tokens: List<CvwToken> = emptyList()
    private var index = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        tokens = MarkupTokenizer(schema).tokenize(buffer.subSequence(startOffset, endOffset))
        index = 0
    }

    override fun getState(): Int = if (index == 0) 0 else 1

    override fun getTokenType(): IElementType? = tokens.getOrNull(index)?.type

    override fun getTokenStart(): Int = startOffset + (tokens.getOrNull(index)?.start ?: (endOffset - startOffset))

    override fun getTokenEnd(): Int = startOffset + (tokens.getOrNull(index)?.end ?: (endOffset - startOffset))

    override fun advance() {
        index++
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset
}
