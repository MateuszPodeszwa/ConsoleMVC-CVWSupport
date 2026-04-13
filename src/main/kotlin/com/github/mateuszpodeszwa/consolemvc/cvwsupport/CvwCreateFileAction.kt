package com.github.mateuszpodeszwa.consolemvc.cvwsupport

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

class CvwCreateFileAction :
    CreateFileFromTemplateAction("ConsoleMVC View", "Create a new ConsoleMVC .cvw view file", CvwIcons.FILE) {

    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder
            .setTitle("New ConsoleMVC View")
            .addKind("Console View", CvwIcons.FILE, "ConsoleMVC View.cvw")
    }

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String {
        return "Create ConsoleMVC View: $newName"
    }
}
