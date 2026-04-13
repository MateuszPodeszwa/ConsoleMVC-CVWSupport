package com.github.mateuszpodeszwa.consolemvc.cvwsupport.psi

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwFileType
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwLanguage
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class CvwFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, CvwLanguage.INSTANCE) {
    override fun getFileType(): FileType = CvwFileType.INSTANCE
    override fun toString(): String = "CVW File"
}
