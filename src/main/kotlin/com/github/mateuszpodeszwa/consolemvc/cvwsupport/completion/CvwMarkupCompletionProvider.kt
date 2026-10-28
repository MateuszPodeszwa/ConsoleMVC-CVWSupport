package com.github.mateuszpodeszwa.consolemvc.cvwsupport.completion

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwIcons
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.CompletionKind
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupCompletion
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupSchema
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupSuggestion
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.util.ProcessingContext

/**
 * Completes C-View Markup with [MarkupCompletion]: the elements allowed where the caret is (with their required
 * attributes and closing tag), attributes not written yet, an attribute's values, the closing tag of the innermost open
 * element, directives and `@` constructs. Choosing an item writes its whole text and puts the caret where the user
 * types next, such as between the quotes of a required attribute.
 *
 * Example: typing `<` inside `<table>` offers only `row`; choosing it writes `<row>`, an indented line and `</row>`.
 */
class CvwMarkupCompletionProvider : CompletionProvider<CompletionParameters>() {
    private val completion = MarkupCompletion(MarkupSchema.bundled)

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        // The original text: the completion copy has a dummy identifier at the caret
        val text = parameters.originalFile.viewProvider.document?.text ?: parameters.originalFile.text
        val offset = parameters.offset.coerceIn(0, text.length)
        val suggestions = completion.complete(text, offset)
        if (suggestions.isEmpty()) return

        // Every suggestion here replaces from the same start, so one prefix serves them all
        val prefix = text.substring(suggestions[0].replaceStart, offset)
        val matching = result.withPrefixMatcher(prefix)
        for (suggestion in suggestions) {
            matching.addElement(PrioritizedLookupElement.withPriority(lookup(suggestion, offset), suggestion.priority.toDouble()))
        }
    }

    private fun lookup(suggestion: MarkupSuggestion, offset: Int): LookupElement {
        val icon = when (suggestion.kind) {
            CompletionKind.ELEMENT, CompletionKind.CLOSE_TAG -> CvwIcons.FILE
            CompletionKind.ATTRIBUTE -> AllIcons.Nodes.Property
            CompletionKind.VALUE -> AllIcons.Nodes.Enum
            CompletionKind.DIRECTIVE -> AllIcons.Nodes.Annotationtype
            CompletionKind.CONSTRUCT -> AllIcons.Nodes.Template
        }
        val lookupString = when (suggestion.kind) {
            CompletionKind.CLOSE_TAG -> suggestion.text
            CompletionKind.DIRECTIVE -> suggestion.label.removePrefix("@")
            CompletionKind.CONSTRUCT -> suggestion.label.removePrefix("@").substringBefore(' ')
            else -> suggestion.label
        }
        return LookupElementBuilder.create(suggestion, lookupString)
            .withPresentableText(suggestion.label)
            .withTypeText(suggestion.detail.take(60), true)
            .withIcon(icon)
            .withBoldness(suggestion.kind == CompletionKind.ELEMENT || suggestion.kind == CompletionKind.CLOSE_TAG)
            .withInsertHandler { context, _ -> insert(context, suggestion, offset) }
    }

    /** Writes the suggestion's whole text over what was typed (and the rest of the word after the caret). */
    private fun insert(context: InsertionContext, suggestion: MarkupSuggestion, originalOffset: Int) {
        val document = context.document
        val start = context.startOffset
        val after = (suggestion.replaceEnd - originalOffset).coerceAtLeast(0)
        val end = (context.tailOffset + after).coerceAtMost(document.textLength)
        document.replaceString(start, end, suggestion.text)
        context.editor.caretModel.moveToOffset(start + suggestion.caretOffset)
        context.commitDocument()
    }
}
