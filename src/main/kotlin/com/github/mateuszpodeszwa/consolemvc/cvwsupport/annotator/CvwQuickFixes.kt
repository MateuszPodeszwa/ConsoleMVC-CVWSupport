package com.github.mateuszpodeszwa.consolemvc.cvwsupport.annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Quick-fix that adds `return NavigationResult.Quit();` at the end of a view.
 * Offered when a page view has neither a top-level `<menu>` nor a return.
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
