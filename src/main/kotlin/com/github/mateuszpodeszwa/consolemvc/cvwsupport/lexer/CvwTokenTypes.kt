package com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwLanguage
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

class CvwTokenType(debugName: String) : IElementType(debugName, CvwLanguage.INSTANCE) {
    override fun toString(): String = "CvwTokenType.${super.toString()}"
}

object CvwTokenTypes {
    // Directive tokens
    @JvmField val MODEL_KEYWORD = CvwTokenType("MODEL_KEYWORD")       // @model
    @JvmField val USING_KEYWORD = CvwTokenType("USING_KEYWORD")       // @using
    @JvmField val DIRECTIVE_ARGUMENT = CvwTokenType("DIRECTIVE_ARGUMENT") // type/namespace after directive

    // Code body tokens
    @JvmField val CODE_BODY = CvwTokenType("CODE_BODY")               // C# code lines

    // Structural tokens
    @JvmField val NEWLINE = CvwTokenType("NEWLINE")
    @JvmField val WHITE_SPACE = CvwTokenType("WHITE_SPACE")
    @JvmField val BAD_CHARACTER = CvwTokenType("BAD_CHARACTER")

    // C# keyword tokens (for syntax highlighting in the code body)
    @JvmField val CS_KEYWORD = CvwTokenType("CS_KEYWORD")
    @JvmField val CS_STRING = CvwTokenType("CS_STRING")
    @JvmField val CS_NUMBER = CvwTokenType("CS_NUMBER")
    @JvmField val CS_LINE_COMMENT = CvwTokenType("CS_LINE_COMMENT")
    @JvmField val CS_BLOCK_COMMENT = CvwTokenType("CS_BLOCK_COMMENT")
    @JvmField val CS_IDENTIFIER = CvwTokenType("CS_IDENTIFIER")
    @JvmField val CS_OPERATOR = CvwTokenType("CS_OPERATOR")
    @JvmField val CS_PUNCTUATION = CvwTokenType("CS_PUNCTUATION")  // ; ,
    @JvmField val CS_LBRACE = CvwTokenType("CS_LBRACE")            // {
    @JvmField val CS_RBRACE = CvwTokenType("CS_RBRACE")            // }
    @JvmField val CS_LPAREN = CvwTokenType("CS_LPAREN")            // (
    @JvmField val CS_RPAREN = CvwTokenType("CS_RPAREN")            // )
    @JvmField val CS_LBRACKET = CvwTokenType("CS_LBRACKET")        // [
    @JvmField val CS_RBRACKET = CvwTokenType("CS_RBRACKET")        // ]
    @JvmField val CS_DOT = CvwTokenType("CS_DOT")

    // Token sets for the parser
    @JvmField val COMMENTS = TokenSet.create(CS_LINE_COMMENT, CS_BLOCK_COMMENT)
    @JvmField val WHITESPACE = TokenSet.create(WHITE_SPACE, NEWLINE)
    @JvmField val DIRECTIVES = TokenSet.create(MODEL_KEYWORD, USING_KEYWORD)
}
