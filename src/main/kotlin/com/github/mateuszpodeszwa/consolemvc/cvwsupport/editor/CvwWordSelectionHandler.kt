package com.github.mateuszpodeszwa.consolemvc.cvwsupport.editor

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwTokenTypes
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.psi.CvwElementTypes
import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType

/**
 * Provides smarter extend-selection (Ctrl+W) behavior for .cvw files.
 *
 * Selection expansion order for directives:
 *   argument text → full directive line → all directives
 *
 * For the code body:
 *   word → expression → statement → block → entire code body
 */
class CvwWordSelectionHandler : ExtendWordSelectionHandlerBase() {

    override fun canSelect(e: PsiElement): Boolean {
        val tokenType = e.elementType
        return tokenType == CvwTokenTypes.DIRECTIVE_ARGUMENT
            || tokenType == CvwTokenTypes.MODEL_KEYWORD
            || tokenType == CvwTokenTypes.USING_KEYWORD
            || tokenType == CvwElementTypes.MODEL_DIRECTIVE
            || tokenType == CvwElementTypes.USING_DIRECTIVE
            || tokenType == CvwElementTypes.CODE_BODY
    }

    override fun select(
        e: PsiElement,
        editorText: CharSequence,
        cursorOffset: Int,
        editor: Editor
    ): MutableList<TextRange> {
        val ranges = mutableListOf<TextRange>()
        val tokenType = e.elementType

        when (tokenType) {
            // For directive arguments: select the argument, then the full directive
            CvwTokenTypes.DIRECTIVE_ARGUMENT -> {
                ranges.add(e.textRange)
                val parent = e.parent
                if (parent != null) {
                    ranges.add(parent.textRange)
                }
            }

            // For directive keywords: select the keyword, then the full directive
            CvwTokenTypes.MODEL_KEYWORD, CvwTokenTypes.USING_KEYWORD -> {
                ranges.add(e.textRange)
                val parent = e.parent
                if (parent != null) {
                    ranges.add(parent.textRange)
                }
            }

            // For directive nodes: select the directive, then all directives combined
            CvwElementTypes.MODEL_DIRECTIVE, CvwElementTypes.USING_DIRECTIVE -> {
                ranges.add(e.textRange)
                // Offer selecting all directives together
                val allDirectiveRange = computeAllDirectivesRange(e)
                if (allDirectiveRange != null) {
                    ranges.add(allDirectiveRange)
                }
            }

            // For code body: select the entire code body
            CvwElementTypes.CODE_BODY -> {
                ranges.add(e.textRange)
            }
        }

        return ranges
    }

    private fun computeAllDirectivesRange(element: PsiElement): TextRange? {
        val parent = element.parent ?: return null
        var start = Int.MAX_VALUE
        var end = Int.MIN_VALUE
        var count = 0

        var child = parent.firstChild
        while (child != null) {
            val childType = child.elementType
            if (childType == CvwElementTypes.MODEL_DIRECTIVE || childType == CvwElementTypes.USING_DIRECTIVE) {
                start = minOf(start, child.textRange.startOffset)
                end = maxOf(end, child.textRange.endOffset)
                count++
            }
            child = child.nextSibling
        }

        // Only return a combined range if there are multiple directives
        return if (count > 1) TextRange(start, end) else null
    }
}
