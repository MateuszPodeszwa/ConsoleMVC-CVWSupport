package com.github.mateuszpodeszwa.consolemvc.cvwsupport.settings

import com.github.mateuszpodeszwa.consolemvc.cvwsupport.preview.PreviewToolService
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel

/**
 * Settings | Tools | ConsoleMVC: the `cvw-preview` command, the preview's width and theme, and the checks.
 *
 * Example: registered in plugin.xml as an applicationConfigurable under "tools".
 */
class CvwSettingsConfigurable : BoundConfigurable("ConsoleMVC") {
    override fun createPanel(): DialogPanel {
        val values = CvwSettings.instance.state
        return panel {
            group("Preview") {
                row("cvw-preview command:") {
                    textField().bindText(values::previewPath).columns(40)
                        .comment("Install it with <code>dotnet tool install -g ConsoleMVC.Preview</code>. " +
                            "With the default, ~/.dotnet/tools is searched too.")
                }
                row("Width in columns:") {
                    intTextField(20..500).bindIntText(values::previewWidth)
                }
                row {
                    checkBox("Use the high-contrast theme").bindSelected(values::highContrast)
                }
            }
            group("Checks") {
                row {
                    checkBox("Check views while typing").bindSelected(values::diagnosticsEnabled)
                        .comment("With cvw-preview, the build's own diagnostics; without it, checks against the markup schema.")
                }
            }
        }
    }

    override fun apply() {
        super.apply()
        PreviewToolService.instance.settingsChanged()
    }
}
