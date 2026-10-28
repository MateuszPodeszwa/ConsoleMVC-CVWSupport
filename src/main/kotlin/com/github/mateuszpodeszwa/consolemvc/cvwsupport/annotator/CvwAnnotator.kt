package com.github.mateuszpodeszwa.consolemvc.cvwsupport.annotator

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupParser
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupSchema
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.psi.CvwFile
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * File-level checks of a view that do not need the markup schema:
 * - a directive without its argument, such as `@model` or `@layout` alone;
 * - a page view (not in Views/Shared) that neither has a top-level `<menu>` nor returns where to go next, which the
 *   build rejects ("not all code paths return a value"), with a quick fix that adds `return NavigationResult.Quit();`.
 *
 * Markup itself is checked by [CvwMarkupAnnotator]. A view without `@model` is valid: its model is `object`.
 *
 * Example: a view with only `<text>Hello</text>` gets a warning on its first line.
 */
class CvwAnnotator : Annotator {
    private companion object {
        val DIRECTIVE = Regex("(?m)^[ \\t]*(@(model|using|inject|layout|refresh))[ \\t]*$")
        val RETURN = Regex("\\breturn\\b")
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Only annotate at the file level to avoid repeated processing
        if (element !is CvwFile) return
        val text = element.text

        for (match in DIRECTIVE.findAll(text)) {
            val keyword = match.groups[1]!!
            holder.newAnnotation(HighlightSeverity.ERROR, "${keyword.value} needs an argument, such as ${example(match.groupValues[2])}.")
                .range(TextRange(keyword.range.first, keyword.range.last + 1))
                .create()
        }

        val path = element.virtualFile?.path?.replace('\\', '/') ?: ""
        val isShared = path.contains("/Views/Shared/")
        if (!isShared && text.isNotBlank()) {
            val document = MarkupParser.parse(text, MarkupSchema.bundled)
            val hasMenu = document.roots.any { it.name == "menu" }
            if (!hasMenu && !RETURN.containsMatchIn(text)) {
                holder.newAnnotation(HighlightSeverity.WARNING,
                    "A view without a <menu> must say where to go next, such as return NavigationResult.Quit();")
                    .range(TextRange(0, minOf(text.length, text.indexOf('\n').let { if (it < 0) text.length else it }.coerceAtLeast(1))))
                    .withFix(AddReturnStatementQuickFix())
                    .create()
            }
        }
    }

    private fun example(directive: String): String = when (directive) {
        "model" -> "@model Shop.Models.HomeViewModel"
        "using" -> "@using System.Globalization"
        "inject" -> "@inject IClock Clock"
        "layout" -> "@layout _Layout or @layout none"
        else -> "@refresh 5s"
    }
}
