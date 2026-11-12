package com.amlzq.csle.inspection.settings

import com.amlzq.csle.inspection.util.CsleBundle
import com.amlzq.csle.inspection.util.CsleCustomDictionary
import com.amlzq.csle.inspection.util.CsleUtils
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

/**
 * “添加到自定义词典”的对话框：原词（from）和目标词（to）各一个输入框。
 *
 * 词条写入当前生效的设置层级（启用项目方案时是项目方案，否则是 IDE 方案）里
 * “快速修复目标字形”对应的词典；原词已存在时预填现有目标词，保存即覆盖。
 */
class CsleAddToDictionaryDialog(
    private val project: Project,
    private val glyph: CsleGlyphs,
    source: String,
    target: String,
) : DialogWrapper(project, true) {

    private val sourceField = JBTextField(source, 28)
    private val targetField = JBTextField(target, 28)

    val source: String get() = sourceField.text.trim()
    val target: String get() = targetField.text.trim()

    init {
        title = CsleBundle.message("add.to.dictionary.title", glyph.label)
        init()
    }

    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent(CsleBundle.message("custom.dictionary.source"), sourceField)
        .addLabeledComponent(CsleBundle.message("custom.dictionary.target"), targetField)
        .addComponent(
            JBLabel(CsleBundle.message("add.to.dictionary.note", glyph.label)).apply {
                foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
                border = JBUI.Borders.emptyTop(4)
            },
        )
        .panel

    override fun getPreferredFocusedComponent(): JComponent =
        if (sourceField.text.isBlank()) sourceField else targetField

    override fun doValidate(): ValidationInfo? {
        val error = CsleCustomDictionary.validate(source, target) ?: return null
        return when (error) {
            CsleCustomDictionary.ValidationError.EMPTY_SOURCE ->
                ValidationInfo(CsleBundle.message("add.to.dictionary.source.required"), sourceField)
            CsleCustomDictionary.ValidationError.EMPTY_TARGET ->
                ValidationInfo(CsleBundle.message("add.to.dictionary.target.required"), targetField)
            CsleCustomDictionary.ValidationError.COMMENT_PREFIX ->
                ValidationInfo(CsleBundle.message("custom.dictionary.invalid.comment.entry"), sourceField)
            CsleCustomDictionary.ValidationError.INVALID_CHARACTER ->
                ValidationInfo(CsleBundle.message("custom.dictionary.invalid.character.entry"), sourceField)
            CsleCustomDictionary.ValidationError.TARGET_HAS_SPACE ->
                ValidationInfo(CsleBundle.message("custom.dictionary.invalid.target.space.entry"), targetField)
        }
    }

    companion object {
        /**
         * 弹出对话框并保存词条；返回是否保存了词条。
         * [rawSource] 是用户选中的文字或被标出的字符串内容，作为原词预填。
         */
        fun addEntry(project: Project, rawSource: String): Boolean {
            val settings = CsleSettings.effective(project)
            val glyph = CsleUtils.getQuickFixGlyphs(project)
            val source = rawSource.trim()
            val dictionaryText = settings.customDictionaryFor(glyph)
            val existing = CsleCustomDictionary.parse(dictionaryText).entries[source]
            val target = existing ?: CsleUtils.getConvertedText(source, project)
            val dialog = CsleAddToDictionaryDialog(project, glyph, source, target)
            if (!dialog.showAndGet()) return false
            // 对话框打开期间设置可能被改过，保存时重新读取
            val latest = settings.customDictionaryFor(glyph)
            settings.customDictionaries[glyph.name] = CsleCustomDictionary.put(latest, dialog.source, dialog.target).first
            // 词条会影响转换结果，让编辑器重新分析
            DaemonCodeAnalyzer.getInstance(project).restart("CsleAddToDictionaryDialog.save")
            return true
        }
    }
}
