package com.github.mateuszpodeszwa.consolemvc.cvwsupport.navigation

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwIcons
import com.intellij.navigation.GotoRelatedItem
import com.intellij.navigation.GotoRelatedProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Provides "Go to Related" navigation between .cvw views and controllers.
 *
 * From a .cvw file (Views/{Controller}/{Action}View.cvw):
 *   -> navigates to Controllers/{Controller}Controller.cs
 *
 * From a *Controller.cs file:
 *   -> navigates to all .cvw files in Views/{Controller}/
 */
class CvwGotoRelatedProvider : GotoRelatedProvider() {
    override fun getItems(psiElement: PsiElement): List<GotoRelatedItem> {
        val file = psiElement.containingFile ?: return emptyList()
        val virtualFile = file.virtualFile ?: return emptyList()
        val project = psiElement.project

        return when {
            virtualFile.extension == "cvw" -> findRelatedController(virtualFile.path, project)
            virtualFile.name.endsWith("Controller.cs") -> findRelatedViews(virtualFile.name, project)
            else -> emptyList()
        }
    }

    private fun findRelatedController(cvwPath: String, project: Project): List<GotoRelatedItem> {
        val normalizedPath = cvwPath.replace('\\', '/')
        val parts = normalizedPath.split('/')
        val viewsIndex = parts.indexOfLast { it.equals("Views", ignoreCase = true) }
        if (viewsIndex < 0 || viewsIndex + 1 >= parts.size - 1) return emptyList()

        val controllerName = parts[viewsIndex + 1]
        val controllerFileName = "${controllerName}Controller.cs"

        val controllerFiles = FilenameIndex.getVirtualFilesByName(
            controllerFileName,
            GlobalSearchScope.projectScope(project)
        )

        return controllerFiles.mapNotNull { vf ->
            PsiManager.getInstance(project).findFile(vf)
        }.map { psiFile ->
            GotoRelatedItem(psiFile, "Controller")
        }
    }

    private fun findRelatedViews(controllerFileName: String, project: Project): List<GotoRelatedItem> {
        // Extract controller name: HomeController.cs -> Home
        val controllerName = controllerFileName
            .removeSuffix("Controller.cs")

        // Find all .cvw files that might be in Views/{controllerName}/
        val scope = GlobalSearchScope.projectScope(project)
        val cvwFiles = FilenameIndex.getAllFilesByExt(project, "cvw", scope)

        return cvwFiles.filter { vf ->
            val path = vf.path.replace('\\', '/')
            val parts = path.split('/')
            val viewsIndex = parts.indexOfLast { it.equals("Views", ignoreCase = true) }
            viewsIndex >= 0 && viewsIndex + 1 < parts.size - 1 &&
                parts[viewsIndex + 1].equals(controllerName, ignoreCase = true)
        }.mapNotNull { vf ->
            PsiManager.getInstance(project).findFile(vf)
        }.map { psiFile ->
            GotoRelatedItem(psiFile, "View")
        }
    }
}
