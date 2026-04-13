package com.github.mateuszpodeszwa.consolemvc.cvwsupport.completion

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwLanguage
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwTokenTypes
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Provides code completion for .cvw files.
 *
 * Phase 1 (frontend-only) completions:
 * - Directive keywords (@model, @using) at the start of lines in the directive section
 * - NavigationResult static methods in the code body
 * - Common Console methods in the code body
 * - ViewData access in the code body
 */
class CvwCompletionContributor : CompletionContributor() {
    init {
        // Completion in the directive argument position (after @model / @using)
        // This is limited in Phase 1 — full type completion requires the ReSharper backend

        // Completion in the code body for common framework types
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(CvwTokenTypes.CS_IDENTIFIER)
                .withLanguage(CvwLanguage.INSTANCE),
            CvwCodeBodyCompletionProvider()
        )

        // Completion at the start of directives
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .withLanguage(CvwLanguage.INSTANCE),
            CvwDirectiveCompletionProvider()
        )
    }
}

/**
 * Provides completions for NavigationResult, Console, ViewData, and Model
 * in the C# code body of .cvw files.
 */
private class CvwCodeBodyCompletionProvider : CompletionProvider<CompletionParameters>() {
    companion object {
        private val NAVIGATION_RESULT_METHODS = listOf(
            LookupElementBuilder.create("NavigationResult.To")
                .withPresentableText("NavigationResult.To(controller, action)")
                .withInsertHandler { ctx, _ ->
                    ctx.document.insertString(ctx.tailOffset, "(\"${'$'}END${'$'}\", \"\")")
                    ctx.editor.caretModel.moveToOffset(ctx.tailOffset - 5)
                }
                .withTailText("(string controller, string action)")
                .withTypeText("NavigationResult")
                .bold(),
            LookupElementBuilder.create("NavigationResult.ToAction")
                .withPresentableText("NavigationResult.ToAction(action)")
                .withInsertHandler { ctx, _ ->
                    ctx.document.insertString(ctx.tailOffset, "(\"\")")
                    ctx.editor.caretModel.moveToOffset(ctx.tailOffset - 2)
                }
                .withTailText("(string action)")
                .withTypeText("NavigationResult")
                .bold(),
            LookupElementBuilder.create("NavigationResult.Quit")
                .withPresentableText("NavigationResult.Quit()")
                .withInsertHandler { ctx, _ ->
                    ctx.document.insertString(ctx.tailOffset, "()")
                    ctx.editor.caretModel.moveToOffset(ctx.tailOffset)
                }
                .withTailText("()")
                .withTypeText("NavigationResult")
                .bold(),
        )

        private val CONSOLE_METHODS = listOf(
            createMethodLookup("Console.WriteLine", "(\"\")", "void", -2),
            createMethodLookup("Console.Write", "(\"\")", "void", -2),
            createMethodLookup("Console.ReadLine", "()", "string?", 0),
            createMethodLookup("Console.ReadKey", "()", "ConsoleKeyInfo", 0),
            createMethodLookup("Console.Clear", "()", "void", 0),
        )

        private val VIEW_DATA_LOOKUPS = listOf(
            LookupElementBuilder.create("ViewData")
                .withTypeText("ViewDataDictionary")
                .bold(),
        )

        private val MODEL_LOOKUP = LookupElementBuilder.create("Model")
            .withTypeText("TModel")
            .bold()

        private fun createMethodLookup(
            name: String,
            suffix: String,
            returnType: String,
            caretOffset: Int
        ): LookupElementBuilder {
            return LookupElementBuilder.create(name)
                .withInsertHandler { ctx, _ ->
                    ctx.document.insertString(ctx.tailOffset, suffix)
                    ctx.editor.caretModel.moveToOffset(ctx.tailOffset + caretOffset)
                }
                .withTailText(suffix)
                .withTypeText(returnType)
        }
    }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val prefix = result.prefixMatcher.prefix

        // NavigationResult completions
        if ("NavigationResult".startsWith(prefix, ignoreCase = true) || prefix.startsWith("Nav", ignoreCase = true)) {
            result.addAllElements(NAVIGATION_RESULT_METHODS)
        }

        // Console completions
        if ("Console".startsWith(prefix, ignoreCase = true) || prefix.startsWith("Con", ignoreCase = true)) {
            result.addAllElements(CONSOLE_METHODS)
        }

        // ViewData
        if ("ViewData".startsWith(prefix, ignoreCase = true)) {
            result.addAllElements(VIEW_DATA_LOOKUPS)
        }

        // Model
        if ("Model".startsWith(prefix, ignoreCase = true)) {
            result.addElement(MODEL_LOOKUP)
        }

        // return keyword (important since every .cvw must return)
        if ("return".startsWith(prefix, ignoreCase = true)) {
            result.addElement(
                LookupElementBuilder.create("return")
                    .bold()
                    .withTailText(" NavigationResult...")
            )
        }
    }
}

/**
 * Provides @model and @using directive completions at the start of lines.
 */
private class CvwDirectiveCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position
        val document = parameters.editor.document
        val lineNumber = document.getLineNumber(position.textOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val textBeforeCaret = document.getText(com.intellij.openapi.util.TextRange(lineStart, position.textOffset)).trim()

        // Only suggest directives if we're at the start of a line and before the code body
        if (textBeforeCaret.isEmpty() || textBeforeCaret == "@" || textBeforeCaret.startsWith("@m") || textBeforeCaret.startsWith("@u")) {
            // Check if we're still in the directive section (no code lines above)
            val isInDirectiveSection = isBeforeCodeBody(document, lineNumber)
            if (isInDirectiveSection) {
                result.addElement(
                    LookupElementBuilder.create("@model ")
                        .withPresentableText("@model")
                        .withTailText(" <fully-qualified-type>")
                        .bold()
                )
                result.addElement(
                    LookupElementBuilder.create("@using ")
                        .withPresentableText("@using")
                        .withTailText(" <namespace>")
                        .bold()
                )
            }
        }
    }

    private fun isBeforeCodeBody(document: com.intellij.openapi.editor.Document, currentLine: Int): Boolean {
        for (i in 0 until currentLine) {
            val lineStart = document.getLineStartOffset(i)
            val lineEnd = document.getLineEndOffset(i)
            val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd)).trim()
            if (lineText.isNotEmpty() && !lineText.startsWith("@model ") && !lineText.startsWith("@using ")) {
                return false // Code body has started
            }
        }
        return true
    }
}
