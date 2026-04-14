package com.github.mateuszpodeszwa.consolemvc.cvwsupport.annotator

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.psi.CvwFile
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Cross-file annotator that detects model type mismatches between .cvw files
 * and their corresponding controller actions.
 *
 * Checks if a controller action's View() call passes a model whose type
 * does not match the @model directive in the .cvw file.
 *
 * This is a heuristic frontend-only approach using text/regex analysis
 * of the controller C# source. Full semantic analysis requires the Phase 2 backend.
 */
class CvwModelMismatchAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is CvwFile) return

        val virtualFile = element.virtualFile ?: return
        if (virtualFile.extension != "cvw") return

        val text = element.text
        val project = element.project

        // Extract @model type from the .cvw file
        val modelType = extractModelType(text) ?: return
        val modelShortName = modelType.substringAfterLast('.')

        // Extract controller name and action name from file path
        val normalizedPath = virtualFile.path.replace('\\', '/')
        val parts = normalizedPath.split('/')
        val viewsIndex = parts.indexOfLast { it.equals("Views", ignoreCase = true) }
        if (viewsIndex < 0 || viewsIndex + 1 >= parts.size - 1) return

        val controllerName = parts[viewsIndex + 1]
        val fileName = virtualFile.nameWithoutExtension // e.g., "IndexView"
        val actionName = if (fileName.endsWith("View")) {
            fileName.removeSuffix("View")
        } else {
            return // Non-standard naming, skip
        }

        // Find the controller file
        val controllerFileName = "${controllerName}Controller.cs"
        val controllerFiles = FilenameIndex.getVirtualFilesByName(
            controllerFileName,
            GlobalSearchScope.projectScope(project)
        )

        if (controllerFiles.isEmpty()) return

        // Read and analyze the controller file
        for (controllerVf in controllerFiles) {
            val controllerPsi = PsiManager.getInstance(project).findFile(controllerVf) ?: continue
            val controllerText = controllerPsi.text

            val mismatch = detectModelMismatch(controllerText, actionName, modelType, modelShortName)
            if (mismatch != null) {
                // Find the @model directive range in the .cvw file for the annotation
                val modelDirectiveRange = findModelDirectiveRange(text)
                if (modelDirectiveRange != null) {
                    holder.newAnnotation(
                        HighlightSeverity.WARNING,
                        "Model type mismatch: @model declares '$modelShortName' but " +
                            "${controllerName}Controller.${actionName}() passes '${mismatch.actualType}'"
                    ).range(modelDirectiveRange).create()
                }
                break
            }
        }
    }

    private fun extractModelType(text: String): String? {
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("@model ")) {
                return trimmed.substring("@model ".length).trim().takeIf { it.isNotEmpty() }
            }
            if (trimmed.isNotEmpty() && !trimmed.startsWith("@") && !trimmed.startsWith("//")) {
                break // Code body started
            }
        }
        return null
    }

    private fun findModelDirectiveRange(text: String): TextRange? {
        var offset = 0
        for (line in text.split("\n")) {
            val trimmed = line.trim()
            if (trimmed.startsWith("@model ")) {
                val argStart = offset + line.indexOf("@model ") + "@model ".length
                val argEnd = offset + line.trimEnd().length
                return if (argEnd > argStart) TextRange(argStart, argEnd) else null
            }
            offset += line.length + 1
        }
        return null
    }

    /**
     * Analyzes a controller C# source to detect if the given action method
     * passes a model type that doesn't match the expected type.
     *
     * Returns a [MismatchInfo] if a mismatch is detected, null otherwise.
     */
    private fun detectModelMismatch(
        controllerText: String,
        actionName: String,
        expectedModelType: String,
        expectedShortName: String
    ): MismatchInfo? {
        // Find the action method: public ActionResult ActionName()
        val actionPattern = Regex(
            """(?:public\s+(?:override\s+)?(?:ActionResult|ViewResult|IActionResult)\s+${Regex.escape(actionName)}\s*\()"""
        )
        val actionMatch = actionPattern.find(controllerText) ?: return null
        val actionStart = actionMatch.range.first

        // Find the method body — look for the opening brace after the method signature
        val braceStart = controllerText.indexOf('{', actionStart)
        if (braceStart < 0) return null

        // Extract the method body (find matching closing brace)
        val methodBody = extractMethodBody(controllerText, braceStart) ?: return null

        // Look for View() calls in the method body
        val viewCallPattern = Regex("""(?:return\s+)?View\s*\(\s*(\w+)\s*\)""")
        val viewCalls = viewCallPattern.findAll(methodBody)

        for (viewCall in viewCalls) {
            val argName = viewCall.groupValues[1]

            // Check if argName is "null" or empty — skip
            if (argName == "null") continue

            // Look for the type of this variable in the method body
            // Pattern 1: var argName = new TypeName(...)
            val varNewPattern = Regex(
                """(?:var|${Regex.escape(expectedShortName)}|\w+)\s+${Regex.escape(argName)}\s*=\s*new\s+(\w+)"""
            )
            val varNewMatch = varNewPattern.find(methodBody)
            if (varNewMatch != null) {
                val actualType = varNewMatch.groupValues[1]
                if (!typesMatch(expectedModelType, expectedShortName, actualType)) {
                    return MismatchInfo(actualType)
                }
                // Types match — no mismatch
                return null
            }

            // Pattern 2: TypeName argName = ...
            val typedVarPattern = Regex("""(\w+(?:\.\w+)*)\s+${Regex.escape(argName)}\s*=""")
            val typedVarMatch = typedVarPattern.find(methodBody)
            if (typedVarMatch != null) {
                val actualType = typedVarMatch.groupValues[1]
                if (actualType != "var" && !typesMatch(expectedModelType, expectedShortName, actualType)) {
                    return MismatchInfo(actualType.substringAfterLast('.'))
                }
                return null
            }
        }

        // Also check for inline new: return View(new TypeName(...))
        val inlineNewPattern = Regex("""View\s*\(\s*new\s+(\w+(?:\.\w+)*)""")
        val inlineMatches = inlineNewPattern.findAll(methodBody)
        for (inlineMatch in inlineMatches) {
            val actualType = inlineMatch.groupValues[1]
            if (!typesMatch(expectedModelType, expectedShortName, actualType)) {
                return MismatchInfo(actualType.substringAfterLast('.'))
            }
            // Found a match
            return null
        }

        return null
    }

    /**
     * Extracts a method body from the opening brace to its matching closing brace.
     */
    private fun extractMethodBody(text: String, braceStart: Int): String? {
        var depth = 0
        for (i in braceStart until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(braceStart + 1, i)
                    }
                }
            }
        }
        return null
    }

    /**
     * Checks if two type names refer to the same type.
     * Compares both fully-qualified and short names.
     */
    private fun typesMatch(expectedFull: String, expectedShort: String, actual: String): Boolean {
        val actualShort = actual.substringAfterLast('.')
        // Match if short names are equal, or if the actual is a suffix of expected full name
        return actualShort.equals(expectedShort, ignoreCase = false) ||
            expectedFull.equals(actual, ignoreCase = false) ||
            expectedFull.endsWith(".$actual")
    }

    private data class MismatchInfo(val actualType: String)
}
