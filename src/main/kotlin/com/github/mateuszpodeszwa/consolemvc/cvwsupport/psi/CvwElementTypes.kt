package com.github.mateuszpodeszwa.consolemvc.cvwsupport.psi

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwLanguage
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType

object CvwElementTypes {
    @JvmField val FILE = IFileElementType(CvwLanguage.INSTANCE)
    @JvmField val MODEL_DIRECTIVE = CvwElementType("MODEL_DIRECTIVE")
    @JvmField val USING_DIRECTIVE = CvwElementType("USING_DIRECTIVE")
    @JvmField val CODE_BODY = CvwElementType("CODE_BODY")
}

class CvwElementType(debugName: String) : IElementType(debugName, CvwLanguage.INSTANCE) {
    override fun toString(): String = "CvwElementType.${super.toString()}"
}
