package com.github.mateuszpodeszwa.consolemvc.cvwsupport.navigation

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwIcons
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File

/**
 * Adds gutter icons on .cvw files linking to related controller files,
 * and on potential controller action methods linking to their view files.
 *
 * This is a frontend-only heuristic approach:
 * - For .cvw files: looks for a corresponding controller file based on the
 *   directory structure (Views/{Controller}/ -> Controllers/{Controller}Controller.cs)
 * - Navigation between views and controllers by file naming convention
 *
 * Full semantic navigation (resolving actual method calls) requires the Phase 2 backend.
 */
class CvwViewLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        // Only process file-level elements to avoid excessive work
        val file = element.containingFile ?: return
        if (element != file.firstChild) return

        val virtualFile = file.virtualFile ?: return
        val project = element.project

        if (virtualFile.extension == "cvw") {
            // This is a .cvw file — try to find the related controller
            addControllerMarker(element, virtualFile.path, project, result)
        }
    }

    private fun addControllerMarker(
        element: PsiElement,
        filePath: String,
        project: Project,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        // Extract controller name from path: Views/{ControllerName}/{Action}View.cvw
        val normalizedPath = filePath.replace('\\', '/')
        val parts = normalizedPath.split('/')
        val viewsIndex = parts.indexOfLast { it.equals("Views", ignoreCase = true) }
        if (viewsIndex < 0 || viewsIndex + 1 >= parts.size - 1) return

        val controllerName = parts[viewsIndex + 1]
        val controllerFileName = "${controllerName}Controller.cs"

        // Search for the controller file in the project
        val controllerFiles = FilenameIndex.getVirtualFilesByName(
            controllerFileName,
            GlobalSearchScope.projectScope(project)
        )

        if (controllerFiles.isNotEmpty()) {
            val targets = controllerFiles.mapNotNull { vf ->
                PsiManager.getInstance(project).findFile(vf)
            }

            if (targets.isNotEmpty()) {
                val builder = NavigationGutterIconBuilder.create(CvwIcons.FILE)
                    .setTargets(targets)
                    .setTooltipText("Navigate to ${controllerName}Controller")
                result.add(builder.createLineMarkerInfo(element))
            }
        }
    }
}
