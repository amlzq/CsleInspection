package com.amlzq.csle.inspection

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ItemEvent
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CsleSettingsConfigurable : Configurable {
    private lateinit var inspectComboBox: JComboBox<String>
    private lateinit var quickFixComboBox: JComboBox<String>
    private lateinit var excludedField: JBTextArea

    private val options: Array<String> = CsleMode.entries.map { it.label }.toTypedArray()

    override fun createComponent(): JComponent {
        val panel = JPanel(GridBagLayout())

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST // 靠上左对齐
            insets = JBUI.insets(5) // 设置每个组件的间隔
            weightx = 1.0 // 防止水平拉伸
            weighty = 0.0 // 防止垂直拉伸
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
            layout = FlowLayout(FlowLayout.LEFT, 0, 5)
            add(JLabel("Inspect whether a string literal expression contains"))
            add(inspectComboBox)
            add(JLabel(", and quick fix to "))
            add(quickFixComboBox)
        }

        // 将 inspectionPanel 添加到 GridBagLayout 中
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 2 // 使其横跨两列
        gbc.anchor = GridBagConstraints.NORTHWEST // 靠上左对齐
        panel.add(inspectionPanel, gbc)

        val excludedLabel =
            JLabel("If you want to exclude some special functions (e.g. print, log)of inspection, fill in the field following to wrap the split.").apply {
                alignmentX = Component.LEFT_ALIGNMENT
            }

        // 将 excludedLabel 添加到 GridBagLayout 中
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.gridwidth = 2
        gbc.anchor = GridBagConstraints.NORTHWEST // 靠上左对齐
        panel.add(excludedLabel, gbc)

        excludedField = JBTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
        }

        // 将 excludedField 添加到 GridBagLayout 中
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.gridwidth = 1
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.anchor = GridBagConstraints.NORTHWEST // 靠上左对齐
        panel.add(JBScrollPane(excludedField).apply {
            preferredSize = Dimension(150, 150)  // 设置输入框的宽高
            maximumSize = Dimension(150, 150)   // 限制输入框的最大尺寸
        }, gbc)

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
        return "Chinese Expression Inspection (Java/Kotlin)"
    }
}
