package com.amlzq.csle.inspection

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import java.awt.event.ItemEvent

class CsleSettingsConfigurable : Configurable {
    private lateinit var inspectComboBox: ComboBox<String>
    private lateinit var quickFixComboBox: ComboBox<String>
    private lateinit var targetLanguageComboBox: ComboBox<ExcludedTarget>
    private lateinit var excludedHintLabel: JBLabel
    private lateinit var excludedField: JBTextArea
    private lateinit var literalExpressionCheckBox: JBCheckBox
    private lateinit var docCommentsCheckBox: JBCheckBox

    private val options: Array<String> = CsleGlyphs.entries.map { it.label }.toTypedArray()
    private val excludedTextByTarget: MutableMap<ExcludedTarget, String> = mutableMapOf()
    private var currentTarget: ExcludedTarget = ExcludedTarget.JS_TS

    override fun createComponent(): javax.swing.JComponent {
        val longest = options.maxByOrNull { it.length } ?: ""

        inspectComboBox = ComboBox(options).apply {
            setPrototypeDisplayValue(longest)
        }
        quickFixComboBox = ComboBox(emptyArray<String>()).apply {
            setPrototypeDisplayValue(longest)
        }

        val excludedTargets = ExcludedTarget.entries.toTypedArray()
        val longestTarget = excludedTargets.maxByOrNull { it.toString().length } ?: ExcludedTarget.JS_TS
        targetLanguageComboBox = ComboBox(excludedTargets).apply {
            setPrototypeDisplayValue(longestTarget)
            selectedItem = currentTarget
        }

        excludedHintLabel = JBLabel()

        excludedField = JBTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
        }
        val scrollPane = JBScrollPane(excludedField).apply { preferredSize = Dimension(300, 150) }

        literalExpressionCheckBox = JBCheckBox(
            CsleBundle.message("inspect.target.literal.expression"),
            CsleSettings.instance.state.checkLiteralExpression,
        )
        literalExpressionCheckBox.isEnabled = false
        docCommentsCheckBox = JBCheckBox(
            CsleBundle.message("inspect.target.doc.comments"),
            CsleSettings.instance.state.checkDocComments,
        )

        val initialInspect = CsleSettings.instance.state.inspect
        inspectComboBox.selectedItem = initialInspect
        updateQuickFixOptions(initialInspect, preferredQuickFix = CsleSettings.instance.state.quickFix)

        inspectComboBox.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                val selectedInspect = inspectComboBox.selectedItem as? String ?: return@addItemListener
                updateQuickFixOptions(selectedInspect)
            }
        }

        targetLanguageComboBox.addItemListener { e ->
            if (e.stateChange != ItemEvent.SELECTED) return@addItemListener
            excludedTextByTarget[currentTarget] = excludedField.text
            val selected = targetLanguageComboBox.selectedItem as? ExcludedTarget ?: return@addItemListener
            currentTarget = selected
            excludedField.text = excludedTextByTarget[selected] ?: getExcludedFromState(selected).joinToString("\n")
            updateExcludedHintLabel()
        }

        val root = panel {

            row {
                text(CsleBundle.message("inspection.label"))
                cell(inspectComboBox)
                text(CsleBundle.message("quickfix.label"))
                cell(quickFixComboBox)
            }

            group("Inspect targets") {
                row {
                    cell(literalExpressionCheckBox)
                    cell(docCommentsCheckBox)
                }
            }

            group("Excluded list") {
                row {
                    text(CsleBundle.message("target.language.label"))
                    cell(targetLanguageComboBox)
                }

                row {
                    cell(excludedHintLabel)
                }

                row {
                    cell(scrollPane)
                }
            }
        }
        reset()
        return root
    }

    private fun updateExcludedHintLabel() {
        val part1 = CsleBundle.message("excluded.label.part1")
        val part2 = CsleBundle.message(excludedLabelPart2Key(currentTarget))
        val part3 = CsleBundle.message("excluded.label.part3")
        excludedHintLabel.text =
            "<html>${escapeHtml(part1)}<br>${escapeHtml(part2)}<br>${escapeHtml(part3)}</html>"
    }

    private fun excludedLabelPart2Key(target: ExcludedTarget): String {
        return when (target) {
            ExcludedTarget.JS_TS -> "excluded.label.part2.js_ts"
            ExcludedTarget.JAVA -> "excluded.label.part2.java"
            ExcludedTarget.KOTLIN -> "excluded.label.part2.kotlin"
            ExcludedTarget.PYTHON -> "excluded.label.part2.python"
            ExcludedTarget.PHP -> "excluded.label.part2.php"
            ExcludedTarget.DART -> "excluded.label.part2.dart"
            ExcludedTarget.HTML -> "excluded.label.part2.html"
            ExcludedTarget.CSS -> "excluded.label.part2.css"
            ExcludedTarget.XML -> "excluded.label.part2.xml"
            ExcludedTarget.JSON -> "excluded.label.part2.json"
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
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

    private fun normalizeLines(text: String): List<String> {
        return text.trim().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun getExcludedFromState(target: ExcludedTarget): List<String> {
        val state = CsleSettings.instance.state
        return when (target) {
            ExcludedTarget.JS_TS -> state.excludedJs
            ExcludedTarget.JAVA -> state.excludedJava
            ExcludedTarget.KOTLIN -> state.excludedKotlin
            ExcludedTarget.PYTHON -> state.excludedPython
            ExcludedTarget.PHP -> state.excludedPhp
            ExcludedTarget.DART -> state.excludedDart
            ExcludedTarget.HTML -> state.excludedHtml
            ExcludedTarget.CSS -> state.excludedCss
            ExcludedTarget.XML -> state.excludedXml
            ExcludedTarget.JSON -> state.excludedJson
        }
    }

    private fun setExcludedToState(target: ExcludedTarget, list: List<String>) {
        val state = CsleSettings.instance.state
        when (target) {
            ExcludedTarget.JS_TS -> state.excludedJs = list
            ExcludedTarget.JAVA -> state.excludedJava = list
            ExcludedTarget.KOTLIN -> state.excludedKotlin = list
            ExcludedTarget.PYTHON -> state.excludedPython = list
            ExcludedTarget.PHP -> state.excludedPhp = list
            ExcludedTarget.DART -> state.excludedDart = list
            ExcludedTarget.HTML -> state.excludedHtml = list
            ExcludedTarget.CSS -> state.excludedCss = list
            ExcludedTarget.XML -> state.excludedXml = list
            ExcludedTarget.JSON -> state.excludedJson = list
        }
    }

    private fun getExcludedText(target: ExcludedTarget): String {
        if (target == currentTarget) return excludedField.text
        return excludedTextByTarget[target] ?: getExcludedFromState(target).joinToString("\n")
    }

    override fun isModified(): Boolean {
        val inspect = inspectComboBox.selectedItem as? String ?: ""
        val quickFix = quickFixComboBox.selectedItem as? String ?: ""
        return inspect != CsleSettings.instance.state.inspect
                || quickFix != CsleSettings.instance.state.quickFix
                || ExcludedTarget.entries.any { target ->
            getExcludedFromState(target) != normalizeLines(getExcludedText(target))
        }
                || docCommentsCheckBox.isSelected != CsleSettings.instance.state.checkDocComments
    }

    override fun apply() {
        CsleSettings.instance.state.inspect =
            inspectComboBox.selectedItem as? String ?: CsleSettings.instance.state.inspect
        CsleSettings.instance.state.quickFix =
            quickFixComboBox.selectedItem as? String ?: CsleSettings.instance.state.quickFix
        excludedTextByTarget[currentTarget] = excludedField.text
        ExcludedTarget.entries.forEach { target ->
            setExcludedToState(target, normalizeLines(getExcludedText(target)))
        }
        CsleSettings.instance.state.checkLiteralExpression = true
        CsleSettings.instance.state.checkDocComments = docCommentsCheckBox.isSelected

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
        excludedTextByTarget.clear()
        ExcludedTarget.entries.forEach { target ->
            excludedTextByTarget[target] = getExcludedFromState(target).joinToString("\n")
        }
        currentTarget = ExcludedTarget.JS_TS
        targetLanguageComboBox.selectedItem = currentTarget
        excludedField.text = excludedTextByTarget[currentTarget].orEmpty()
        updateExcludedHintLabel()
        literalExpressionCheckBox.isSelected = true
        docCommentsCheckBox.isSelected = CsleSettings.instance.state.checkDocComments
    }

    override fun getDisplayName(): String = CsleBundle.message("display.name")
}

private enum class ExcludedTarget(val labelKey: String) {
    JS_TS("target.language.js_ts"),
    JAVA("target.language.java"),
    KOTLIN("target.language.kotlin"),
    PYTHON("target.language.python"),
    PHP("target.language.php"),
    DART("target.language.dart"),
    HTML("target.language.html"),
    CSS("target.language.css"),
    XML("target.language.xml"),
    JSON("target.language.json");

    override fun toString(): String = CsleBundle.message(labelKey)
}
