package com.amlzq.csle.inspection.settings

import com.amlzq.csle.inspection.util.CsleBundle
import com.amlzq.csle.inspection.util.CsleCustomDictionary
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import java.awt.event.ItemEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.event.DocumentEvent

class CsleSettingsConfigurable(private val project: Project) : Configurable {
    private lateinit var schemeComboBox: ComboBox<SettingsScheme>
    private lateinit var schemeGearButton: JComponent
    private var loading = false
    private lateinit var inspectComboBox: ComboBox<String>
    private lateinit var quickFixComboBox: ComboBox<String>
    private lateinit var targetLanguageComboBox: ComboBox<ExcludedTarget>
    private lateinit var excludedHintLabel: JEditorPane
    private lateinit var excludedField: ChipsInputField
    private lateinit var literalExpressionCheckBox: JBCheckBox
    private lateinit var docCommentsCheckBox: JBCheckBox
    private lateinit var autoScanCheckBox: JBCheckBox
    private lateinit var dictionaryStatusLabel: JEditorPane
    private lateinit var dictionarySourceField: JBTextField
    private lateinit var dictionaryTargetField: JBTextField
    private lateinit var dictionaryAddButton: JButton
    private lateinit var dictionaryAllButton: JButton
    private lateinit var settingsRoot: JComponent
    private var pendingDictionaries: MutableMap<String, String> = mutableMapOf()

    private val options: Array<String> = CsleGlyphs.entries.map { it.label }.toTypedArray()
    private val excludedTextByTarget: MutableMap<ExcludedTarget, List<String>> = mutableMapOf()
    private var currentTarget: ExcludedTarget = ExcludedTarget.JS_TS

    private fun projectSettings(): CsleProjectSettings = project.service<CsleProjectSettings>()

    private fun settingsOf(scheme: SettingsScheme): CsleSettings =
        if (scheme == SettingsScheme.PROJECT) projectSettings().scheme else CsleSettings.instance

    private fun selectedScheme(): SettingsScheme =
        schemeComboBox.selectedItem as? SettingsScheme ?: SettingsScheme.IDE

    /** 设置页当前编辑的那一份设置（随 Scheme 下拉框切换） */
    private fun active(): CsleSettings = settingsOf(selectedScheme())

    private fun storedScheme(): SettingsScheme =
        if (projectSettings().enabled) SettingsScheme.PROJECT else SettingsScheme.IDE

    override fun createComponent(): javax.swing.JComponent {
        val longest = options.maxByOrNull { it.length } ?: ""

        inspectComboBox = ComboBox(options).apply {
            setPrototypeDisplayValue(longest)
        }
        quickFixComboBox = ComboBox(options).apply {
            setPrototypeDisplayValue(longest)
        }

        val excludedTargets = ExcludedTarget.entries.toTypedArray()
        val longestTarget = excludedTargets.maxByOrNull { it.toString().length } ?: ExcludedTarget.JS_TS
        targetLanguageComboBox = ComboBox(excludedTargets).apply {
            setPrototypeDisplayValue(longestTarget)
            selectedItem = currentTarget
        }

        excludedField = ChipsInputField()

        literalExpressionCheckBox = JBCheckBox(
            CsleBundle.message("inspect.target.literal.expression"),
            CsleSettings.instance.state.checkLiteralExpression,
        ).apply {
            // 字面量检查是插件的基本功能：始终开启，UI 中只做展示
            isEnabled = false
            isSelected = true
            toolTipText = htmlTooltip(CsleBundle.message("inspect.target.literal.expression.tip"))
        }
        docCommentsCheckBox = JBCheckBox(
            CsleBundle.message("inspect.target.doc.comments"),
            CsleSettings.instance.state.checkDocComments,
        ).apply {
            // 悬停提示同样给出来，说明只有具备文档注释语法的语言才受此开关影响
            toolTipText = htmlTooltip(CsleBundle.message("inspect.target.doc.comments.tip"))
        }

        autoScanCheckBox = JBCheckBox(
            CsleBundle.message("scan.auto.on.open"),
            CsleSettings.instance.state.autoScanOnOpen,
        ).apply {
            toolTipText = htmlTooltip(CsleBundle.message("scan.auto.on.open.tip"))
        }

        schemeComboBox = ComboBox(SettingsScheme.entries.toTypedArray()).apply {
            setPrototypeDisplayValue(SettingsScheme.entries.maxByOrNull { it.toString().length })
        }
        // 与 Code Style 一致：下拉列表按存储位置分组，每组有一个标题
        // 分组标题只在非 Swing 弹窗（ListPopup）里渲染
        schemeComboBox.isSwingPopup = false
        schemeComboBox.renderer = object : GroupedComboBoxRenderer<SettingsScheme>(schemeComboBox) {
            override fun getText(item: SettingsScheme): String = item.toString()

            override fun separatorFor(value: SettingsScheme): ListSeparator =
                ListSeparator(CsleBundle.message(value.groupKey))
        }
        schemeComboBox.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED && !loading) loadFrom(active())
        }
        // 与 Code Style 一致：紧凑的图标按钮，点击弹出菜单
        schemeGearButton = ActionButton(
            object : DumbAwareAction(CsleBundle.message("scheme.label"), null, AllIcons.General.GearPlain) {
                override fun actionPerformed(e: AnActionEvent) = showSchemeMenu(schemeGearButton)
            },
            null,
            ActionPlaces.UNKNOWN,
            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE,
        )

        quickFixComboBox.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED && ::dictionaryStatusLabel.isInitialized) updateDictionaryStatus()
        }

        targetLanguageComboBox.addItemListener { e ->
            if (e.stateChange != ItemEvent.SELECTED) return@addItemListener
            excludedTextByTarget[currentTarget] = excludedField.items
            val selected = targetLanguageComboBox.selectedItem as? ExcludedTarget ?: return@addItemListener
            currentTarget = selected
            excludedField.items = excludedTextByTarget[selected] ?: getExcludedFromState(selected)
            updateExcludedHintLabel()
        }

        dictionarySourceField = JBTextField().apply {
            emptyText.text = CsleBundle.message("custom.dictionary.source")
        }
        dictionaryTargetField = JBTextField().apply {
            emptyText.text = CsleBundle.message("custom.dictionary.target")
        }
        dictionaryAddButton = JButton(CsleBundle.message("custom.dictionary.add")).apply {
            addActionListener { addDictionaryEntry() }
            isEnabled = false
        }
        dictionaryAllButton = JButton().apply {
            addActionListener { openDictionaryDialog() }
        }
        val dictionaryFieldListener = object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                dictionaryAddButton.isEnabled =
                    dictionarySourceField.text.isNotBlank() && dictionaryTargetField.text.isNotBlank()
            }
        }
        dictionarySourceField.document.addDocumentListener(dictionaryFieldListener)
        dictionaryTargetField.document.addDocumentListener(dictionaryFieldListener)

        val root = panel {

            row {
                label(CsleBundle.message("scheme.label"))
                cell(schemeComboBox)
                cell(schemeGearButton)
            }

            row {
                label(CsleBundle.message("inspection.label"))
                cell(inspectComboBox)
                label(CsleBundle.message("quickfix.label"))
                cell(quickFixComboBox)
            }

            group(CsleBundle.message("group.inspect.item")) {
                row {
                    cell(literalExpressionCheckBox)
                }

                // 常驻说明为什么这一项不可改动
                row {
                    comment(CsleBundle.message("inspect.target.literal.expression.hint"))
                }

                row {
                    cell(docCommentsCheckBox)
                }

                // 说明适用范围：只有具备文档注释语法的语言才受上面的开关影响
                row {
                    comment(CsleBundle.message("inspect.target.doc.comments.hint"))
                }
            }

            group(CsleBundle.message("group.excluded.item")) {
                row {
                    label(CsleBundle.message("target.language.label"))
                    cell(targetLanguageComboBox)
                }

                // 整句交给 comment 渲染：跟随面板宽度自动换行，不再手工插入 <br>
                row {
                    excludedHintLabel = comment(excludedHintText(currentTarget)).component
                }

                row {
                    cell(excludedField)
                        .align(Align.FILL)
                        .resizableColumn()
                }
            }

            group(CsleBundle.message("group.scan")) {
                row {
                    cell(autoScanCheckBox)
                }

                row {
                    comment(CsleBundle.message("scan.auto.on.open.hint"))
                }
            }

            group(CsleBundle.message("group.custom.dictionary")) {
                row {
                    comment(CsleBundle.message("custom.dictionary.hint"))
                }
                row {
                    cell(dictionarySourceField).columns(14)
                    cell(dictionaryTargetField).columns(14)
                    cell(dictionaryAddButton)
                    cell(dictionaryAllButton)
                }
                row {
                    dictionaryStatusLabel = comment("").component
                }
            }
        }
        settingsRoot = root
        reset()
        return root
    }

    private fun showSchemeMenu(anchor: JComponent) {
        // 选中 IDE 方案时可以复制到项目，选中项目方案时可以复制到 IDE，另一个动作不显示
        val group = DefaultActionGroup(
            copyAction("scheme.copy.to.project", from = SettingsScheme.IDE, to = SettingsScheme.PROJECT),
            copyAction("scheme.copy.to.ide", from = SettingsScheme.PROJECT, to = SettingsScheme.IDE),
        )
        JBPopupFactory.getInstance()
            .createActionGroupPopup(null, group, DataManager.getInstance().getDataContext(anchor),
                com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.MNEMONICS, true)
            .showUnderneathOf(anchor)
    }

    private fun copyAction(textKey: String, from: SettingsScheme, to: SettingsScheme) =
        object : DumbAwareAction(CsleBundle.message(textKey)) {
            override fun update(e: AnActionEvent) {
                e.presentation.isVisible = selectedScheme() == from
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun actionPerformed(e: AnActionEvent) {
                val answer = Messages.showYesNoDialog(
                    settingsRoot,
                    CsleBundle.message("scheme.copy.confirm", from.toString(), to.toString()),
                    CsleBundle.message(textKey),
                    Messages.getQuestionIcon(),
                )
                if (answer == Messages.YES) copyToScheme(to)
            }
        }

    /** 把当前方案的内容带到另一个方案里；点 Apply 后才真正写入 */
    internal fun copyToScheme(target: SettingsScheme) {
        val source = CsleSettings().also { it.copyFrom(active()) }
        loading = true
        try {
            schemeComboBox.selectedItem = target
        } finally {
            loading = false
        }
        loadFrom(source)
    }

    private fun currentQuickFixGlyph(): CsleGlyphs =
        CsleGlyphs.fromLabel(quickFixComboBox.selectedItem as? String ?: "") ?: CsleGlyphs.SIMPLIFIED

    private fun addDictionaryEntry() {
        val glyph = currentQuickFixGlyph()
        val source = dictionarySourceField.text
        val target = dictionaryTargetField.text
        val error = CsleCustomDictionary.validate(source, target)
        val errorKey = when (error) {
            CsleCustomDictionary.ValidationError.COMMENT_PREFIX -> "custom.dictionary.invalid.comment.entry"
            CsleCustomDictionary.ValidationError.TARGET_HAS_SPACE -> "custom.dictionary.invalid.target.space.entry"
            CsleCustomDictionary.ValidationError.INVALID_CHARACTER -> "custom.dictionary.invalid.character.entry"
            else -> null
        }
        if (errorKey != null) {
            dictionaryStatusLabel.text = CsleBundle.message(errorKey)
            return
        }
        if (error != null) return
        val (text, overwritten) = CsleCustomDictionary.put(pendingDictionaries[glyph.name].orEmpty(), source, target)
        pendingDictionaries[glyph.name] = text
        dictionarySourceField.text = ""
        dictionaryTargetField.text = ""
        updateDictionaryStatus()
        if (overwritten) {
            dictionaryStatusLabel.text = CsleBundle.message("custom.dictionary.overwritten", dictionaryStatusLabel.text)
        }
        // 清空输入框会让“添加”按钮变为不可用，焦点随之丢失；等焦点稳定后再放回原词输入框，方便连续录入
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
            IdeFocusManager.getGlobalInstance().requestFocus(dictionarySourceField, true)
        }
    }

    private fun openDictionaryDialog() {
        val glyph = currentQuickFixGlyph()
        val dialog = CsleCustomDictionaryDialog(settingsRoot, glyph, pendingDictionaries[glyph.name].orEmpty())
        if (!dialog.showAndGet()) return
        if (dialog.resultText.isBlank()) pendingDictionaries.remove(glyph.name)
        else pendingDictionaries[glyph.name] = dialog.resultText
        updateDictionaryStatus()
    }

    private fun normalizedDictionaries(map: Map<String, String>): Map<String, String> =
        map.filterValues { it.isNotBlank() }

    private fun updateDictionaryStatus() {
        val glyph = currentQuickFixGlyph()
        val count = CsleCustomDictionary.parse(pendingDictionaries[glyph.name].orEmpty()).entries.size
        dictionaryStatusLabel.text =
            if (count == 0) CsleBundle.message("custom.dictionary.empty", glyph.label)
            else CsleBundle.message("custom.dictionary.count", glyph.label, count)
        dictionaryAllButton.text = CsleBundle.message("custom.dictionary.all", count)
        // 没有词条时没有可查看的内容，“全部”按钮不显示
        dictionaryAllButton.isVisible = count > 0
    }

    /** tooltip 给定宽度，长文案交给 Swing 的 HTML 渲染自动折行，避免拉成一条超长横条 */
    private fun htmlTooltip(text: String, widthPx: Int = 360): String =
        "<html><body style='width:${widthPx}px'>$text</body></html>"

    /** 拼接成一句完整的话，换行位置交给组件按实际宽度决定 */
    private fun excludedHintText(target: ExcludedTarget): String {
        return listOf(
            CsleBundle.message("excluded.label.part1"),
            CsleBundle.message(excludedLabelPart2Key(target)),
            CsleBundle.message("excluded.label.part3"),
        ).joinToString(" ")
    }

    private fun updateExcludedHintLabel() {
        excludedHintLabel.text = excludedHintText(currentTarget)
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

    private fun getExcludedFromState(target: ExcludedTarget): List<String> = excludedOf(active(), target)

    private fun excludedOf(state: CsleSettings, target: ExcludedTarget): List<String> {
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
        val state = active()
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

    private fun getExcludedItems(target: ExcludedTarget): List<String> {
        if (target == currentTarget) return excludedField.items
        return excludedTextByTarget[target] ?: getExcludedFromState(target)
    }

    override fun isModified(): Boolean {
        val inspect = inspectComboBox.selectedItem as? String ?: ""
        val quickFix = quickFixComboBox.selectedItem as? String ?: ""
        val state = active()
        return selectedScheme() != storedScheme()
                || inspect != state.resolvedInspect()
                || quickFix != state.resolvedQuickFix()
                || ExcludedTarget.entries.any { target ->
            getExcludedFromState(target) != getExcludedItems(target)
        }
                || docCommentsCheckBox.isSelected != state.checkDocComments
                || autoScanCheckBox.isSelected != state.autoScanOnOpen
                || normalizedDictionaries(pendingDictionaries) != normalizedDictionaries(state.customDictionaries)
    }

    override fun apply() {
        val state = active()
        state.inspect = inspectComboBox.selectedItem as? String ?: state.resolvedInspect()
        state.quickFix = quickFixComboBox.selectedItem as? String ?: state.resolvedQuickFix()
        excludedField.commitPending()
        excludedTextByTarget[currentTarget] = excludedField.items
        ExcludedTarget.entries.forEach { target ->
            setExcludedToState(target, getExcludedItems(target))
        }
        state.checkLiteralExpression = true
        state.checkDocComments = docCommentsCheckBox.isSelected
        // 只影响下次打开项目，不需要刷新编辑器中的 inspection
        state.autoScanOnOpen = autoScanCheckBox.isSelected
        state.customDictionaries = normalizedDictionaries(pendingDictionaries).toMutableMap()
        projectSettings().enabled = selectedScheme() == SettingsScheme.PROJECT

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
        loading = true
        try {
            schemeComboBox.selectedItem = storedScheme()
        } finally {
            loading = false
        }
        loadFrom(active())
    }

    /** 用给定的设置填充界面（切换方案、复制到项目、重置时使用） */
    private fun loadFrom(source: CsleSettings) {
        inspectComboBox.selectedItem = source.resolvedInspect()
        quickFixComboBox.selectedItem = source.resolvedQuickFix()
        // 先切下拉框：切换时 ItemListener 会把文本框内容写入"切换前"目标名的缓存，
        // 随后这里重填缓存会覆盖它。若先改 currentTarget，监听器会把未应用的文本
        // 记到默认目标（JS_TS）名下，导致 reset 后显示的仍是编辑中的内容而不是状态里的值
        targetLanguageComboBox.selectedItem = ExcludedTarget.JS_TS
        excludedTextByTarget.clear()
        ExcludedTarget.entries.forEach { target ->
            excludedTextByTarget[target] = excludedOf(source, target)
        }
        currentTarget = ExcludedTarget.JS_TS
        excludedField.items = excludedTextByTarget[currentTarget].orEmpty()
        updateExcludedHintLabel()
        literalExpressionCheckBox.isSelected = true
        docCommentsCheckBox.isSelected = source.checkDocComments
        autoScanCheckBox.isSelected = source.autoScanOnOpen
        pendingDictionaries = source.customDictionaries.toMutableMap()
        updateDictionaryStatus()
    }

    override fun getDisplayName(): String = CsleBundle.message("display.name")
}

/** 设置的生效级别：项目方案存在 .idea 下随项目走，IDE 方案对所有项目生效 */
internal enum class SettingsScheme(val labelKey: String, val groupKey: String) {
    PROJECT("scheme.project", "scheme.group.project"),
    IDE("scheme.ide", "scheme.group.ide");

    override fun toString(): String = CsleBundle.message(labelKey)
}

private enum class ExcludedTarget(val labelKey: String) {
    CSS("target.language.css"),
    DART("target.language.dart"),
    HTML("target.language.html"),
    JAVA("target.language.java"),
    JS_TS("target.language.js_ts"),
    JSON("target.language.json"),
    KOTLIN("target.language.kotlin"),
    PHP("target.language.php"),
    PYTHON("target.language.python"),
    XML("target.language.xml");

    override fun toString(): String = CsleBundle.message(labelKey)
}
