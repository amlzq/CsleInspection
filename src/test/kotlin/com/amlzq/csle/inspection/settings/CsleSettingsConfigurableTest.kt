package com.amlzq.csle.inspection.settings

import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.amlzq.csle.inspection.util.CsleBundle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.components.DslLabel
import java.awt.Component
import java.awt.Container

/**
 * 设置页的默认值与 [Configurable] 交互。
 *
 * 「Doc comments」控制是否检查文档注释（KDoc/Javadoc/JSDoc/PHPDoc/DartDoc），默认必须关闭，
 * 只检查字符串字面量；这里断言 UI 的初始状态，避免状态字段与界面绑定被改反。
 * 同时覆盖 apply / isModified / reset：排除列表按行解析、回填，以及检查项选择联动。
 */
class CsleSettingsConfigurableTest : CsleBasePlatformTest() {

    private fun createConfigurable(): Pair<Configurable, Container> {
        val configurable = CsleSettingsConfigurable()
        return configurable to (configurable.createComponent() as Container)
    }

    private fun findCheckBox(container: Container, text: String): JBCheckBox? =
        findAll(container, JBCheckBox::class.java).firstOrNull { it.text == text }

    private fun <T : Component> findAll(container: Container, type: Class<T>): List<T> {
        val result = mutableListOf<T>()
        for (child in container.components) {
            if (type.isInstance(child)) result.add(type.cast(child))
            if (child is Container) result.addAll(findAll(child, type))
        }
        return result
    }

    /** 字形选择框（含全部字形，且区别于不含当前检查项的目标字形下拉框） */
    private fun inspectComboBox(root: Container): ComboBox<*> =
        findAll(root, ComboBox::class.java).first { it.itemCount == CsleGlyphs.entries.size }

    private fun quickFixComboBox(root: Container): ComboBox<*> {
        val inspect = inspectComboBox(root)
        return findAll(root, ComboBox::class.java)
            .first { it !== inspect && it.itemCount == CsleGlyphs.entries.size - 1 }
    }

    private fun items(comboBox: ComboBox<*>): List<Any?> =
        (0 until comboBox.itemCount).map { comboBox.getItemAt(it) }

    /** 排除列表上方的目标语言下拉框（选项是 ExcludedTarget，不是字形名） */
    private fun targetComboBox(root: Container): ComboBox<*> =
        findAll(root, ComboBox::class.java).first { it.itemCount > 0 && it.getItemAt(0) !is String }

    private fun selectTarget(comboBox: ComboBox<*>, labelKey: String) {
        val label = CsleBundle.message(labelKey)
        val item = items(comboBox).first { it.toString() == label }
        comboBox.selectedItem = item
    }

    /** 排除列表上方的说明文案（UI DSL 的 comment 组件，会按宽度自动换行） */
    private fun hintLabel(root: Container): DslLabel {
        val prefix = CsleBundle.message("excluded.label.part1")
        return findAll(root, DslLabel::class.java).first { it.userText?.startsWith(prefix) == true }
    }

    fun testDocCommentsUncheckedByDefault() {
        assertFalse(CsleSettings.instance.state.checkDocComments)

        val (_, root) = createConfigurable()
        val checkbox = findCheckBox(root, CsleBundle.message("inspect.target.doc.comments"))

        assertNotNull(checkbox)
        assertFalse(checkbox!!.isSelected)
    }

    fun testDocCommentsCheckBoxFollowsState() {
        CsleSettings.instance.state.checkDocComments = true

        val (_, root) = createConfigurable()
        val checkbox = findCheckBox(root, CsleBundle.message("inspect.target.doc.comments"))

        assertNotNull(checkbox)
        assertTrue(checkbox!!.isSelected)
    }

    fun testLiteralExpressionAlwaysCheckedAndDisabled() {
        CsleSettings.instance.state.checkLiteralExpression = false

        val (_, root) = createConfigurable()
        val checkbox = findCheckBox(root, CsleBundle.message("inspect.target.literal.expression"))

        assertNotNull(checkbox)
        // 字面量检查是基本功能：始终勾选且不允许用户改动
        assertTrue(checkbox!!.isSelected)
        assertFalse(checkbox.isEnabled)

        // 复选框下有常驻说明，悬停提示同样带折行
        val hint = CsleBundle.message("inspect.target.literal.expression.hint")
        assertTrue(findAll(root, DslLabel::class.java).any { it.userText == hint })
        val tooltip = checkbox.toolTipText
        assertNotNull(tooltip)
        assertTrue(tooltip!!.contains(CsleBundle.message("inspect.target.literal.expression.tip")))
    }

    fun testInitiallyNotModified() {
        val (configurable, _) = createConfigurable()
        assertFalse(configurable.isModified())
    }

    fun testDocCommentsHintExplainsUnsupportedLanguages() {
        val (_, root) = createConfigurable()
        val hint = CsleBundle.message("inspect.target.doc.comments.hint")
        val tip = CsleBundle.message("inspect.target.doc.comments.tip")

        // 开关上挂完整说明的悬停提示；设置页里还有一行常驻说明
        val checkbox = findCheckBox(root, CsleBundle.message("inspect.target.doc.comments"))
        assertNotNull(checkbox)
        val tooltip = checkbox!!.toolTipText
        assertNotNull(tooltip)
        assertTrue(tooltip!!.contains(tip))
        // 长提示按固定宽度折行，不再是一条超长横条
        assertTrue(tooltip.startsWith("<html>") && tooltip.contains("width:"))

        // 常驻说明与悬停提示都要点名没有文档注释语法的语言
        listOf(hint, tip).forEach {
            assertTrue(it.contains("HTML"))
            assertTrue(it.contains("CSS"))
            assertTrue(it.contains("Python"))
        }
        // Python 的替代规则只写在完整提示里
        assertTrue(tip.contains("docstring", ignoreCase = true))
    }

    fun testSelectingInspectRemovesItFromQuickFixOptions() {
        val (configurable, root) = createConfigurable()
        val inspect = inspectComboBox(root)
        val quickFix = quickFixComboBox(root)

        inspect.selectedItem = CsleGlyphs.HONGKONG.label

        val options = items(quickFix)
        assertFalse(options.contains(CsleGlyphs.HONGKONG.label))
        assertTrue(configurable.isModified())

        configurable.apply()
        assertEquals(CsleGlyphs.HONGKONG.label, CsleSettings.instance.state.inspect)
        assertFalse(CsleSettings.instance.state.quickFix == CsleGlyphs.HONGKONG.label)
        assertFalse(configurable.isModified())
    }

    fun testApplyNormalizesExcludedLines() {
        val (configurable, root) = createConfigurable()
        val textArea = findAll(root, JBTextArea::class.java).single()
        textArea.text = "  logger \n\n println  "

        assertTrue(configurable.isModified())
        configurable.apply()

        val state = CsleSettings.instance.state
        // 按行解析：去掉首尾空白与空行，只写回当前选中的语言
        assertEquals(listOf("logger", "println"), state.excludedJs)
        assertTrue(state.excludedJava.isEmpty())
        assertFalse(configurable.isModified())
    }

    fun testApplyPersistsDocCommentsAndLiteralSwitch() {
        CsleSettings.instance.state.checkLiteralExpression = false
        val (configurable, root) = createConfigurable()
        val checkbox = findCheckBox(root, CsleBundle.message("inspect.target.doc.comments"))!!

        checkbox.isSelected = true
        assertTrue(configurable.isModified())
        configurable.apply()

        assertTrue(CsleSettings.instance.state.checkDocComments)
        // 字面量检查项在 UI 中不可关闭，apply 时保持开启
        assertTrue(CsleSettings.instance.state.checkLiteralExpression)
    }

    fun testSwitchTargetKeepsEditedText() {
        val (configurable, root) = createConfigurable()
        val textArea = findAll(root, JBTextArea::class.java).single()
        val targets = targetComboBox(root)

        textArea.text = "logger"
        selectTarget(targets, "target.language.kotlin")
        // 切到未编辑的目标时显示其自身状态（默认空）
        assertEquals("", textArea.text)

        textArea.text = "println"
        selectTarget(targets, "target.language.js_ts")
        // 切回来时保留未应用的编辑内容
        assertEquals("logger", textArea.text)

        configurable.apply()
        assertEquals(listOf("logger"), CsleSettings.instance.state.excludedJs)
        assertEquals(listOf("println"), CsleSettings.instance.state.excludedKotlin)
    }

    fun testTargetTextLoadedFromState() {
        CsleSettings.instance.state.excludedKotlin = listOf("println", "logger")

        val (_, root) = createConfigurable()
        val textArea = findAll(root, JBTextArea::class.java).single()
        selectTarget(targetComboBox(root), "target.language.kotlin")

        assertEquals("println\nlogger", textArea.text)
    }

    fun testResetSelectsJsTargetAndReloadsText() {
        CsleSettings.instance.state.excludedJs = listOf("logger")
        CsleSettings.instance.state.excludedKotlin = listOf("println")

        val (configurable, root) = createConfigurable()
        val textArea = findAll(root, JBTextArea::class.java).single()
        val targets = targetComboBox(root)

        selectTarget(targets, "target.language.kotlin")
        textArea.text = ""
        assertTrue(configurable.isModified())

        configurable.reset()

        assertEquals(CsleBundle.message("target.language.js_ts"), targets.selectedItem.toString())
        assertEquals("logger", textArea.text)
        assertFalse(configurable.isModified())
    }

    fun testExcludedHintLabelFollowsTarget() {
        val (_, root) = createConfigurable()
        val hint = hintLabel(root)
        val kotlinHint = CsleBundle.message("excluded.label.part2.kotlin")

        assertFalse(hint.userText?.contains(kotlinHint) == true)
        // 文案不再手工插入 <br>，换行交给组件按宽度决定
        assertFalse(hint.text.contains("<br"))

        selectTarget(targetComboBox(root), "target.language.kotlin")

        assertTrue(hint.userText?.contains(kotlinHint) == true)
    }

    fun testDisplayName() {
        assertEquals(CsleBundle.message("display.name"), CsleSettingsConfigurable().displayName)
    }

    fun testResetRestoresUiFromState() {
        CsleSettings.instance.state.checkDocComments = true
        CsleSettings.instance.state.excludedJs = listOf("logger")

        val (configurable, root) = createConfigurable()
        val checkbox = findCheckBox(root, CsleBundle.message("inspect.target.doc.comments"))!!
        val textArea = findAll(root, JBTextArea::class.java).single()
        assertEquals("logger", textArea.text)

        checkbox.isSelected = false
        textArea.text = ""
        assertTrue(configurable.isModified())

        configurable.reset()

        assertTrue(checkbox.isSelected)
        assertEquals("logger", textArea.text)
        assertFalse(configurable.isModified())
    }

    private fun autoScanCheckBox(root: Container) =
        findCheckBox(root, CsleBundle.message("scan.auto.on.open"))!!

    fun testAutoScanUncheckedByDefault() {
        assertFalse(CsleSettings.instance.state.autoScanOnOpen)
        val (configurable, root) = createConfigurable()
        assertFalse(autoScanCheckBox(root).isSelected)
        assertFalse(configurable.isModified())
    }

    fun testAutoScanApplyAndReset() {
        val (configurable, root) = createConfigurable()
        val checkbox = autoScanCheckBox(root)
        try {
            checkbox.isSelected = true
            assertTrue(configurable.isModified())
            configurable.reset()
            assertFalse(checkbox.isSelected)

            checkbox.isSelected = true
            configurable.apply()
            assertTrue(CsleSettings.instance.state.autoScanOnOpen)
            assertFalse(configurable.isModified())
        } finally {
            CsleSettings.instance.autoScanOnOpen = false
        }
    }

    fun testAutoScanSurvivesStateRoundTrip() {
        val settings = CsleSettings.instance
        try {
            val saved = CsleSettings().also { it.autoScanOnOpen = true }
            settings.autoScanOnOpen = false
            settings.loadState(saved)
            assertTrue(settings.autoScanOnOpen)
        } finally {
            settings.autoScanOnOpen = false
        }
    }
}
