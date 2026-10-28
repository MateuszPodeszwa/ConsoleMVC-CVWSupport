package com.github.mateuszpodeszwa.consolemvc.cvwsupport.preview

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.ProblemRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PreviewRunnerTest {
    private val windows = System.getProperty("os.name").startsWith("Windows")

    /** A cvw-preview stand-in: a shell script that echoes its input as the screen and lists one problem. */
    private fun fakeTool(body: String): String {
        val file = Files.createTempFile("cvw-preview", ".sh").toFile()
        file.writeText("#!/bin/sh\n$body\n")
        file.setExecutable(true)
        return file.absolutePath
    }

    @Test
    fun `the arguments name the file, read standard input and ask for HTML`() {
        assertEquals(listOf("/a b/IndexView.cvw", "--stdin", "--format", "html", "--width", "100"),
            PreviewRunner.arguments("/a b/IndexView.cvw", 100, false))
        assertEquals(listOf("--stdin", "--format", "html", "--width", "20", "--high-contrast"), PreviewRunner.arguments(null, 5, true))
    }

    @Test
    fun `a drawn preview has its screen and problems`() {
        assumeFalse(windows)
        val tool = fakeTool("""
            cat > /dev/null
            printf '<pre class="cvw-screen">Hi &amp; bye</pre>\n<ul class="cvw-problems">\n'
            printf '<li class="cvw-error" data-line="2" data-column="3">Line 2, column 3: CVW105 Unknown element &lt;blink&gt;</li>\n'
            printf '<li class="cvw-note">Partial &#39;Header&#39; not found</li>\n</ul>\n'
        """.trimIndent())
        val result = PreviewRunner(tool).run("x.cvw", "<text>Hi</text>", 80, false) as PreviewResult.Drawn
        assertEquals(PreviewProblem("error", 2, 3, "CVW105", "Unknown element <blink>"), result.problems[0])
        assertEquals(PreviewProblem("note", null, null, null, "Partial 'Header' not found"), result.problems[1])
    }

    @Test
    fun `missing, failing and unexpected tools are reported`() {
        assertEquals(PreviewResult.NotInstalled, PreviewRunner("/nowhere/cvw-preview-missing").run(null, "", 80, false))
        assumeFalse(windows)
        val failing = PreviewRunner(fakeTool("cat > /dev/null; echo 'cannot read' >&2; exit 2")).run(null, "", 80, false)
        assertTrue((failing as PreviewResult.Failed).error.contains("cannot read"))
        val garbage = PreviewRunner(fakeTool("cat > /dev/null; echo Usage")).run(null, "", 80, false)
        assertTrue((garbage as PreviewResult.Failed).error.contains("something other than a preview"))
    }

    @Test
    fun `only the tool's screen and spans are kept`() {
        assertEquals("<pre class=\"cvw-screen\"><span style=\"color:#ff0000\">red</span>&lt;img src=x></pre>",
            PreviewRunner.onlyScreen("<p>x</p><pre class=\"cvw-screen\"><span style=\"color:#ff0000\">red</span><img src=x></pre><script>"))
        assertEquals("<pre class=\"cvw-screen\"></pre>", PreviewRunner.onlyScreen("nothing"))
    }

    @Test
    fun `entities are decoded`() {
        assertEquals("<a> & \"b\" 'c' A", PreviewRunner.decode("&lt;a&gt; &amp; &quot;b&quot; &#39;c&#39; &#x41;"))
    }

    @Test
    fun `the default command is also looked for in the dotnet tools folder`() {
        assertEquals(listOf("cvw-preview", "/home/me/.dotnet/tools/cvw-preview"), ToolLocator.candidates(null, false, "/home/me"))
        assertEquals(listOf("cvw-preview", "C:\\Users\\me\\.dotnet\\tools\\cvw-preview.exe"), ToolLocator.candidates("", true, "C:\\Users\\me"))
        assertEquals(listOf("/opt/x"), ToolLocator.candidates(" /opt/x "))
        assertTrue(ToolLocator.looksMissing("/nowhere/cvw-preview"))
        assertTrue(!ToolLocator.looksMissing("cvw-preview") && !ToolLocator.looksMissing(File(".").absolutePath))
    }

    @Test
    fun `a problem's range covers the tag at its position`() {
        val text = "<box>\n  <blink />"
        val range = ProblemRange.fromLineColumn(text, 2, 3)
        assertEquals("<blink", text.substring(range.start, range.end))
        assertEquals(5, ProblemRange.offsetOf("ab\ncd", 9, 1))
    }
}
