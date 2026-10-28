package com.github.mateuszpodeszwa.consolemvc.cvwsupport.settings

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.preview.ToolLocator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * The plugin's settings, kept for the whole IDE (not per project, so a project cannot choose which program the
 * plugin runs). Edited under Settings | Tools | ConsoleMVC.
 *
 * Example: `CvwSettings.instance.state.previewWidth`.
 */
@Service(Service.Level.APP)
@State(name = "ConsoleMvcCvwSettings", storages = [Storage("consolemvc-cvw.xml")])
class CvwSettings : PersistentStateComponent<CvwSettings.Values> {
    /**
     * The values of the settings.
     *
     * @property previewPath The `cvw-preview` command or its full path.
     * @property previewWidth The width of the preview in columns, 20 to 500.
     * @property highContrast Whether the preview uses ConsoleMVC's high-contrast theme.
     * @property diagnosticsEnabled Whether views are checked while they are typed.
     */
    data class Values(
        var previewPath: String = ToolLocator.DEFAULT_COMMAND,
        var previewWidth: Int = 80,
        var highContrast: Boolean = false,
        var diagnosticsEnabled: Boolean = true,
    )

    private var values = Values()

    override fun getState(): Values = values

    override fun loadState(state: Values) {
        values = state.copy(previewWidth = state.previewWidth.coerceIn(20, 500))
    }

    companion object {
        /** The settings of this IDE. */
        val instance: CvwSettings get() = ApplicationManager.getApplication().getService(CvwSettings::class.java)
    }
}
