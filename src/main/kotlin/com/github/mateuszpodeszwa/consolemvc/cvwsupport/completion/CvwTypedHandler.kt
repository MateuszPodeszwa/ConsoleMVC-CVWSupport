package com.github.mateuszpodeszwa.consolemvc.cvwsupport.completion

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupParser
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupSchema
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.psi.CvwFile
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Opens completion by itself in views where markup is typed: after `<` and `</`, after `"` and a space inside a start
 * tag (values and attributes), and after `@`.
 *
 * Example: typing `<` inside `<table>` shows `row` without pressing Ctrl+Space.
 */
class CvwTypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is CvwFile) return Result.CONTINUE
        val popup = when (charTyped) {
            '<', '@' -> true
            '/' -> editor.caretModel.offset > 0 && editor.document.charsSequence[editor.caretModel.offset - 1] == '<'
            '"', ' ' -> insideStartTag(editor)
            else -> false
        }
        if (!popup) return Result.CONTINUE
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
    }

    private fun insideStartTag(editor: Editor): Boolean {
        val text = editor.document.text
        val offset = editor.caretModel.offset
        return MarkupParser.parse(text, MarkupSchema.bundled).startTagAt(offset) != null
    }
}
