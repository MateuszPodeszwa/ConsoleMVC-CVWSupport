package com.github.mateuszpodeszwa.consolemvc.cvwsupport.preview

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwFileType
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.Alarm
import javax.swing.JComponent
import javax.swing.JEditorPane

/**
 * The ConsoleMVC Preview tool window's content: the view in the selected editor, drawn by `cvw-preview` and updated
 * a moment after typing stops, including unsaved changes. It shows a message when no view is selected, the tool is
 * missing, or the project is not trusted (the tool never runs then).
 *
 * The page is shown in the IDE's built-in browser (JCEF) with scripts blocked by its Content Security Policy, or in a
 * plain HTML pane where JCEF is not available.
 *
 * Example: created by [CvwPreviewToolWindowFactory]; `panel.refresh()` draws the selected view again.
 */
class CvwPreviewPanel(private val project: Project) : Disposable {
    private companion object {
        /** How long to wait after typing stops, in milliseconds. */
        const val DELAY_MS = 300
    }

    private val browser: JBCefBrowser? = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private val fallback: JEditorPane? = if (browser == null) JEditorPane("text/html", "").apply { isEditable = false } else null
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private var lastDrawn: PreviewResult.Drawn? = null

    /** The Swing component to put in the tool window. */
    val component: JComponent = browser?.component ?: JBScrollPane(fallback)

    init {
        browser?.let { Disposer.register(this, it) }
        project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) = refresh()
        })
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document)
                if (file != null && file == selectedView()) refresh()
            }
        }, this)
        refresh()
    }

    /** Draws the selected view again, after a short wait; earlier pending draws are dropped. */
    fun refresh() {
        val view = selectedView()
        if (view == null) {
            show(PreviewPage.message("No view selected", listOf("Open a .cvw view to see its preview."), dark()))
            return
        }
        if (!project.isTrusted()) {
            show(PreviewPage.message("The preview is off in untrusted projects", listOf("Trust this project to draw previews with cvw-preview."), dark()))
            return
        }
        val text = FileDocumentManager.getInstance().getDocument(view)?.text ?: return
        val path = if (view.fileSystem is LocalFileSystem) view.path else null

        alarm.cancelAllRequests()
        alarm.addRequest({
            val result = PreviewToolService.instance.draw(path, text)
            ApplicationManager.getApplication().invokeLater({ showResult(result) }, ModalityState.any()) { project.isDisposed }
        }, DELAY_MS)
    }

    private fun showResult(result: PreviewResult) {
        val dark = dark()
        val page = when (result) {
            is PreviewResult.Drawn -> {
                lastDrawn = result
                PreviewPage.screen(result.html, result.problems, dark)
            }
            PreviewResult.NotInstalled -> PreviewPage.message("The preview needs the cvw-preview tool", listOf(
                "Install it with `dotnet tool install -g ConsoleMVC.Preview`, then reopen the view.",
                "If it is installed somewhere else, set its path under Settings | Tools | ConsoleMVC."), dark)
            is PreviewResult.Failed -> lastDrawn?.let {
                PreviewPage.screen(it.html, listOf(PreviewProblem("error", null, null, null, result.error)), dark, stale = true)
            } ?: PreviewPage.message("The preview could not be drawn", listOf(result.error), dark)
        }
        show(page)
    }

    private fun show(page: String) {
        browser?.loadHTML(page)
        fallback?.text = page
    }

    private fun selectedView(): VirtualFile? =
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull { it.fileType == CvwFileType.INSTANCE }

    private fun dark(): Boolean = !JBColor.isBright()

    override fun dispose() {
    }
}
