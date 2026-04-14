package com.github.mateuszpodeszwa.consolemvc.cvwsupport.navigation

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwIcons
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwTokenTypes
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.elementType

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
        val file = element.containingFile ?: return
        val virtualFile = file.virtualFile ?: return
        val project = element.project

        if (virtualFile.extension != "cvw") return

        // File-level: link .cvw to its controller
        if (element == file.firstChild) {
            addControllerMarker(element, virtualFile.path, project, result)
        }

        // NavigationResult.To("Controller", "Action") — link to target controller
        if (element.elementType == CvwTokenTypes.CS_STRING) {
            addNavigationResultTargetMarker(element, project, result)
        }
    }

    /**
     * Adds gutter navigation for NavigationResult.To("Controller", "Action") calls.
     * Links the controller name string literal to the corresponding controller file.
     */
    private fun addNavigationResultTargetMarker(
        element: PsiElement,
        project: Project,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        val text = element.text
        // Must be a quoted string like "Home" or "Settings"
        if (!text.startsWith("\"") || !text.endsWith("\"") || text.length < 3) return
        val stringContent = text.substring(1, text.length - 1)
        if (stringContent.isEmpty() || stringContent.contains(' ') || stringContent.contains('.')) return

        // Check if this string is the first argument of NavigationResult.To(
        // Walk backwards: expect LPAREN, then "To" or "ToAction" identifier, then DOT, then "NavigationResult"
        val prevTokens = collectPreviousTokens(element, 6)
        val isNavigationTo = isFirstArgOfNavigationResultTo(prevTokens)

        if (!isNavigationTo) return

        // The first string arg of NavigationResult.To() is the controller name
        val controllerFileName = "${stringContent}Controller.cs"
        val controllerFiles = FilenameIndex.getVirtualFilesByName(
            controllerFileName,
            GlobalSearchScope.projectScope(project)
        )

        if (controllerFiles.isNotEmpty()) {
            val targets = controllerFiles.mapNotNull { vf ->
                PsiManager.getInstance(project).findFile(vf)
            }
            if (targets.isNotEmpty()) {
                val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
                    .setTargets(targets)
                    .setTooltipText("Navigate to ${stringContent}Controller")
                result.add(builder.createLineMarkerInfo(element))
            }
        }
    }

    /**
     * Collect the previous N non-whitespace tokens from an element.
     */
    private fun collectPreviousTokens(element: PsiElement, count: Int): List<PsiElement> {
        val tokens = mutableListOf<PsiElement>()
        var current = element.prevSibling
        while (current != null && tokens.size < count) {
            val type = current.elementType
            if (type != CvwTokenTypes.WHITE_SPACE && type != CvwTokenTypes.NEWLINE) {
                tokens.add(current)
            }
            current = current.prevSibling
        }
        return tokens
    }

    /**
     * Checks if the collected previous tokens match the pattern:
     * NavigationResult . To (
     * i.e., tokens[0]='(', tokens[1]='To', tokens[2]='.', tokens[3]='NavigationResult'
     */
    private fun isFirstArgOfNavigationResultTo(tokens: List<PsiElement>): Boolean {
        if (tokens.size < 4) return false
        return tokens[0].elementType == CvwTokenTypes.CS_LPAREN
            && tokens[1].text == "To"
            && tokens[2].elementType == CvwTokenTypes.CS_DOT
            && tokens[3].text == "NavigationResult"
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
