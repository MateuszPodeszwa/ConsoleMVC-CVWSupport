package com.github.mateuszpodeszwa.consolemvc.cvwsupport.highlighting

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwLexer
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwTokenTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

/**
 * Colours `.cvw` views: C-View Markup (tags, attributes, values, text, `@` transitions, escapes, entities, comments),
 * directives and C#. Every colour can be changed under Settings | Editor | Color Scheme | CVW (Console View).
 *
 * Example: `CvwSyntaxHighlighter().getTokenHighlights(CvwTokenTypes.TAG_NAME)` is `[CVW_TAG, CVW_TAG_NAME]`.
 */
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
        val CS_BRACES = createTextAttributesKey(
            "CVW_CS_BRACES", DefaultLanguageHighlighterColors.BRACES
        )
        val CS_PARENTHESES = createTextAttributesKey(
            "CVW_CS_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES
        )
        val CS_BRACKETS = createTextAttributesKey(
            "CVW_CS_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS
        )
        val CS_DOT = createTextAttributesKey(
            "CVW_CS_DOT", DefaultLanguageHighlighterColors.DOT
        )
        val BAD_CHARACTER = createTextAttributesKey(
            "CVW_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER
        )

        // C-View Markup highlighting, falling back to the colours of XML and HTML markup
        val TAG = createTextAttributesKey("CVW_TAG", XmlHighlighterColors.XML_TAG)
        val TAG_NAME = createTextAttributesKey("CVW_TAG_NAME", XmlHighlighterColors.XML_TAG_NAME)
        val UNKNOWN_TAG_NAME = createTextAttributesKey("CVW_UNKNOWN_TAG_NAME", CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES)
        val ATTRIBUTE_NAME = createTextAttributesKey("CVW_ATTRIBUTE_NAME", XmlHighlighterColors.XML_ATTRIBUTE_NAME)
        val ATTRIBUTE_VALUE = createTextAttributesKey("CVW_ATTRIBUTE_VALUE", XmlHighlighterColors.XML_ATTRIBUTE_VALUE)
        val MARKUP_TEXT = createTextAttributesKey("CVW_MARKUP_TEXT", HighlighterColors.TEXT)
        val TRANSITION = createTextAttributesKey("CVW_TRANSITION", DefaultLanguageHighlighterColors.METADATA)
        val ESCAPE = createTextAttributesKey("CVW_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
        val ENTITY = createTextAttributesKey("CVW_ENTITY", XmlHighlighterColors.XML_ENTITY_REFERENCE)
        val MARKUP_COMMENT = createTextAttributesKey("CVW_MARKUP_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)

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
        private val CS_BRACES_KEYS = arrayOf(CS_BRACES)
        private val CS_PARENTHESES_KEYS = arrayOf(CS_PARENTHESES)
        private val CS_BRACKETS_KEYS = arrayOf(CS_BRACKETS)
        private val CS_DOT_KEYS = arrayOf(CS_DOT)
        private val BAD_CHARACTER_KEYS = arrayOf(BAD_CHARACTER)
        private val TAG_KEYS = arrayOf(TAG)
        private val TAG_NAME_KEYS = arrayOf(TAG, TAG_NAME)
        private val UNKNOWN_TAG_NAME_KEYS = arrayOf(TAG, UNKNOWN_TAG_NAME)
        private val ATTRIBUTE_NAME_KEYS = arrayOf(TAG, ATTRIBUTE_NAME)
        private val ATTRIBUTE_VALUE_KEYS = arrayOf(TAG, ATTRIBUTE_VALUE)
        private val MARKUP_TEXT_KEYS = arrayOf(MARKUP_TEXT)
        private val TRANSITION_KEYS = arrayOf(TRANSITION)
        private val ESCAPE_KEYS = arrayOf(ESCAPE)
        private val ENTITY_KEYS = arrayOf(ENTITY)
        private val MARKUP_COMMENT_KEYS = arrayOf(MARKUP_COMMENT)
        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }

    override fun getHighlightingLexer(): Lexer = CvwLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            CvwTokenTypes.MODEL_KEYWORD, CvwTokenTypes.USING_KEYWORD, CvwTokenTypes.DIRECTIVE_KEYWORD -> DIRECTIVE_KEYWORD_KEYS
            CvwTokenTypes.TAG_DELIMITER, CvwTokenTypes.TAG_SPACE, CvwTokenTypes.ATTRIBUTE_EQ -> TAG_KEYS
            CvwTokenTypes.TAG_NAME -> TAG_NAME_KEYS
            CvwTokenTypes.UNKNOWN_TAG_NAME -> UNKNOWN_TAG_NAME_KEYS
            CvwTokenTypes.ATTRIBUTE_NAME -> ATTRIBUTE_NAME_KEYS
            CvwTokenTypes.ATTRIBUTE_VALUE -> ATTRIBUTE_VALUE_KEYS
            CvwTokenTypes.MARKUP_TEXT -> MARKUP_TEXT_KEYS
            CvwTokenTypes.TRANSITION -> TRANSITION_KEYS
            CvwTokenTypes.ESCAPE -> ESCAPE_KEYS
            CvwTokenTypes.ENTITY -> ENTITY_KEYS
            CvwTokenTypes.MARKUP_COMMENT -> MARKUP_COMMENT_KEYS
            CvwTokenTypes.DIRECTIVE_ARGUMENT -> DIRECTIVE_ARGUMENT_KEYS
            CvwTokenTypes.CS_KEYWORD -> CS_KEYWORD_KEYS
            CvwTokenTypes.CS_STRING -> CS_STRING_KEYS
            CvwTokenTypes.CS_NUMBER -> CS_NUMBER_KEYS
            CvwTokenTypes.CS_LINE_COMMENT -> CS_LINE_COMMENT_KEYS
            CvwTokenTypes.CS_BLOCK_COMMENT -> CS_BLOCK_COMMENT_KEYS
            CvwTokenTypes.CS_IDENTIFIER -> CS_IDENTIFIER_KEYS
            CvwTokenTypes.CS_OPERATOR -> CS_OPERATOR_KEYS
            CvwTokenTypes.CS_PUNCTUATION -> CS_PUNCTUATION_KEYS
            CvwTokenTypes.CS_LBRACE, CvwTokenTypes.CS_RBRACE -> CS_BRACES_KEYS
            CvwTokenTypes.CS_LPAREN, CvwTokenTypes.CS_RPAREN -> CS_PARENTHESES_KEYS
            CvwTokenTypes.CS_LBRACKET, CvwTokenTypes.CS_RBRACKET -> CS_BRACKETS_KEYS
            CvwTokenTypes.CS_DOT -> CS_DOT_KEYS
            CvwTokenTypes.BAD_CHARACTER -> BAD_CHARACTER_KEYS
            else -> EMPTY_KEYS
        }
    }
}
