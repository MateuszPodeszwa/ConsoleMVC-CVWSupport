package com.github.mateuszpodeszwa.consolemvc.cvwsupport.preview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPageTest {
    @Test
    fun `the page blocks scripts and keeps only the tool's screen`() {
        val page = PreviewPage.screen("<pre class=\"cvw-screen\">x<script>alert(1)</script></pre><script>y</script>", emptyList(), dark = true)
        assertTrue(page.contains("content=\"default-src &#39;none&#39;; style-src &#39;unsafe-inline&#39;;\""))
        assertFalse(page.contains("<script"))
        assertTrue(page.contains("x&lt;script>alert(1)&lt;/script>"))
    }

    @Test
    fun `problems and messages are escaped`() {
        val page = PreviewPage.screen("<pre class=\"cvw-screen\"></pre>",
            listOf(PreviewProblem("error", 2, 3, "CVW105", "Unknown element <blink>"), PreviewProblem("x\" onclick=\"y", null, null, null, "note")), dark = false)
        assertTrue(page.contains("<li class=\"error\">Line 2, column 3: CVW105 Unknown element &lt;blink&gt;</li>"))
        assertTrue(page.contains("<li class=\"note\">note</li>"))
        val message = PreviewPage.message("Not <installed>", listOf("Run `dotnet tool install -g ConsoleMVC.Preview` & retry"), dark = true)
        assertTrue(message.contains("<h2>Not &lt;installed&gt;</h2>"))
        assertTrue(message.contains("<code>dotnet tool install -g ConsoleMVC.Preview</code> &amp; retry"))
    }
}
