package com.amlzq.csle.inspection

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ItemEvent
import javax.swing.*

class CsleSettingsConfigurable : Configurable {
    private lateinit var inspectComboBox: JComboBox<String>
    private lateinit var quickFixComboBox: JComboBox<String>
    private lateinit var excludedField: JBTextArea

    private val options: Array<String> = CsleMode.entries.map { it.label }.toTypedArray()

    override fun createComponent(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        inspectComboBox = ComboBox(options).apply {
            addItemListener { e ->
                if (e.stateChange == ItemEvent.SELECTED) {
                    val selectedItem = inspectComboBox.selectedItem as String
                    quickFixComboBox.removeAllItems()
                    options.filter { it != selectedItem }.forEach { quickFixComboBox.addItem(it) }
                }
            }
        }
        quickFixComboBox = ComboBox(options.filter { it != CsleSettings.instance.state.inspect }.toTypedArray())

        val inspectionPanel = JPanel().apply {
            layout = FlowLayout(FlowLayout.LEFT, 0, 0)
            alignmentX = Component.LEFT_ALIGNMENT
            add(JLabel("Inspect whether a string literal expression contains"))
            add(inspectComboBox)
            add(JLabel(", and quick fix to "))
            add(quickFixComboBox)
        }
        panel.add(inspectionPanel)

        panel.add(Box.createVerticalStrut(10))

        val excludedLabel =
            JLabel("If you want to excluded some special functions of inspection, fill in the field following to wrap the split.").apply {
                alignmentX = Component.LEFT_ALIGNMENT
            }

        excludedField = JBTextArea(5, 20)
        excludedField.lineWrap = true
        excludedField.wrapStyleWord = true
//        excludedField.bounds = Rectangle(20, 20, 300, 150)
        excludedField.preferredSize = Dimension(300, 150)

        val excludedPanel = JPanel(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(excludedLabel, BorderLayout.NORTH)
            add(Box.createVerticalStrut(10))
            add(JBScrollPane(excludedField).apply {
                preferredSize = Dimension(300, 150)
            }, BorderLayout.CENTER)
        }
        panel.add(excludedPanel)

        return panel
    }

    private fun functionNames(): List<String> {
        return excludedField.text.trim().split("\n")
    }

    /**
     * 判断是否修改了设置
     */
    override fun isModified(): Boolean {
        val inspect = inspectComboBox.selectedItem as String
        val quickFix = quickFixComboBox.selectedItem as String
        return inspect != CsleSettings.instance.state.inspect || quickFix != CsleSettings.instance.state.quickFix || CsleSettings.instance.state.excluded != functionNames().map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * 保存用户的选择
     */
    override fun apply() {
        CsleSettings.instance.state.inspect = inspectComboBox.selectedItem as String
        CsleSettings.instance.state.quickFix = quickFixComboBox.selectedItem as String
        CsleSettings.instance.state.excluded = functionNames().map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 恢复选中项和输入框内容
     */
    override fun reset() {
        super.reset()
        inspectComboBox.selectedItem = CsleSettings.instance.state.inspect
        quickFixComboBox.selectedItem = CsleSettings.instance.state.quickFix
        excludedField.text = CsleSettings.instance.state.excluded.joinToString("\n")
    }

    override fun getDisplayName(): String {
        return "Chinese Expression Inspection (Dart)"
    }
}
