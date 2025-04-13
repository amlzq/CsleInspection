package com.amlzq.csle.inspection

import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.PhpPsiElementFactory
import com.jetbrains.php.lang.psi.elements.PhpEchoStatement
import com.jetbrains.php.lang.psi.elements.PhpPrintExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import org.jetbrains.annotations.NotNull

class CslePhpInspection : CsleLocalInspectionTool() {
    /**
     * 检查字符串是否在用户设置的排除方法中，比如：print
     */
    private fun isSpecialCallExpression(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            debugPrintln("parent=$parent text=${parent.text} isSpecialCallExpression")
            if (parent is PhpPrintExpression && CsleSettings.instance.state.excluded.contains("print")) {
                return true
            } else if (parent is PhpEchoStatement && CsleSettings.instance.state.excluded.contains("echo")) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    @NotNull
    override fun checkFile(
        @NotNull file: PsiFile, @NotNull manager: InspectionManager, isOnTheFly: Boolean
    ): @NotNull Array<ProblemDescriptor>? {
        debugPrintln("checkFile")

        if (!isOnTheFly) return null

        // 判断是否为 PHP 文件
        if (file !is PhpFile) {
            debugPrintln("file is not PhpFile")
            return null
        }

        val virtualFile: VirtualFile? = Utils.getRealVirtualFile(file)
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) return null

        val project: Project = file.project
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return null

        val problems: MutableList<ProblemDescriptor> = ArrayList()

        // 遍历文件中的所有 PSI 元素
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                // 检查是否是 PHP 字符串字面量表达式
                if (element !is StringLiteralExpression) {
                    debugPrintln("element is not StringLiteralExpression")
                    return
                }

                // 是否在特殊方法中
                if (isSpecialCallExpression(element)) {
                    return
                }

                var text: String = element.text
                if (!ZhConverterUtil.containsChinese(text)) {
                    debugPrintln("containsChinese=false")
                    return
                }
                debugPrintln("visit text=$text")

                // 去掉引号，获取实际内容
                if (text.startsWith("\"") || text.startsWith("'")) {
                    text = text.substring(1, text.length - 1)
                }

                // 去掉字母数字空格符号
                text = cleanPattern.matcher(text).replaceAll("")

                val inspect = CsleSettings.instance.state.inspect
                val quickFix = CsleSettings.instance.state.quickFix

                val containsChinese = when (inspect) {
                    CsleGlyphs.SIMPLIFIED.label -> ZhConverterUtil.containsChinese(text)
                    CsleGlyphs.TRADITIONAL.label -> ZhConverterUtil.containsTraditional(text)
                    CsleGlyphs.TAIWAN.label -> ZhTwConverterUtil.containsTraditional(text)
                    else -> ZhConverterUtil.containsChinese(text)
                }
                if (!containsChinese) {
                    debugPrintln("containsChinese again=false")
                    return
                }

                // 有inspect的字，但是转换后是同一个字，也就是简繁共用字的情况，比如：“坪”
                val converted = when (quickFix) {
                    CsleGlyphs.SIMPLIFIED.label -> ZhConverterUtil.toSimple(text)
                    CsleGlyphs.TRADITIONAL.label -> ZhConverterUtil.toTraditional(text)
                    CsleGlyphs.TAIWAN.label -> ZhTwConverterUtil.toTraditional(text)
                    else -> ZhConverterUtil.toSimple(text)
                }
                if (text == converted) {
                    debugPrintln("converted=true")
                    return
                }

                // 创建问题描述，显示黄色波浪线
                problems.add(
                    manager.createProblemDescriptor(
                        element,
                        CsleBundle.message("convert.to.another", quickFix),
                        PhpLocalQuickFix(),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                        isOnTheFly,
                    )
                )
            }
        })
        return problems.toTypedArray<ProblemDescriptor>()
    }
}

class PhpLocalQuickFix : CsleLocalQuickFix() {

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        debugPrintln("psiElement=${descriptor.psiElement}")

        val element = descriptor.psiElement as? StringLiteralExpression ?: return
        val text: String = element.text
        debugPrintln("text=$text")

        // 根据用户配置转换字形
        val converted = when (quickFix) {
            CsleGlyphs.SIMPLIFIED.label -> ZhConverterUtil.toSimple(text)
            CsleGlyphs.TRADITIONAL.label -> ZhConverterUtil.toTraditional(text)
            CsleGlyphs.TAIWAN.label -> ZhTwConverterUtil.toTraditional(text)
            else -> ZhConverterUtil.toSimple(text)
        }
        debugPrintln("converted=$converted")

        // 使用 WriteCommandAction 确保写操作发生在正确的上下文中
        WriteCommandAction.runWriteCommandAction(project) {
            // 将新的字符串应用到代码中
            val newElement = createStringLiteralExpression(project, converted)
            newElement?.let { element.replace(it) }
        }
    }

    private fun createStringLiteralExpression(project: Project, converted: String): PsiElement? {
        if (converted.startsWith("<<<")) {
            return PhpPsiElementFactory.createFromText(project, StringLiteralExpression::class.java, converted)
        } else {
            // 获取原始引号类型（单引号/双引号）
            val singleQuote = converted.startsWith("'")
            val quoteChar = if (singleQuote) '\'' else '"'

            // 处理字符串内容
            val withoutQuote = converted.removeSurrounding(quoteChar.toString())
            debugPrintln("withoutQuote=$withoutQuote")

            return PhpPsiElementFactory.createStringLiteralExpression(project, withoutQuote, singleQuote)
        }
    }
}
