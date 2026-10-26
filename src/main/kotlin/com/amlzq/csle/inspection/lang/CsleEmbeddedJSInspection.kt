package com.amlzq.csle.inspection.lang

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.javascript.psi.JSEmbeddedContent
import com.intellij.psi.PsiElement

/**
 * HTML 内嵌 `<script>` 代码块的检查入口。
 *
 * HTML 中 `<script>` 的内容由 JavaScript 解析器在 HTML 文件内直接解析成 [JSEmbeddedContent]，
 * 它不是注入片段，HTML 文件也不会交给注册为 JavaScript 语言的 [CsleJSInspection]，
 * 所以脚本中的字符串字面量一直不会被检查、也没有快速修复。
 *
 * 这里由注册为 HTML 语言的 [CsleHTMLInspection] 复用 [CsleJSInspection] 的检查逻辑，
 * 使内嵌脚本与独立的 JS 文件表现一致（包括 JS 排除列表）。
 *
 * 本对象引用了 JavaScript 插件的类，调用方必须先确认 JavaScript 插件可用
 * （见 [CsleMarkupInspection]），否则没装插件时会加载失败。
 */
internal object CsleEmbeddedJSInspection {

    /** 是否内嵌脚本代码块 */
    fun isEmbeddedContent(element: PsiElement): Boolean = element is JSEmbeddedContent

    /** 检查内嵌脚本代码块中的字符串字面量 */
    fun collectProblems(
        element: PsiElement,
        manager: InspectionManager,
        isOnTheFly: Boolean,
        problems: MutableList<ProblemDescriptor>
    ) {
        CsleJSInspection().collectProblems(element, manager, isOnTheFly, problems)
    }
}
