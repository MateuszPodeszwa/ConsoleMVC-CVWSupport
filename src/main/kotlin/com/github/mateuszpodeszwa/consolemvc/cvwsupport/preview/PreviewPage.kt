package com.github.mateuszpodeszwa.consolemvc.cvwsupport.preview

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.markup.MarkupDocumentation.escape

/**
 * Builds the HTML page of the preview window: the screen drawn by `cvw-preview`, the problems under it, or a message.
 * The page has no scripts, and a Content Security Policy that allows only inline styles (the screen's colours), so
 * nothing in a view can load or run anything. The same page as the VS Code extension's PreviewPage.js.
 *
 * Example: `browser.loadHTML(PreviewPage.screen(result.html, result.problems, dark = true))`.
 */
object PreviewPage {
    /** A page showing a drawn screen and its problems; [stale] says the screen is from an older text. */
    fun screen(screenHtml: String, problems: List<PreviewProblem>, dark: Boolean, stale: Boolean = false): String {
        val list = if (problems.isEmpty()) "" else buildString {
            append("<section class=\"problems\"><h2>Problems</h2><ul>")
            for (problem in problems) {
                val where = if (problem.line != null) "Line ${problem.line}, column ${problem.column}: " else ""
                val code = problem.code?.let { "$it " } ?: ""
                val severity = if (problem.severity == "error" || problem.severity == "warning") problem.severity else "note"
                append("<li class=\"").append(severity).append("\">").append(escape(where + code + problem.message)).append("</li>")
            }
            append("</ul></section>")
        }
        val staleNote = if (stale) "<p class=\"note\">Showing the last preview that could be drawn.</p>" else ""
        return page(dark, staleNote + PreviewRunner.onlyScreen(screenHtml) + list)
    }

    /** A page with a message instead of a screen; text between backticks in [lines] is shown as code. */
    fun message(heading: String, lines: List<String>, dark: Boolean): String {
        val body = lines.joinToString("") { "<p>" + escape(it).replace(Regex("`([^`]+)`"), "<code>$1</code>") + "</p>" }
        return page(dark, "<section class=\"message\"><h2>${escape(heading)}</h2>$body</section>")
    }

    private fun page(dark: Boolean, body: String): String {
        val background = if (dark) "#2b2d30" else "#ffffff"
        val foreground = if (dark) "#dfe1e5" else "#1e1f22"
        return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src &#39;none&#39;; style-src &#39;unsafe-inline&#39;;">
<style>
    body { background: $background; color: $foreground; font-family: sans-serif; font-size: 13px; padding: 10px; margin: 0; }
    .cvw-screen { font-family: "JetBrains Mono", Consolas, Menlo, monospace; font-size: 13px; line-height: normal; margin: 0; padding: 12px; background: #0c0c0c; color: #cccccc; border-radius: 4px; overflow-x: auto; }
    h2 { font-size: 1.1em; margin: 14px 0 6px; }
    ul { padding-left: 20px; margin: 0; }
    li { margin: 3px 0; }
    .error { color: #f75464; }
    .warning { color: #e5c07b; }
    .note { color: #8c8c8c; }
    code { font-family: "JetBrains Mono", Consolas, Menlo, monospace; }
</style>
</head>
<body>
$body
</body>
</html>
"""
    }
}
