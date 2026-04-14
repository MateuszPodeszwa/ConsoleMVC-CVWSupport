package com.github.mateuszpodeszwa.consolemvc.cvwsupport.documentation

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwTokenTypes
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType

class CvwDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = originalElement ?: element ?: return null
        val tokenType = target.elementType

        return when (tokenType) {
            CvwTokenTypes.MODEL_KEYWORD -> buildDoc(
                "@model",
                "Directive",
                "Declares the view model type for this .cvw view file. " +
                    "The type becomes the generic argument of <code>ConsoleView&lt;T&gt;</code> " +
                    "and the type of the <code>Model</code> parameter in <code>Render()</code>.",
                "@model Fully.Qualified.TypeName"
            )

            CvwTokenTypes.USING_KEYWORD -> buildDoc(
                "@using",
                "Directive",
                "Adds a <code>using</code> statement to the generated view class. " +
                    "Multiple <code>@using</code> directives are allowed. " +
                    "The <code>ConsoleMVC.Mvc</code> namespace is always imported automatically.",
                "@using Some.Namespace"
            )

            CvwTokenTypes.CS_IDENTIFIER -> generateIdentifierDoc(target)

            CvwTokenTypes.DIRECTIVE_ARGUMENT -> {
                val prevSibling = findPreviousNonWhitespace(target)
                when (prevSibling?.elementType) {
                    CvwTokenTypes.MODEL_KEYWORD -> buildDoc(
                        target.text,
                        "Model Type",
                        "The view model type. The code body receives an instance as the " +
                            "<code>Model</code> parameter. This type must be a valid, " +
                            "fully-qualified C# type accessible to the project.",
                        null
                    )
                    CvwTokenTypes.USING_KEYWORD -> buildDoc(
                        target.text,
                        "Imported Namespace",
                        "This namespace will be available in the code body via a " +
                            "<code>using</code> directive in the generated C# class.",
                        null
                    )
                    else -> null
                }
            }

            else -> null
        }
    }

    private fun generateIdentifierDoc(element: PsiElement): String? {
        val text = element.text
        return when (text) {
            "Model" -> buildDoc(
                "Model",
                "Parameter",
                "The view model instance passed from the controller's <code>View(model)</code> call. " +
                    "Its type is declared by the <code>@model</code> directive at the top of this file.",
                null
            )

            "ViewData" -> buildDoc(
                "ViewData",
                "Property: ViewDataDictionary",
                "A <code>Dictionary&lt;string, object?&gt;</code> inherited from " +
                    "<code>ConsoleView</code>. Use it to pass additional data from the controller " +
                    "that isn't part of the model.",
                "ViewData[\"key\"]"
            )

            "NavigationResult" -> buildDoc(
                "NavigationResult",
                "Class: ConsoleMVC.Mvc",
                "Represents the navigation outcome of a view's <code>Render()</code> method. " +
                    "Every .cvw code body must return a <code>NavigationResult</code>.",
                "NavigationResult.To(\"Controller\", \"Action\")\n" +
                    "NavigationResult.ToAction(\"Action\")\n" +
                    "NavigationResult.Quit()"
            )

            "To" -> {
                if (isPrecededByNavigationResult(element)) buildDoc(
                    "NavigationResult.To(string controller, string action)",
                    "Static Method",
                    "Navigates to the specified controller and action after this view finishes rendering.",
                    "return NavigationResult.To(\"Home\", \"Index\");"
                ) else null
            }

            "ToAction" -> {
                if (isPrecededByNavigationResult(element)) buildDoc(
                    "NavigationResult.ToAction(string action)",
                    "Static Method",
                    "Navigates to a different action on the current controller.",
                    "return NavigationResult.ToAction(\"Details\");"
                ) else null
            }

            "Quit" -> {
                if (isPrecededByNavigationResult(element)) buildDoc(
                    "NavigationResult.Quit()",
                    "Static Method",
                    "Signals the ConsoleMVC application to exit the event loop.",
                    "return NavigationResult.Quit();"
                ) else null
            }

            else -> null
        }
    }

    private fun isPrecededByNavigationResult(element: PsiElement): Boolean {
        // Walk backwards: expect DOT, then "NavigationResult" identifier
        var prev = element.prevSibling
        while (prev != null && prev.elementType == CvwTokenTypes.WHITE_SPACE) prev = prev.prevSibling
        if (prev?.elementType != CvwTokenTypes.CS_DOT) return false
        prev = prev?.prevSibling
        while (prev != null && prev.elementType == CvwTokenTypes.WHITE_SPACE) prev = prev.prevSibling
        return prev?.text == "NavigationResult"
    }

    private fun findPreviousNonWhitespace(element: PsiElement): PsiElement? {
        var prev = element.prevSibling
        while (prev != null && (prev.elementType == CvwTokenTypes.WHITE_SPACE || prev.elementType == CvwTokenTypes.NEWLINE)) {
            prev = prev.prevSibling
        }
        return prev
    }

    private fun buildDoc(name: String, type: String, description: String, example: String?): String {
        val sb = StringBuilder()
        sb.append(DocumentationMarkup.DEFINITION_START)
        sb.append("<b>").append(name).append("</b>")
        sb.append(" &mdash; <i>").append(type).append("</i>")
        sb.append(DocumentationMarkup.DEFINITION_END)

        sb.append(DocumentationMarkup.CONTENT_START)
        sb.append(description)
        sb.append(DocumentationMarkup.CONTENT_END)

        if (example != null) {
            sb.append(DocumentationMarkup.SECTIONS_START)
            sb.append(DocumentationMarkup.SECTION_HEADER_START)
            sb.append("Example:")
            sb.append(DocumentationMarkup.SECTION_SEPARATOR)
            sb.append("<pre><code>").append(example).append("</code></pre>")
            sb.append(DocumentationMarkup.SECTION_END)
            sb.append(DocumentationMarkup.SECTIONS_END)
        }

        return sb.toString()
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = originalElement ?: element ?: return null
        return when (target.elementType) {
            CvwTokenTypes.MODEL_KEYWORD -> "@model — CVW directive (declares view model type)"
            CvwTokenTypes.USING_KEYWORD -> "@using — CVW directive (imports namespace)"
            else -> null
        }
    }
}
