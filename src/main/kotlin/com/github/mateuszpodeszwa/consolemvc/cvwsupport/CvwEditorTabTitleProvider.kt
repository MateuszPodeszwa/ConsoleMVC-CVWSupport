package com.github.mateuszpodeszwa.consolemvc.cvwsupport

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Shows "{Controller}/{Action}View" in editor tabs for .cvw files
 * instead of just the filename, providing better context.
 *
 * For example: "Home/IndexView" instead of "IndexView.cvw"
 */
class CvwEditorTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        if (file.extension != "cvw") return null

        val path = file.path.replace('\\', '/')
        val parts = path.split('/')
        val viewsIndex = parts.indexOfLast { it.equals("Views", ignoreCase = true) }

        if (viewsIndex >= 0 && viewsIndex + 1 < parts.size - 1) {
            val controllerName = parts[viewsIndex + 1]
            val fileName = file.nameWithoutExtension
            return "$controllerName/$fileName"
        }

        return null // Fall back to default tab title
    }
}
