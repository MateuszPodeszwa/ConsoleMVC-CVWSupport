package com.github.mateuszpodeszwa.consolemvc.cvwsupport.highlighting

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.CvwIcons
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class CvwColorSettingsPage : ColorSettingsPage {

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Markup//Tag", CvwSyntaxHighlighter.TAG),
            AttributesDescriptor("Markup//Tag name", CvwSyntaxHighlighter.TAG_NAME),
            AttributesDescriptor("Markup//Unknown tag name", CvwSyntaxHighlighter.UNKNOWN_TAG_NAME),
            AttributesDescriptor("Markup//Attribute name", CvwSyntaxHighlighter.ATTRIBUTE_NAME),
            AttributesDescriptor("Markup//Attribute value", CvwSyntaxHighlighter.ATTRIBUTE_VALUE),
            AttributesDescriptor("Markup//Text", CvwSyntaxHighlighter.MARKUP_TEXT),
            AttributesDescriptor("Markup//@ before C#", CvwSyntaxHighlighter.TRANSITION),
            AttributesDescriptor("Markup//@@ escape", CvwSyntaxHighlighter.ESCAPE),
            AttributesDescriptor("Markup//Entity (&lt;)", CvwSyntaxHighlighter.ENTITY),
            AttributesDescriptor("Markup//Comment (@* *@)", CvwSyntaxHighlighter.MARKUP_COMMENT),
            AttributesDescriptor("Directive keyword (@model, @using, @inject, @layout, @refresh)", CvwSyntaxHighlighter.DIRECTIVE_KEYWORD),
            AttributesDescriptor("Directive argument (type/namespace)", CvwSyntaxHighlighter.DIRECTIVE_ARGUMENT),
            AttributesDescriptor("C# keyword", CvwSyntaxHighlighter.CS_KEYWORD),
            AttributesDescriptor("C# string literal", CvwSyntaxHighlighter.CS_STRING),
            AttributesDescriptor("C# number literal", CvwSyntaxHighlighter.CS_NUMBER),
            AttributesDescriptor("C# line comment", CvwSyntaxHighlighter.CS_LINE_COMMENT),
            AttributesDescriptor("C# block comment", CvwSyntaxHighlighter.CS_BLOCK_COMMENT),
            AttributesDescriptor("C# identifier", CvwSyntaxHighlighter.CS_IDENTIFIER),
            AttributesDescriptor("C# operator", CvwSyntaxHighlighter.CS_OPERATOR),
            AttributesDescriptor("C# semicolon/comma", CvwSyntaxHighlighter.CS_PUNCTUATION),
            AttributesDescriptor("C# braces { }", CvwSyntaxHighlighter.CS_BRACES),
            AttributesDescriptor("C# parentheses ( )", CvwSyntaxHighlighter.CS_PARENTHESES),
            AttributesDescriptor("C# brackets [ ]", CvwSyntaxHighlighter.CS_BRACKETS),
            AttributesDescriptor("C# dot", CvwSyntaxHighlighter.CS_DOT),
            AttributesDescriptor("Bad character", CvwSyntaxHighlighter.BAD_CHARACTER),
        )
    }

    override fun getIcon(): Icon = CvwIcons.FILE
    override fun getHighlighter(): SyntaxHighlighter = CvwSyntaxHighlighter()
    override fun getDemoText(): String = DEMO_TEXT
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDisplayName(): String = "CVW (Console View)"
}

private val DEMO_TEXT = """
@model Shop.Models.OrdersViewModel
@inject IStringLocalizer<SharedResource> L
@* A comment: removed from the view *@

<box title="@L["Orders"]" border="rounded" padding="1">
    <text bold>@Model.Count orders &amp; @@mentions</text>
</box>

<table border="single">
    <row header><cell>Customer</cell><cell align="right">Total</cell></row>
    @foreach (var order in Model.Orders)
    {
        <row><cell>@order.Customer</cell><cell>@order.Total.ToString("C")</cell></row>
    }
</table>

<menu>
    <option key="1" nav="Orders/Details?id=@Model.First">First order</option>
    <option key="q" quit>Exit</option>
</menu>
""".trimStart()
