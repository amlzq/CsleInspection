package com.amlzq.csle.inspection.lang

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.css.CssString
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue

/**
 * HTML 内嵌样式表的检查入口（CSS 插件可用时由 `withCSS.xml` 注册）。
 *
 * HTML 中 `<style>` 的内容由 CSS 解析器在 HTML 文件内直接解析成 [CssString] 等节点，
 * 它们不是注入片段，HTML 文件也不会交给注册为 CSS 语言的 [CsleCSSInspection]，
 * 所以样式表里的字符串一直不会被检查、也没有快速修复。
 *
 * 这里由注册为 HTML 语言的 [CsleHTMLInspection] 复用 [CsleCSSInspection] 的检查逻辑，
 * 使内嵌样式表与独立的 CSS 文件表现一致（包括 CSS 排除列表）。
 *
 * `style="..."` 内联样式同样会解析出 [CssString]，但标记语言的属性值检查已经覆盖，
 * 这里跳过，避免同一段文本被重复报告。
 *
 * 本类引用了 CSS 插件的类，只在 `withCSS.xml` 里注册，没装插件时不会被加载。
 */
class CsleEmbeddedCSSInspection : CsleEmbeddedInspector {

    /** 是否是内嵌样式表中、且不属于内联 `style` 属性值的字符串 */
    override fun accepts(element: PsiElement): Boolean {
        if (element !is CssString) return false
        return PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false) == null
    }

    /** 检查内嵌样式表中的字符串 */
    override fun collectProblems(
        element: PsiElement,
        manager: InspectionManager,
        isOnTheFly: Boolean,
        problems: MutableList<ProblemDescriptor>
    ) {
        val cssString = element as? CssString ?: return
        CsleCSSInspection().checkString(cssString, manager, isOnTheFly)?.let(problems::add)
    }
}
