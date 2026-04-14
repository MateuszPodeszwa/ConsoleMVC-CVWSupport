package com.github.mateuszpodeszwa.consolemvc.cvwsupport.annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Quick-fix that inserts an @model directive at the top of a .cvw file.
 * Offered when the annotator detects a missing @model directive.
 */
class AddModelDirectiveQuickFix : IntentionAction, PriorityAction {
    override fun getText(): String = "Add @model directive"
    override fun getFamilyName(): String = "ConsoleMVC CVW"
    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return file?.name?.endsWith(".cvw") == true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val document = editor.document

        // Insert @model directive at the very beginning of the file
        val template = "@model "
        document.insertString(0, template + "\n\n")

        // Position cursor right after "@model " for the user to type the type name
        editor.caretModel.moveToOffset(template.length)
    }

    override fun startInWriteAction(): Boolean = true
}

/**
 * Quick-fix that adds a return statement at the end of the code body.
 * Offered when the annotator detects a missing return statement.
 */
class AddReturnStatementQuickFix : IntentionAction, PriorityAction {
    override fun getText(): String = "Add return NavigationResult.Quit()"
    override fun getFamilyName(): String = "ConsoleMVC CVW"
    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return file?.name?.endsWith(".cvw") == true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val document = editor.document
        val text = document.text

        // Ensure the file ends with a newline before adding the return
        val suffix = if (text.endsWith("\n")) "" else "\n"
        val returnStatement = "${suffix}return NavigationResult.Quit();\n"
        document.insertString(document.textLength, returnStatement)
    }

    override fun startInWriteAction(): Boolean = true
}
