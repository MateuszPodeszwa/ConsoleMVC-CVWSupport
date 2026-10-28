package com.github.mateuszpodeszwa.consolemvc.cvwsupport.ide

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.highlighting.CvwSyntaxHighlighter
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.lexer.CvwLexer
import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.TestViews
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.testFramework.LexerTestCase
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.UsefulTestCase

/** The platform's own lexer checks on the template's views: restarting mid-text, and covering every character. */
class CvwHighlightingMachineryTest : LexerTestCase() {
    override fun createLexer() = CvwLexer()

    override fun getDirPath() = "src/test/testData/markup"

    fun `test the platform's restart check passes on the template's views`() {
        for (name in TestViews.templateViews) {
            checkCorrectRestart(TestViews.read(name))
        }
    }

    fun `test the platform's zero-length and coverage checks pass`() {
        for (name in TestViews.templateViews) {
            checkZeroState(TestViews.read(name), com.intellij.psi.tree.TokenSet.EMPTY)
        }
    }
}

/**
 * The editor's incremental highlighter (the IDE's LexerEditorHighlighter, which after each edit re-lexes only from the
 * last place the lexer says it can restart) must give the same tokens as lexing the whole text afresh, edit after edit.
 */
class CvwIncrementalHighlightingTest : UsefulTestCase() {
    override fun setUp() {
        super.setUp()
        TestApplicationManager.getInstance()
    }

    /** Lexes the whole text afresh: each token's type and range. */
    private fun freshTokens(text: String): List<Triple<Int, Int, String>> {
        val lexer = CvwLexer()
        lexer.start(text)
        val tokens = mutableListOf<Triple<Int, Int, String>>()
        while (lexer.tokenType != null) {
            tokens += Triple(lexer.tokenStart, lexer.tokenEnd, lexer.tokenType.toString())
            lexer.advance()
        }
        return tokens
    }

    fun `test the editor's incremental highlighter matches a fresh lex after each edit`() {
        val text = TestViews.read("FeaturesView.cvw")
        val document = EditorFactory.getInstance().createDocument(text)
        val highlighter = LexerEditorHighlighter(CvwSyntaxHighlighter(), EditorColorsManager.getInstance().globalScheme)
        // The editor the highlighter repaints; nothing is drawn in a test
        highlighter.setEditor(object : HighlighterClient {
            override fun getProject(): Project? = null
            override fun repaint(start: Int, end: Int) {}
            override fun getDocument(): Document = document
        })
        highlighter.setText(document.immutableCharSequence)
        document.addDocumentListener(highlighter)

        // Typing that changes what is markup: opening and closing tags, quotes, @ and comments, and undoing them
        val edits = listOf("<box>", "\"", "@", "@*", "*@", "</box>", "<text>Hi @Model.Name</text>", "}", "{")
        var offset = text.indexOf("<table")
        val inserted = mutableListOf<Pair<Int, String>>()
        for (insert in edits) {
            WriteCommandAction.runWriteCommandAction(null) { document.insertString(offset, insert) }
            inserted += offset to insert
            assertEquals("after inserting $insert", freshTokens(document.text), highlighterTokens(highlighter, document.textLength))
            offset += insert.length / 2
        }
        for ((at, insert) in inserted.reversed()) {
            WriteCommandAction.runWriteCommandAction(null) { document.deleteString(at, at + insert.length) }
            assertEquals("after deleting $insert", freshTokens(document.text), highlighterTokens(highlighter, document.textLength))
        }
        assertEquals(text, document.text)
    }

    private fun highlighterTokens(highlighter: LexerEditorHighlighter, length: Int): List<Triple<Int, Int, String>> {
        val tokens = mutableListOf<Triple<Int, Int, String>>()
        val iterator = highlighter.createIterator(0)
        while (!iterator.atEnd() && iterator.start < length) {
            tokens += Triple(iterator.start, iterator.end, iterator.tokenType.toString())
            iterator.advance()
        }
        return tokens
    }
}
