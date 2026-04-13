package com.github.mateuszpodeszwa.consolemvc.cvwsupport

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

@Suppress("deprecation")
class CvwLiveTemplateContext : TemplateContextType("CVW", "CVW (Console View)") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val file = templateActionContext.file
        return file.name.endsWith(".cvw")
    }
}
