package com.amlzq.csle.inspection.lang

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

/**
 * HTML 内嵌其他语言（`<script>` 的 JavaScript、`<style>` 的 CSS）的检查入口。
 *
 * 内嵌内容由对应语言的解析器在 HTML 文件内直接解析，不是注入片段，
 * 所以实现类要引用 JavaScript / CSS 插件的类。它们分别在 `withJS.xml`、`withCSS.xml`
 * 这两个可选依赖配置里注册，对应插件没装时不会被加载，
 * 本接口与 [CsleMarkupInspection] 则不依赖这两个插件。
 */
interface CsleEmbeddedInspector {

    /** [element] 是否是本实现负责的内嵌内容；是的话 HTML 检查不再继续遍历它的子节点 */
    fun accepts(element: PsiElement): Boolean

    /** 检查 [element] 并把结果加入 [problems] */
    fun collectProblems(
        element: PsiElement,
        manager: InspectionManager,
        isOnTheFly: Boolean,
        problems: MutableList<ProblemDescriptor>
    )

    companion object {
        val EP_NAME: ExtensionPointName<CsleEmbeddedInspector> =
            ExtensionPointName.create("com.amlzq.csle.inspection.embeddedInspector")
    }
}
