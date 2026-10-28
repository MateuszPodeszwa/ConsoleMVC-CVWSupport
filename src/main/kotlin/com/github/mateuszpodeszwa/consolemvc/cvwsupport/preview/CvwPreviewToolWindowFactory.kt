package com.github.mateuszpodeszwa.consolemvc.cvwsupport.preview

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwFileType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

/**
 * Creates the ConsoleMVC Preview tool window (on the right), which shows the selected view drawn by `cvw-preview`.
 *
 * Example: registered in plugin.xml as the toolWindow "ConsoleMVC Preview".
 */
class CvwPreviewToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CvwPreviewPanel(project)
        Disposer.register(toolWindow.disposable, panel)
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(panel.component, "", false))
    }

    companion object {
        /** The tool window's id, as in plugin.xml. */
        const val ID = "ConsoleMVC Preview"
    }
}

/**
 * Opens the ConsoleMVC Preview tool window for the view being edited (Ctrl+Shift+Alt+V, or the editor's context menu).
 *
 * Example: registered in plugin.xml as the action "ConsoleMVC.ShowPreview".
 */
class CvwShowPreviewAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ToolWindowManager.getInstance(project).getToolWindow(CvwPreviewToolWindowFactory.ID)?.activate(null)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null &&
            event.getData(CommonDataKeys.VIRTUAL_FILE)?.fileType == CvwFileType.INSTANCE
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
