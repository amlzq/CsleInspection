package com.amlzq.csle.inspection

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.WrapLayout
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

    private val options: Array<String> = CsleGlyphs.entries.map { it.label }.toTypedArray()

    override fun createComponent(): JComponent {
        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10)
        }

        val longest = options.maxByOrNull { it.length } ?: ""

        inspectComboBox = ComboBox(options).apply {
            setPrototypeDisplayValue(longest)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            addItemListener { e ->
                if (e.stateChange == ItemEvent.SELECTED) {
                    val selected = selectedItem ?: ""
                    quickFixComboBox.removeAllItems()
                    options.filter { it != selected }.forEach { quickFixComboBox.addItem(it) }
                }
            }
        }
        quickFixComboBox = ComboBox(options.filter { it != CsleSettings.instance.state.inspect }.toTypedArray()).apply {
            setPrototypeDisplayValue(longest)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        val comboPanel = JPanel(WrapLayout(FlowLayout.LEFT, 5, 5)).apply {
            add(JLabel(CsleBundle.message("inspection.label")))
            add(inspectComboBox)
            add(JLabel(CsleBundle.message("quickfix.label")))
            add(quickFixComboBox)
        }
        mainPanel.add(comboPanel)

        val excludedLabel = JBTextArea(CsleBundle.message("excluded.label")).apply {
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            border = null
            isEditable = false
            background = null
        }
        // 用于自动调整宽度的容器
        val labelPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5, 0)
            add(excludedLabel, BorderLayout.CENTER)
            maximumSize = Dimension(Int.MAX_VALUE, excludedLabel.preferredSize.height * 3)
        }
        // 添加监听器动态调整宽度
        mainPanel.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                val width = labelPanel.width
                excludedLabel.preferredSize = Dimension(width, excludedLabel.preferredSize.height)
                excludedLabel.revalidate()
            }
        })
        mainPanel.add(labelPanel)

        excludedField = JBTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }
        val scrollPane = JBScrollPane(excludedField).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            preferredSize = Dimension(300, 150)
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }
        val fieldRow = JPanel(WrapLayout(FlowLayout.LEFT, 5, 2)).apply {
            add(scrollPane)
        }
        mainPanel.add(fieldRow)
        mainPanel.add(Box.createVerticalGlue())

        return mainPanel
    }

    private fun functionNames(): List<String> {
        return excludedField.text.trim().split("\n")
    }

    override fun isModified(): Boolean {
        val inspect = inspectComboBox.selectedItem as String
        val quickFix = quickFixComboBox.selectedItem as String
        return inspect != CsleSettings.instance.state.inspect
                || quickFix != CsleSettings.instance.state.quickFix
                || CsleSettings.instance.state.excluded != functionNames().map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun apply() {
        CsleSettings.instance.state.inspect = inspectComboBox.selectedItem as String
        CsleSettings.instance.state.quickFix = quickFixComboBox.selectedItem as String
        CsleSettings.instance.state.excluded = functionNames().map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun reset() {
        inspectComboBox.selectedItem = CsleSettings.instance.state.inspect
        quickFixComboBox.selectedItem = CsleSettings.instance.state.quickFix
        excludedField.text = CsleSettings.instance.state.excluded.joinToString("\n")
    }

    override fun getDisplayName(): String = CsleBundle.message("display.name")
}
