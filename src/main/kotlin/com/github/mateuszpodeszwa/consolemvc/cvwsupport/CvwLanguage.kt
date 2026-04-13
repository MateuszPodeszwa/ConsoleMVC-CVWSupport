package com.github.mateuszpodeszwa.consolemvc.cvwsupport

import com.intellij.lang.Language

class CvwLanguage private constructor() : Language("CVW") {
    companion object {
        @JvmStatic
        val INSTANCE = CvwLanguage()
    }

    override fun getDisplayName(): String = "CVW (Console View)"
    override fun isCaseSensitive(): Boolean = true
}
