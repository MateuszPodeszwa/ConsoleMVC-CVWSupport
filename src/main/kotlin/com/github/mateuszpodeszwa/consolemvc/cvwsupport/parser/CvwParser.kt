package com.github.mateuszpodeszwa.consolemvc.cvwsupport.parser

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwTokenTypes
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.psi.CvwElementTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

class CvwParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()

        // Parse directive section
        parseDirectives(builder)

        // Parse code body (everything remaining)
        if (!builder.eof()) {
            val codeMarker = builder.mark()
            while (!builder.eof()) {
                builder.advanceLexer()
            }
            codeMarker.done(CvwElementTypes.CODE_BODY)
        }

        rootMarker.done(root)
        return builder.treeBuilt
    }

    private fun parseDirectives(builder: PsiBuilder) {
        while (!builder.eof()) {
            val tokenType = builder.tokenType

            when (tokenType) {
                CvwTokenTypes.MODEL_KEYWORD -> parseModelDirective(builder)
                CvwTokenTypes.USING_KEYWORD -> parseUsingDirective(builder)
                CvwTokenTypes.NEWLINE, CvwTokenTypes.WHITE_SPACE -> builder.advanceLexer()
                else -> return // First non-directive, non-whitespace token = start of code
            }
        }
    }

    private fun parseModelDirective(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume @model

        // Skip whitespace between keyword and argument
        while (builder.tokenType == CvwTokenTypes.WHITE_SPACE) {
            builder.advanceLexer()
        }

        // Consume directive argument (type name)
        if (builder.tokenType == CvwTokenTypes.DIRECTIVE_ARGUMENT) {
            builder.advanceLexer()
        }

        marker.done(CvwElementTypes.MODEL_DIRECTIVE)
    }

    private fun parseUsingDirective(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // consume @using

        while (builder.tokenType == CvwTokenTypes.WHITE_SPACE) {
            builder.advanceLexer()
        }

        if (builder.tokenType == CvwTokenTypes.DIRECTIVE_ARGUMENT) {
            builder.advanceLexer()
        }

        marker.done(CvwElementTypes.USING_DIRECTIVE)
    }
}
