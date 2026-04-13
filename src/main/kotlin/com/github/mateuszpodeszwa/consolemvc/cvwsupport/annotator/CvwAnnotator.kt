package com.github.mateuszpodeszwa.consolemvc.cvwsupport.annotator

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.psi.CvwFile
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Annotator for .cvw files that provides error and warning highlights.
 *
 * Phase 1 checks:
 * - Missing @model directive (file will be silently skipped by the source generator)
 * - Empty code body (no code after directives)
 * - Missing return statement (code must return NavigationResult)
 */
class CvwAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Only annotate at the file level to avoid repeated processing
        if (element !is CvwFile) return

        val text = element.text
        val lines = text.split("\n")

        var hasModelDirective = false
        var codeStartIndex = -1
        var modelDirectiveLine = -1

        // Parse the directive section
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("@model ")) {
                hasModelDirective = true
                modelDirectiveLine = i
                val modelType = trimmed.substring("@model ".length).trim()
                if (modelType.isEmpty()) {
                    // @model with no type argument
                    val lineOffset = getLineOffset(lines, i)
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        "Missing type argument for @model directive"
                    ).range(TextRange(lineOffset, lineOffset + trimmed.length)).create()
                }
                continue
            }
            if (trimmed.startsWith("@using ")) {
                val ns = trimmed.substring("@using ".length).trim()
                if (ns.isEmpty()) {
                    val lineOffset = getLineOffset(lines, i)
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        "Missing namespace argument for @using directive"
                    ).range(TextRange(lineOffset, lineOffset + trimmed.length)).create()
                }
                continue
            }

            // First non-directive, non-blank line = code body start
            codeStartIndex = i
            break
        }

        // Check for missing @model directive
        if (!hasModelDirective) {
            // Annotate the first line / beginning of file
            val endOffset = if (lines.isNotEmpty()) minOf(lines[0].length, text.length) else 0
            holder.newAnnotation(
                HighlightSeverity.WARNING,
                "Missing @model directive. This file will be skipped by the ConsoleMVC source generator."
            ).range(TextRange(0, maxOf(endOffset, 1))).create()
        }

        // Check for missing return statement in code body
        if (hasModelDirective && codeStartIndex >= 0) {
            val codeBody = lines.drop(codeStartIndex).joinToString("\n")
            if (!codeBody.contains("return ") && !codeBody.contains("return\n")) {
                val codeOffset = getLineOffset(lines, codeStartIndex)
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "Code body should return a NavigationResult. Missing 'return' statement."
                ).range(TextRange(codeOffset, minOf(codeOffset + 1, text.length))).create()
            }
        }

        // Check for empty code body
        if (hasModelDirective && codeStartIndex < 0) {
            // All lines are directives/blank — no code body
            val offset = if (modelDirectiveLine >= 0) getLineOffset(lines, modelDirectiveLine) else 0
            val endOffset = if (modelDirectiveLine >= 0) offset + lines[modelDirectiveLine].length else 1
            holder.newAnnotation(
                HighlightSeverity.WARNING,
                "Empty code body. The generated Render() method will have no implementation."
            ).range(TextRange(offset, endOffset)).create()
        }

        // Check for invalid directives (lines starting with @ but not @model or @using)
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("@") && !trimmed.startsWith("@model ") && !trimmed.startsWith("@using ")) {
                // Only flag in the directive section (before code body)
                if (codeStartIndex < 0 || i < codeStartIndex) {
                    if (trimmed == "@model" || trimmed == "@using") {
                        // Missing space — these are incomplete directives
                        val lineOffset = getLineOffset(lines, i)
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            "Incomplete directive. Expected '@model <type>' or '@using <namespace>'."
                        ).range(TextRange(lineOffset, lineOffset + trimmed.length)).create()
                    }
                }
            }
        }
    }

    private fun getLineOffset(lines: List<String>, lineIndex: Int): Int {
        var offset = 0
        for (i in 0 until lineIndex) {
            offset += lines[i].length + 1 // +1 for \n
        }
        return offset
    }
}
