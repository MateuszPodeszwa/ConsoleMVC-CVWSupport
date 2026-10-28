package com.github.mateuszpodeszwa.consolemvc.cvwsupport.annotator

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupSchema
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupValidator
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.ProblemRange
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.preview.PreviewResult
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.preview.PreviewToolService
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.psi.CvwFile
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.settings.CvwSettings
import com.intellij.ide.impl.isTrusted
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile

/**
 * Checks views while they are typed, off the UI thread: with `cvw-preview` (the generator's own diagnostics, CVWxxx)
 * when it is installed and the project is trusted, otherwise with [MarkupValidator] against the markup schema.
 *
 * Example: `<box><blink /></box>` underlines `<blink` with "CVW105 Unknown element <blink>".
 */
class CvwMarkupAnnotator : ExternalAnnotator<CvwMarkupAnnotator.Input, List<CvwMarkupAnnotator.Problem>>() {
    /** What is checked: the view's text, its path on disk (or null) and whether the tool may run. */
    data class Input(val text: String, val path: String?, val toolAllowed: Boolean)

    /** A problem to show over [start] to [end]. */
    data class Problem(val start: Int, val end: Int, val error: Boolean, val message: String)

    private val validator = MarkupValidator(MarkupSchema.bundled)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Input? = collectInformation(file)

    override fun collectInformation(file: PsiFile): Input? {
        if (file !is CvwFile || !CvwSettings.instance.state.diagnosticsEnabled) return null
        val virtualFile = file.virtualFile
        val onDisk = virtualFile != null && virtualFile.fileSystem is LocalFileSystem
        return Input(file.text, if (onDisk) virtualFile.path else null, file.project.isTrusted())
    }

    override fun doAnnotate(input: Input): List<Problem> {
        if (input.toolAllowed) {
            val result = PreviewToolService.instance.draw(input.path, input.text)
            if (result is PreviewResult.Drawn) {
                return result.problems.filter { it.line != null }.map {
                    val range = ProblemRange.fromLineColumn(input.text, it.line!!, it.column ?: 1)
                    Problem(range.start, range.end, it.severity == "error", listOfNotNull(it.code, it.message).joinToString(" "))
                }
            }
        }
        return validator.validate(input.text).map {
            Problem(it.start, it.end, it.error, listOfNotNull(it.code, it.message).joinToString(" "))
        }
    }

    override fun apply(file: PsiFile, problems: List<Problem>, holder: AnnotationHolder) {
        val length = file.textLength
        for (problem in problems) {
            val start = problem.start.coerceIn(0, length)
            val end = problem.end.coerceIn(start, length)
            holder.newAnnotation(if (problem.error) HighlightSeverity.ERROR else HighlightSeverity.WARNING, problem.message)
                .range(TextRange(start, maxOf(end, minOf(start + 1, length))))
                .create()
        }
    }
}
