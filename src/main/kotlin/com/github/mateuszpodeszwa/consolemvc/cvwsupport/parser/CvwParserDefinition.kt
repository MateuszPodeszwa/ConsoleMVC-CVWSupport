package com.github.mateuszpodeszwa.consolemvc.cvwsupport.parser

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwLexer
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwTokenTypes
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.psi.CvwElementTypes
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.psi.CvwFile
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.extapi.psi.ASTWrapperPsiElement

class CvwParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = CvwLexer()
    override fun createParser(project: Project?): PsiParser = CvwParser()
    override fun getFileNodeType(): IFileElementType = CvwElementTypes.FILE
    override fun getCommentTokens(): TokenSet = CvwTokenTypes.COMMENTS
    override fun getWhitespaceTokens(): TokenSet = CvwTokenTypes.WHITESPACE
    override fun getStringLiteralElements(): TokenSet = TokenSet.create(CvwTokenTypes.CS_STRING)
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = CvwFile(viewProvider)
}
