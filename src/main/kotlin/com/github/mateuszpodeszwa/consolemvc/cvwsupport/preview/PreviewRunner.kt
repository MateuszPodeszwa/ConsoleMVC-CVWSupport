package com.github.mateuszpodeszwa.consolemvc.cvwsupport.preview

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A problem the tool listed after the screen.
 *
 * @property severity error, warning or note.
 * @property line The line, from 1, or null for a note.
 * @property column The column, from 1, or null for a note.
 * @property code The diagnostic, such as CVW105, or null.
 */
data class PreviewProblem(val severity: String, val line: Int?, val column: Int?, val code: String?, val message: String)

/** The outcome of running the tool. */
sealed class PreviewResult {
    /** The preview was drawn: the tool's HTML and its problems. */
    data class Drawn(val html: String, val problems: List<PreviewProblem>) : PreviewResult()

    /** The command was not found. */
    data object NotInstalled : PreviewResult()

    /** The tool failed, timed out or wrote something unexpected. */
    data class Failed(val error: String) : PreviewResult()
}

/**
 * Runs `cvw-preview` (ConsoleMVC.Preview) on a view's text and reads its HTML output: the drawn screen and the list of
 * problems. The command runs without a shell, with its arguments as a list, so nothing in a file name or the view can
 * become a command; the view's text goes through standard input. The same protocol as the VS Code extension's
 * PreviewRunner.js.
 *
 * Example:
 * ```kotlin
 * when (val result = PreviewRunner("cvw-preview").run(file, text, 80, false)) {
 *     is PreviewResult.Drawn -> show(result.html)
 *     else -> showMessage(result)
 * }
 * ```
 *
 * @property command The `cvw-preview` command or its full path.
 */
class PreviewRunner(private val command: String) {
    companion object {
        /** How long one preview may take before it is stopped, in milliseconds. */
        const val TIMEOUT_MS = 10_000L

        /** The most output read from the tool; a bigger preview is refused. */
        const val MAX_OUTPUT_BYTES = 8 * 1024 * 1024

        private const val SCREEN_START = "<pre class=\"cvw-screen\">"
        private val ITEM = Regex("<li class=\"cvw-(error|warning|note)\"(?: data-line=\"(\\d+)\" data-column=\"(\\d+)\")?>([\\s\\S]*?)</li>")
        private val LOCATED = Regex("^Line \\d+, column \\d+: (CVW\\d+) ([\\s\\S]*)$")
        private val ENTITY = Regex("&(?:#(\\d+)|#x([0-9a-fA-F]+)|(lt|gt|amp|quot|apos));")

        /**
         * The arguments for a preview; without a file (an unsaved view) the tool has no project for layouts.
         *
         * Example: `arguments("/shop/Views/Home/IndexView.cvw", 100, false)` is
         * `[/shop/Views/Home/IndexView.cvw, --stdin, --format, html, --width, 100]`.
         */
        fun arguments(file: String?, width: Int, highContrast: Boolean): List<String> {
            val args = mutableListOf<String>()
            if (file != null) args += file
            args += listOf("--stdin", "--format", "html", "--width", width.coerceIn(20, 500).toString())
            if (highContrast) args += "--high-contrast"
            return args
        }

        /** Reads the problems listed after the screen in the tool's HTML output. */
        fun parseProblems(html: String): List<PreviewProblem> {
            val listStart = html.indexOf("<ul class=\"cvw-problems\">")
            if (listStart < 0) return emptyList()
            return ITEM.findAll(html.substring(listStart)).map { match ->
                var message = decode(match.groupValues[4])
                var code: String? = null
                LOCATED.find(message)?.let {
                    code = it.groupValues[1]
                    message = it.groupValues[2]
                }
                PreviewProblem(match.groupValues[1], match.groupValues[2].toIntOrNull(), match.groupValues[3].toIntOrNull(), code, message)
            }.toList()
        }

        /**
         * Keeps only the tool's `<pre class="cvw-screen">…</pre>`, with only its `<span style="…">` runs as markup:
         * any other tag is escaped, so an unexpected tool cannot add markup to the preview.
         *
         * Example: `onlyScreen("<pre class=\"cvw-screen\">x<img></pre>")` is `<pre class="cvw-screen">x&lt;img></pre>`.
         */
        fun onlyScreen(html: String): String {
            val start = html.indexOf(SCREEN_START)
            val end = if (start < 0) -1 else html.indexOf("</pre>", start)
            if (start < 0 || end < 0) return "$SCREEN_START</pre>"
            val inner = html.substring(start + SCREEN_START.length, end)
            return SCREEN_START + inner.replace(Regex("<(?!/?span(?: style=\"[^\"<>]*\")?>)"), "&lt;") + "</pre>"
        }

        /** Decodes the HTML entities the tool writes. Example: `decode("&lt;a&gt; &#39;b&#39;")` is `<a> 'b'`. */
        fun decode(text: String): String = ENTITY.replace(text) { match ->
            val (decimal, hex, named) = match.destructured
            when {
                decimal.isNotEmpty() -> String(Character.toChars(decimal.toInt()))
                hex.isNotEmpty() -> String(Character.toChars(hex.toInt(16)))
                else -> mapOf("lt" to "<", "gt" to ">", "amp" to "&", "quot" to "\"", "apos" to "'")[named.lowercase()] ?: match.value
            }
        }
    }

    /**
     * Draws a view; never throws.
     *
     * @param file The view's path, for its layout and partial views, or null for an unsaved view.
     * @param text The view's current text.
     * @param width The width in columns.
     * @param highContrast Whether to use the high-contrast theme.
     */
    fun run(file: String?, text: String, width: Int, highContrast: Boolean): PreviewResult {
        val process = try {
            ProcessBuilder(listOf(command) + arguments(file, width, highContrast))
                .redirectErrorStream(false)
                .start()
        } catch (e: IOException) {
            return if (isNotFound(e)) PreviewResult.NotInstalled else PreviewResult.Failed(e.message ?: e.toString())
        }

        return try {
            // Standard error is read on its own thread so a chatty tool cannot block on a full pipe
            val errors = StringBuilder()
            val errorReader = Thread {
                runCatching { process.errorStream.bufferedReader().use { reader -> errors.append(reader.readText().takeLast(4096)) } }
            }.apply { isDaemon = true; start() }

            process.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }

            val output = java.io.ByteArrayOutputStream()
            val reader = Thread {
                runCatching {
                    process.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0 || output.size() > MAX_OUTPUT_BYTES) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }.apply { isDaemon = true; start() }

            if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return PreviewResult.Failed("cvw-preview did not finish within ${TIMEOUT_MS / 1000} seconds.")
            }
            reader.join(TIMEOUT_MS)
            errorReader.join(1000)

            if (output.size() > MAX_OUTPUT_BYTES) {
                return PreviewResult.Failed("The preview is too large to show.")
            }
            if (process.exitValue() != 0) {
                return PreviewResult.Failed(errors.toString().trim().ifEmpty { "cvw-preview exited with code ${process.exitValue()}." })
            }
            val html = output.toString(Charsets.UTF_8)
            if (!html.startsWith(SCREEN_START)) {
                return PreviewResult.Failed("cvw-preview wrote something other than a preview; is it ConsoleMVC.Preview 1.34 or later?")
            }
            PreviewResult.Drawn(html, parseProblems(html))
        } catch (e: IOException) {
            PreviewResult.Failed(e.message ?: e.toString())
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    /** Whether starting the process failed because the command does not exist. */
    private fun isNotFound(e: IOException): Boolean {
        val message = e.message ?: return false
        return "error=2," in message || "No such file" in message || "cannot find the file" in message
    }
}

/**
 * Lists where to look for `cvw-preview`: the configured command, and when it is the default, also the folder where
 * `dotnet tool install -g` puts tools (~/.dotnet/tools), which is often missing from the PATH of an IDE started from
 * the desktop.
 *
 * Example: `ToolLocator.candidates("cvw-preview")` is `[cvw-preview, /home/me/.dotnet/tools/cvw-preview]`.
 */
object ToolLocator {
    /** The command name of the ConsoleMVC.Preview tool, and the default setting. */
    const val DEFAULT_COMMAND = "cvw-preview"

    /** The commands to try, in order. */
    fun candidates(configured: String?, windows: Boolean = System.getProperty("os.name").startsWith("Windows"),
                   home: String = System.getProperty("user.home")): List<String> {
        val command = configured?.trim().orEmpty().ifEmpty { DEFAULT_COMMAND }
        if (command != DEFAULT_COMMAND) return listOf(command)
        val executable = if (windows) "$DEFAULT_COMMAND.exe" else DEFAULT_COMMAND
        val separator = if (windows) "\\" else "/"
        return listOf(command, listOf(home, ".dotnet", "tools", executable).joinToString(separator))
    }

    /** Whether a full path names a file that exists; a bare command is looked up on the PATH by the system. */
    fun looksMissing(command: String): Boolean = (command.contains('/') || command.contains('\\')) && !File(command).exists()
}
