package com.github.mateuszpodeszwa.consolemvc.cvwsupport

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwTokenTypes
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class CvwBraceMatcher : PairedBraceMatcher {
    companion object {
        private val PAIRS = arrayOf(
            BracePair(CvwTokenTypes.CS_LBRACE, CvwTokenTypes.CS_RBRACE, true),
            BracePair(CvwTokenTypes.CS_LPAREN, CvwTokenTypes.CS_RPAREN, false),
            BracePair(CvwTokenTypes.CS_LBRACKET, CvwTokenTypes.CS_RBRACKET, false),
        )
    }

    override fun getPairs(): Array<BracePair> = PAIRS
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true
    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
