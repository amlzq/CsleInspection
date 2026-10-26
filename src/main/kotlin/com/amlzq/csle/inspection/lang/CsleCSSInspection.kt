package com.amlzq.csle.inspection.lang

import com.amlzq.csle.inspection.CsleLocalInspectionTool
import com.amlzq.csle.inspection.CsleLocalQuickFix
import com.amlzq.csle.inspection.settings.CsleSettings
import com.amlzq.csle.inspection.util.CsleUtils
import com.amlzq.csle.inspection.util.debugPrintln
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.css.CssDeclaration
import com.intellij.psi.css.CssString
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NotNull

/**
 * 汉字检查器：CSS。
 *
 * 检查字符串值（如 content、font-family）中的汉字；
 * 排除列表按 CSS 属性名配置，自定义属性可用 -- 前缀。
 */
class CsleCSSInspection : CsleLocalInspectionTool() {
    
    override fun inExcludedCallExpression(element: PsiElement): Boolean {
        val excluded = CsleSettings.instance.state.excludedCss
        if (excluded.isEmpty()) return false

        val declaration = PsiTreeUtil.getParentOfType(element, CssDeclaration::class.java, false) ?: return false
        val declarationText = declaration.text
        val colonIndex = declarationText.indexOf(':')
        if (colonIndex <= 0) return false

        val propertyName = declarationText.substring(0, colonIndex).trim()
        if (propertyName.isEmpty()) return false

        val propertyNameNormalized = normalizeCssPropertyName(propertyName)
        for (raw in excluded) {
            val excludedName = normalizeCssPropertyName(raw)
            if (excludedName.isEmpty()) continue
            if (propertyNameNormalized == excludedName) return true
            if (propertyNameNormalized.removePrefix("--") == excludedName.removePrefix("--")) return true
        }
        return false
    }

    private fun normalizeCssPropertyName(name: String): String = name.trim().lowercase()

    @NotNull
    override fun checkFile(
        @NotNull file: PsiFile, @NotNull manager: InspectionManager, isOnTheFly: Boolean
    ): @NotNull Array<ProblemDescriptor>? {

        val languageId = file.language.id.lowercase()
        if (languageId != "css" && languageId != "scss" && languageId != "less") {
            debugPrintln("file is not CSS/SCSS/LESS language, languageId=$languageId")
            return null
        }

        val virtualFile: VirtualFile? = CsleUtils.getRealVirtualFile(file)
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) return null

        val project: Project = file.project
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return null

        val problems: MutableList<ProblemDescriptor> = ArrayList()

        if (!CsleSettings.instance.state.checkLiteralExpression) return null

        // 遍历文件中的所有 PSI 元素
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                if (element !is CssString) return
                checkString(element, manager, isOnTheFly)?.let(problems::add)
            }
        })
        return problems.toTypedArray<ProblemDescriptor>()
    }

    /**
     * 检查单个 CSS 字符串。
     *
     * 同时供 HTML 检查器复用：HTML 中 `<style>` 的内容由 CSS 解析器在 HTML 文件内
     * 直接解析成 [CssString]（不是注入片段），注册为 CSS 语言的检查器收不到 HTML 文件，
     * 只能由 [CsleEmbeddedCSSInspection] 在这里调用。
     */
    internal fun checkString(
        element: CssString,
        manager: InspectionManager,
        isOnTheFly: Boolean
    ): ProblemDescriptor? {
        if (inExcludedCallExpression(element)) return null

        val text = element.text
        if (!containsChinese(text)) {
            debugPrintln("containsChinese=false")
            return null
        }

        debugPrintln("visit CSS text=$text")

        val innerText = text.removeSurrounding("\"").removeSurrounding("'")

        // 清理符号
        val cleanText = cleanPattern.matcher(innerText).replaceAll("")
        if (!containsChinese(cleanText)) {
            debugPrintln("containsChinese again=false")
            return null
        }

        val cleanConverted = getConvertedText(cleanText)
        if (cleanText == cleanConverted) {
            debugPrintln("converted=true")
            return null
        }

        val converted = getConvertedText(text)
        if (text == converted) return null

        // 创建问题描述，显示黄色波浪线
        return manager.createProblemDescriptor(
            element,
            CsleUtils.problemDescription(),
            CssStringQuickFix(),
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
            isOnTheFly,
        )
    }
}

class CssStringQuickFix : CsleLocalQuickFix() {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? CssString ?: return
        val text = element.text
        val newText = getConvertedText(text)

        if (text == newText) return

        WriteCommandAction.runWriteCommandAction(project) {
            // 按文本范围就地替换：内嵌样式表的 CssString 位于 HTML 文件中，
            // 用其它文件构造的 CssString 去 replace 会破坏文档结构（只剩半截内容）
            val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
                ?: return@runWriteCommandAction
            val range = element.textRange
            document.replaceString(range.startOffset, range.endOffset, newText)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}
