package com.github.mateuszpodeszwa.consolemvc.cvwsupport.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Provides code folding for .cvw files:
 * - Directive preamble (@model + @using block) folds to "@model Type..."
 * - Curly brace blocks { ... } fold to "{ ... }"
 * - Multi-line comments fold to "/* ... */"
 */
class CvwFoldingBuilder : FoldingBuilderEx() {

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        val text = document.text
        val lines = text.split("\n")

        // Fold the directive preamble if it spans multiple lines
        buildDirectiveFold(lines, text, root, descriptors)

        // Fold curly brace blocks in the code body
        buildBraceFolds(text, root, descriptors)

        // Fold multi-line comments
        buildCommentFolds(text, root, descriptors)

        return descriptors.toTypedArray()
    }

    private fun buildDirectiveFold(
        lines: List<String>,
        text: String,
        root: PsiElement,
        descriptors: MutableList<FoldingDescriptor>
    ) {
        var firstDirectiveLine = -1
        var lastDirectiveLine = -1

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("@model ") || trimmed.startsWith("@using ")) {
                if (firstDirectiveLine < 0) firstDirectiveLine = i
                lastDirectiveLine = i
            } else {
                break
            }
        }

        // Only fold if there are at least 2 directive lines
        if (firstDirectiveLine >= 0 && lastDirectiveLine > firstDirectiveLine) {
            val startOffset = lineOffset(lines, firstDirectiveLine)
            val endOffset = lineOffset(lines, lastDirectiveLine) + lines[lastDirectiveLine].length
            if (endOffset > startOffset && endOffset <= text.length) {
                descriptors.add(
                    FoldingDescriptor(
                        root.node,
                        TextRange(startOffset, endOffset),
                        FoldingGroup.newGroup("cvw-directives")
                    )
                )
            }
        }
    }

    private fun buildBraceFolds(
        text: String,
        root: PsiElement,
        descriptors: MutableList<FoldingDescriptor>
    ) {
        val stack = ArrayDeque<Int>()

        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '{' -> {
                    // Skip braces inside strings/comments (simple heuristic)
                    if (!isInsideStringOrComment(text, i)) {
                        stack.addLast(i)
                    }
                }
                '}' -> {
                    if (!isInsideStringOrComment(text, i) && stack.isNotEmpty()) {
                        val openBrace = stack.removeLast()
                        // Only fold multi-line blocks
                        if (text.substring(openBrace, i + 1).contains('\n')) {
                            descriptors.add(
                                FoldingDescriptor(
                                    root.node,
                                    TextRange(openBrace, i + 1)
                                )
                            )
                        }
                    }
                }
            }
            i++
        }
    }

    private fun buildCommentFolds(
        text: String,
        root: PsiElement,
        descriptors: MutableList<FoldingDescriptor>
    ) {
        var i = 0
        while (i < text.length - 1) {
            if (text[i] == '/' && text[i + 1] == '*') {
                val end = text.indexOf("*/", i + 2)
                if (end > i && text.substring(i, end + 2).contains('\n')) {
                    descriptors.add(
                        FoldingDescriptor(
                            root.node,
                            TextRange(i, end + 2)
                        )
                    )
                }
                i = if (end > 0) end + 2 else i + 1
            } else {
                i++
            }
        }
    }

    override fun getPlaceholderText(node: ASTNode): String {
        val text = node.text
        return when {
            text.trimStart().startsWith("@model") -> {
                val firstLine = text.lines().first().trim()
                "$firstLine ..."
            }
            text.startsWith("{") -> "{ ... }"
            text.startsWith("/*") -> "/* ... */"
            else -> "..."
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false

    private fun lineOffset(lines: List<String>, lineIndex: Int): Int {
        var offset = 0
        for (i in 0 until lineIndex) {
            offset += lines[i].length + 1 // +1 for \n
        }
        return offset
    }

    private fun isInsideStringOrComment(text: String, pos: Int): Boolean {
        // Simple backward scan heuristic — not 100% accurate but good enough for folding
        var inString = false
        var inVerbatim = false
        var inLineComment = false
        var inBlockComment = false
        var i = 0
        while (i < pos) {
            if (inLineComment) {
                if (text[i] == '\n') inLineComment = false
                i++
                continue
            }
            if (inBlockComment) {
                if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '/') {
                    inBlockComment = false
                    i += 2
                    continue
                }
                i++
                continue
            }
            if (inVerbatim) {
                if (text[i] == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        i += 2; continue // escaped quote
                    }
                    inVerbatim = false
                }
                i++
                continue
            }
            if (inString) {
                if (text[i] == '\\') { i += 2; continue }
                if (text[i] == '"') inString = false
                if (text[i] == '\n') inString = false // unterminated
                i++
                continue
            }
            // Not in any special context
            if (i + 1 < text.length && text[i] == '/' && text[i + 1] == '/') {
                inLineComment = true; i += 2; continue
            }
            if (i + 1 < text.length && text[i] == '/' && text[i + 1] == '*') {
                inBlockComment = true; i += 2; continue
            }
            if (text[i] == '@' && i + 1 < text.length && text[i + 1] == '"') {
                inVerbatim = true; i += 2; continue
            }
            if (text[i] == '"') { inString = true; i++; continue }
            i++
        }
        return inString || inVerbatim || inLineComment || inBlockComment
    }
}
