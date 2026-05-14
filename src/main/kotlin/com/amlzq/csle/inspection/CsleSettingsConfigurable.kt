package com.amlzq.csle.inspection

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import java.awt.event.ItemEvent

class CsleSettingsConfigurable : Configurable {
    private lateinit var inspectComboBox: ComboBox<String>
    private lateinit var quickFixComboBox: ComboBox<String>
    private lateinit var excludedField: JBTextArea

    private val options: Array<String> = CsleGlyphs.entries.map { it.label }.toTypedArray()

    override fun createComponent(): javax.swing.JComponent {
        val longest = options.maxByOrNull { it.length } ?: ""

        inspectComboBox = ComboBox(options).apply {
            setPrototypeDisplayValue(longest)
        }
        quickFixComboBox = ComboBox(emptyArray<String>()).apply {
            setPrototypeDisplayValue(longest)
        }

        excludedField = JBTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
        }
        val scrollPane = JBScrollPane(excludedField).apply { preferredSize = Dimension(300, 150) }

        val initialInspect = CsleSettings.instance.state.inspect
        inspectComboBox.selectedItem = initialInspect
        updateQuickFixOptions(initialInspect, preferredQuickFix = CsleSettings.instance.state.quickFix)

        inspectComboBox.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                val selectedInspect = inspectComboBox.selectedItem as? String ?: return@addItemListener
                updateQuickFixOptions(selectedInspect)
            }
        }

        return panel {
            row {
                text(CsleBundle.message("inspection.label"))
                cell(inspectComboBox)
                text(CsleBundle.message("quickfix.label"))
                cell(quickFixComboBox)
            }

            row {
                text(CsleBundle.message("excluded.label"))
            }

            row {
                cell(scrollPane)
            }
        }
    }

    private fun updateQuickFixOptions(selectedInspect: String, preferredQuickFix: String? = null) {
        val currentQuickFix = quickFixComboBox.selectedItem as? String
        val items = options.filter { it != selectedInspect }
        quickFixComboBox.removeAllItems()
        items.forEach { quickFixComboBox.addItem(it) }

        val toSelect = when {
            preferredQuickFix != null && items.contains(preferredQuickFix) -> preferredQuickFix
            currentQuickFix != null && items.contains(currentQuickFix) -> currentQuickFix
            else -> items.firstOrNull()
        }
        if (toSelect != null) {
            quickFixComboBox.selectedItem = toSelect
        }
    }

    private fun functionNames(): List<String> {
        return excludedField.text.trim().split("\n")
    }

    override fun isModified(): Boolean {
        val inspect = inspectComboBox.selectedItem as? String ?: ""
        val quickFix = quickFixComboBox.selectedItem as? String ?: ""
        return inspect != CsleSettings.instance.state.inspect
                || quickFix != CsleSettings.instance.state.quickFix
                || CsleSettings.instance.state.excluded != functionNames().map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun apply() {
        CsleSettings.instance.state.inspect =
            inspectComboBox.selectedItem as? String ?: CsleSettings.instance.state.inspect
        CsleSettings.instance.state.quickFix =
            quickFixComboBox.selectedItem as? String ?: CsleSettings.instance.state.quickFix
        CsleSettings.instance.state.excluded = functionNames().map { it.trim() }.filter { it.isNotEmpty() }

        // 在后台执行自动刷新所有 "处于编辑器中的文件" 的 inspection
        // 解决“用户修改字形配置之后处于编辑器中的文件没有自动刷新”的问题
        ApplicationManager.getApplication().invokeLater {
            val projectManager = ProjectManager.getInstance()
            projectManager.openProjects.forEach { project ->
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        }
    }

    override fun reset() {
        val inspect = CsleSettings.instance.state.inspect
        inspectComboBox.selectedItem = inspect
        updateQuickFixOptions(inspect, preferredQuickFix = CsleSettings.instance.state.quickFix)
        excludedField.text = CsleSettings.instance.state.excluded.joinToString("\n")
    }

    override fun getDisplayName(): String = CsleBundle.message("display.name")
}
