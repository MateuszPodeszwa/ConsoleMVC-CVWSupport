package com.github.mateuszpodeszwa.consolemvc.cvwsupport.structure

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

/**
 * Structure view for .cvw files showing:
 * - The @model type
 * - @using imports
 * - NavigationResult return targets (heuristic)
 */
class CvwStructureViewElement(private val element: PsiFile) :
    StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = element
    override fun getAlphaSortKey(): String = element.name

    override fun getPresentation(): ItemPresentation {
        return PresentationData(element.name, null, CvwIcons.FILE, null)
    }

    override fun getChildren(): Array<TreeElement> {
        val text = element.text
        val lines = text.split("\n")
        val children = mutableListOf<TreeElement>()

        var codeStarted = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() && !codeStarted) continue

            if (trimmed.startsWith("@model ")) {
                val modelType = trimmed.substring("@model ".length).trim()
                children.add(DirectiveTreeElement("@model", modelType, "Model type"))
            } else if (trimmed.startsWith("@using ")) {
                val ns = trimmed.substring("@using ".length).trim()
                children.add(DirectiveTreeElement("@using", ns, "Import"))
            } else if (!codeStarted && trimmed.isNotEmpty()) {
                codeStarted = true
            }

            // Look for NavigationResult calls in code body
            if (codeStarted) {
                extractNavigationTargets(trimmed, children)
            }
        }

        return children.toTypedArray()
    }

    private fun extractNavigationTargets(line: String, children: MutableList<TreeElement>) {
        // Match NavigationResult.To("Controller", "Action")
        val toPattern = Regex("""NavigationResult\.To\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)""")
        for (match in toPattern.findAll(line)) {
            val controller = match.groupValues[1]
            val action = match.groupValues[2]
            children.add(DirectiveTreeElement("-> Navigate", "$controller/$action", "Navigation target"))
        }

        // Match NavigationResult.ToAction("Action")
        val toActionPattern = Regex("""NavigationResult\.ToAction\(\s*"([^"]+)"\s*\)""")
        for (match in toActionPattern.findAll(line)) {
            val action = match.groupValues[1]
            children.add(DirectiveTreeElement("-> Action", action, "Navigation target"))
        }

        // Match NavigationResult.Quit()
        if (line.contains("NavigationResult.Quit()")) {
            children.add(DirectiveTreeElement("-> Quit", "Exit application", "Navigation target"))
        }
    }

    override fun navigate(requestFocus: Boolean) {
        (element as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true
}

/**
 * A leaf element in the structure view representing a directive or navigation target.
 */
class DirectiveTreeElement(
    private val label: String,
    private val value: String,
    private val description: String
) : StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = "$label $value"
    override fun getAlphaSortKey(): String = "$label $value"

    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String = "$label $value"
            override fun getLocationString(): String = description
            override fun getIcon(unused: Boolean): Icon = CvwIcons.FILE
        }
    }

    override fun getChildren(): Array<TreeElement> = emptyArray()
    override fun navigate(requestFocus: Boolean) {}
    override fun canNavigate(): Boolean = false
    override fun canNavigateToSource(): Boolean = false
}
