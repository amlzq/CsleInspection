package com.amlzq.csle.inspection

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
import com.jetbrains.python.psi.*
import org.jetbrains.annotations.NotNull

/**
 * 汉字检查器：Python。
 *
 * 检查字符串字面量中的汉字；排除列表按函数名配置（如 print）。
 *
 * Python 没有其他语言那种文档注释语法：PEP 257 的文档字符串（docstring，通常写作
 * 三引号 `"""..."""` 或 `'''...'''`）在 PSI 里就是 [PyStringLiteralExpression]，
 * 因此它由“字符串字面量”开关控制，不受“文档注释”开关影响。
 */
class CslePythonInspection : CsleLocalInspectionTool() {
    /**
     * 检查字符串是否在用户设置的排除方法中，比如：print
     */
    override fun inExcludedCallExpression(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            if (parent is PyCallExpression) {
                val text = (parent.callee as? PyReferenceExpression)?.referencedName ?: break
                for (functionName in CsleSettings.instance.state.excludedPython) {
                    if (functionName == text || text.contains(".$functionName")) {
                        debugPrintln("$text is on the exclusion list.")
                        return true
                    }
                }
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

        // 判断是否为 Python 文件
        if (file !is PyFile) {
            debugPrintln("file is not PyFile")
            return null
        }

        val virtualFile: VirtualFile? = CsleUtils.getRealVirtualFile(file)
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) return null

        val project: Project = file.project
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return null

        val problems: MutableList<ProblemDescriptor> = ArrayList()

        // 遍历文件中的所有 PSI 元素
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                debugPrintln("text=${element.text}")

                if (!CsleSettings.instance.state.checkLiteralExpression) return

                // 检查是否是 Python 字符串字面量表达式
                if (element !is PyStringLiteralExpression) {
                    debugPrintln("element is not PyStringLiteralExpression")
                    return
                }

                // 是否在特殊方法中
                if (inExcludedCallExpression(element)) {
                    return
                }

                var text: String = element.text
                if (!containsChinese(text)) {
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
                if (!containsChinese(text)) {
                    debugPrintln("containsChinese again=false")
                    return
                }

                // 有inspect的字，但是转换后是同一个字，也就是简繁共用字的情况，比如：“坪”
                val converted = getConvertedText(text)
                if (text == converted) {
                    debugPrintln("converted=true")
                    return
                }

                // 创建问题描述，显示黄色波浪线
                problems.add(
                    manager.createProblemDescriptor(
                        element,
                         CsleUtils.problemDescription(),
                        PythonLiteralExpressionQuickFix(),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                        isOnTheFly,
                    )
                )
            }
        })
        return problems.toTypedArray<ProblemDescriptor>()
    }
}

class PythonLiteralExpressionQuickFix : CsleLocalQuickFix() {

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? PyStringLiteralExpression ?: return

        // 在原文上转换：保留前缀（r/b/f）与三引号（文档字符串）等原始写法
        val newText = getConvertedText(element.text)
        if (newText == element.text) return

        WriteCommandAction.runWriteCommandAction(project) {
            val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
                ?: return@runWriteCommandAction
            val range = element.textRange
            document.replaceString(range.startOffset, range.endOffset, newText)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}
