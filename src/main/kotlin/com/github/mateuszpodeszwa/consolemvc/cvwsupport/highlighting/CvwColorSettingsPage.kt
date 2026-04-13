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
            AttributesDescriptor("Directive keyword (@model, @using)", CvwSyntaxHighlighter.DIRECTIVE_KEYWORD),
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
@model ConsoleMVC.App.Models.HomeViewModel
@using System.Globalization

// Display the main menu
Console.WriteLine(${'$'}"=== {Model.Title} ===");
Console.WriteLine();
Console.WriteLine(Model.Message);

/* Multi-line
   block comment */
for (var i = 0; i < Model.MenuOptions.Count; i++)
    Console.WriteLine(${'$'}"  [{i + 1}] {Model.MenuOptions[i]}");

Console.Write("Select: ");
var input = Console.ReadLine()?.Trim();

return input switch
{
    "1" => NavigationResult.To("Home", "About"),
    "2" => NavigationResult.Quit(),
    _ => NavigationResult.To("Home", "Index")
};
""".trimStart()
