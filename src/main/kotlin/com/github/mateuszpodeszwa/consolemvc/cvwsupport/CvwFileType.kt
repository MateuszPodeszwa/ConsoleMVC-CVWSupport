package com.github.mateuszpodeszwa.consolemvc.cvwsupport

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class CvwFileType private constructor() : LanguageFileType(CvwLanguage.INSTANCE) {
    companion object {
        @JvmField
        val INSTANCE = CvwFileType()
    }

    override fun getName(): String = "CVW"
    override fun getDescription(): String = "ConsoleMVC Console View file"
    override fun getDefaultExtension(): String = "cvw"
    override fun getIcon(): Icon = CvwIcons.FILE
}
