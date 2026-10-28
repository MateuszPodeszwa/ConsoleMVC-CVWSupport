package com.github.mateuszpodeszwa.consolemvc.cvwsupport.preview

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.settings.CvwSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs `cvw-preview` for the preview window and the checks, with the settings: it tries the configured command (and
 * ~/.dotnet/tools for the default), remembers the one that worked, and remembers that the tool is missing until the
 * settings change, so a missing tool is not looked for on every keystroke.
 *
 * Example: `PreviewToolService.instance.draw(file.path, text)`.
 */
@Service(Service.Level.APP)
class PreviewToolService {
    private val workingCommand = AtomicReference<String?>(null)

    @Volatile
    private var missing = false

    /** Draws a view; [PreviewResult.NotInstalled] when no command works. Call it off the UI thread. */
    fun draw(file: String?, text: String): PreviewResult {
        if (missing) return PreviewResult.NotInstalled
        val settings = CvwSettings.instance.state
        val commands = workingCommand.get()?.let { listOf(it) } ?: ToolLocator.candidates(settings.previewPath)
        for (command in commands) {
            if (ToolLocator.looksMissing(command)) continue
            val result = PreviewRunner(command).run(file, text, settings.previewWidth, settings.highContrast)
            if (result != PreviewResult.NotInstalled) {
                workingCommand.set(command)
                return result
            }
        }
        missing = true
        return PreviewResult.NotInstalled
    }

    /** Forgets the command found and whether it was missing, after the settings change. */
    fun settingsChanged() {
        workingCommand.set(null)
        missing = false
    }

    companion object {
        /** The service of this IDE. */
        val instance: PreviewToolService get() = ApplicationManager.getApplication().getService(PreviewToolService::class.java)
    }
}
