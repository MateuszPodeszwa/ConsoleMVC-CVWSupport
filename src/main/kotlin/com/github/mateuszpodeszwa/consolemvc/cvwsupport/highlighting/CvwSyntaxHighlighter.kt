package com.github.mateuszpodeszwa.consolemvc.cvwsupport.highlighting

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwLexer
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwTokenTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class CvwSyntaxHighlighter : SyntaxHighlighterBase() {

    companion object {
        // Directive highlighting
        val DIRECTIVE_KEYWORD = createTextAttributesKey(
            "CVW_DIRECTIVE_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD
        )
        val DIRECTIVE_ARGUMENT = createTextAttributesKey(
            "CVW_DIRECTIVE_ARGUMENT", DefaultLanguageHighlighterColors.CLASS_REFERENCE
        )

        // C# code body highlighting
        val CS_KEYWORD = createTextAttributesKey(
            "CVW_CS_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD
        )
        val CS_STRING = createTextAttributesKey(
            "CVW_CS_STRING", DefaultLanguageHighlighterColors.STRING
        )
        val CS_NUMBER = createTextAttributesKey(
            "CVW_CS_NUMBER", DefaultLanguageHighlighterColors.NUMBER
        )
        val CS_LINE_COMMENT = createTextAttributesKey(
            "CVW_CS_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT
        )
        val CS_BLOCK_COMMENT = createTextAttributesKey(
            "CVW_CS_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT
        )
        val CS_IDENTIFIER = createTextAttributesKey(
            "CVW_CS_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER
        )
        val CS_OPERATOR = createTextAttributesKey(
            "CVW_CS_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN
        )
        val CS_PUNCTUATION = createTextAttributesKey(
            "CVW_CS_PUNCTUATION", DefaultLanguageHighlighterColors.SEMICOLON
        )
        val CS_DOT = createTextAttributesKey(
            "CVW_CS_DOT", DefaultLanguageHighlighterColors.DOT
        )
        val BAD_CHARACTER = createTextAttributesKey(
            "CVW_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER
        )

        private val DIRECTIVE_KEYWORD_KEYS = arrayOf(DIRECTIVE_KEYWORD)
        private val DIRECTIVE_ARGUMENT_KEYS = arrayOf(DIRECTIVE_ARGUMENT)
        private val CS_KEYWORD_KEYS = arrayOf(CS_KEYWORD)
        private val CS_STRING_KEYS = arrayOf(CS_STRING)
        private val CS_NUMBER_KEYS = arrayOf(CS_NUMBER)
        private val CS_LINE_COMMENT_KEYS = arrayOf(CS_LINE_COMMENT)
        private val CS_BLOCK_COMMENT_KEYS = arrayOf(CS_BLOCK_COMMENT)
        private val CS_IDENTIFIER_KEYS = arrayOf(CS_IDENTIFIER)
        private val CS_OPERATOR_KEYS = arrayOf(CS_OPERATOR)
        private val CS_PUNCTUATION_KEYS = arrayOf(CS_PUNCTUATION)
        private val CS_DOT_KEYS = arrayOf(CS_DOT)
        private val BAD_CHARACTER_KEYS = arrayOf(BAD_CHARACTER)
        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }

    override fun getHighlightingLexer(): Lexer = CvwLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            CvwTokenTypes.MODEL_KEYWORD, CvwTokenTypes.USING_KEYWORD -> DIRECTIVE_KEYWORD_KEYS
            CvwTokenTypes.DIRECTIVE_ARGUMENT -> DIRECTIVE_ARGUMENT_KEYS
            CvwTokenTypes.CS_KEYWORD -> CS_KEYWORD_KEYS
            CvwTokenTypes.CS_STRING -> CS_STRING_KEYS
            CvwTokenTypes.CS_NUMBER -> CS_NUMBER_KEYS
            CvwTokenTypes.CS_LINE_COMMENT -> CS_LINE_COMMENT_KEYS
            CvwTokenTypes.CS_BLOCK_COMMENT -> CS_BLOCK_COMMENT_KEYS
            CvwTokenTypes.CS_IDENTIFIER -> CS_IDENTIFIER_KEYS
            CvwTokenTypes.CS_OPERATOR -> CS_OPERATOR_KEYS
            CvwTokenTypes.CS_PUNCTUATION -> CS_PUNCTUATION_KEYS
            CvwTokenTypes.CS_DOT -> CS_DOT_KEYS
            CvwTokenTypes.BAD_CHARACTER -> BAD_CHARACTER_KEYS
            else -> EMPTY_KEYS
        }
    }
}
