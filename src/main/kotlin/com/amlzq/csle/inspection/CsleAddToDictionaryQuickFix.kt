package com.amlzq.csle.inspection

import com.amlzq.csle.inspection.settings.CsleAddToDictionaryDialog
import com.amlzq.csle.inspection.util.CsleBundle
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project

/**
 * 快速修复：把被标出的文字添加到自定义词典。
 *
 * 不继承 [CsleLocalQuickFix]：批量修复只取第一个 [CsleLocalQuickFix]，这个修复需要用户交互，不参与批量。
 */
class CsleAddToDictionaryQuickFix(private val source: String) : LocalQuickFix, PriorityAction {
    // 低优先级：始终排在“转换为…”之后，不依赖数组顺序
    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.LOW

    override fun getName(): String = CsleBundle.message("add.to.dictionary.fix")
    override fun getFamilyName(): String = name

    // 要弹对话框，不能在写操作里执行
    override fun startInWriteAction(): Boolean = false

    // 预览会在文件副本上执行修复，这里不应该弹窗
    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        CsleAddToDictionaryDialog.addEntry(project, source)
    }

    companion object {
        private val QUOTES = listOf("\"\"\"", "'''", "\"", "'", "`")

        /** 去掉字符串字面量两侧的引号，得到可作为原词的内容 */
        fun sourceOf(elementText: String): String {
            val text = elementText.trim()
            for (q in QUOTES) {
                if (text.length >= q.length * 2 && text.startsWith(q) && text.endsWith(q)) {
                    return text.substring(q.length, text.length - q.length)
                }
            }
            return text
        }
    }
}
