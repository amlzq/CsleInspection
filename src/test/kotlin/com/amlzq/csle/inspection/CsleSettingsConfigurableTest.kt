package com.amlzq.csle.inspection

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
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

    private fun hintLabel(root: Container): JBLabel =
        findAll(root, JBLabel::class.java).first { it.text?.startsWith("<html>") == true }

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
        assertEquals(tip, checkbox!!.toolTipText)

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

        assertFalse(hint.text.contains(kotlinHint))

        selectTarget(targetComboBox(root), "target.language.kotlin")

        assertTrue(hint.text.contains(kotlinHint))
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
}
