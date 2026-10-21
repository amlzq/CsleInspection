package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.jetbrains.lang.dart.ide.actions.DartPubActionBase
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.jetbrains.lang.dart.psi.DartDocComment
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression
import com.jetbrains.lang.dart.util.DartElementGenerator
import org.jetbrains.annotations.NotNull

/**
 * 汉字检查器：Dart。
 *
 * 字符串字面量始终参与检查；文档注释是否参与检查由设置中的
 * "Doc comments" 开关控制。排除列表按方法名配置（如 print）。
 * pub 命令执行期间跳过检查，避免误报。
 */
class CsleDartInspection : CsleLocalInspectionTool() {

    /**
     * 检查字符表达式是否在用户设置的排除方法中，比如：print、dev.log
     */
    override fun inExcludedCallExpression(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            if (parent is DartCallExpression) {
                val text = parent.expression!!.text
                for (functionName in CsleSettings.instance.state.excludedDart) {
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
        if (!isOnTheFly) return null

        if (file !is DartFile) return null

        if (DartPubActionBase.isInProgress) return null

        val virtualFile: VirtualFile? = CsleUtils.getRealVirtualFile(file)
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) return null

        val project: Project = file.getProject()
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return null

        val problems: MutableList<ProblemDescriptor> = ArrayList()

        // 遍历文件中的所有 PSI 元素
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                if (CsleSettings.instance.state.checkDocComments && isDocComment(element)) {
                    val text = element.text
                    if (!containsChinese(text)) {
                        return
                    }

                    val converted = getConvertedText(text)
                    if (text == converted) {
                        return
                    }

                    problems.add(
                        manager.createProblemDescriptor(
                            element,
                            CsleUtils.problemDescription(),
                            DartDocCommentQuickFix(),
                            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                            isOnTheFly,
                        )
                    )
                    return
                }

                if (!CsleSettings.instance.state.checkLiteralExpression) return

                // 检查是否是 Dart 字符串字面量表达式
                if (element !is DartStringLiteralExpression) return

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
                        DartLiteralExpressionQuickFix(),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                        isOnTheFly,
                    )
                )
            }
        })
        return problems.toTypedArray<ProblemDescriptor>()
    }

    /**
     * 是否为文档注释。
     *
     * `/** ... */` 被解析为 [DartDocComment]；Dart 常用的 `///` 只是普通 [PsiComment]，
     * 需要按前缀单独识别，否则最常见的文档注释写法不会被检查。
     */
    private fun isDocComment(element: PsiElement): Boolean =
        element is DartDocComment || (element is PsiComment && element.text.startsWith("///"))
}

class DartLiteralExpressionQuickFix : CsleLocalQuickFix() {

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? DartStringLiteralExpression ?: return
        val text: String = element.text

        // 将简体中文转换为繁体中文
        val newText = getConvertedText(text)

        // 使用 WriteCommandAction 确保写操作发生在正确的上下文中
        WriteCommandAction.runWriteCommandAction(project) {
            // 将新的繁体字符串应用到代码中
            val newElement = DartElementGenerator.createExpressionFromText(project, newText)
            newElement?.let { element.replace(it) }
        }
    }
}

class DartDocCommentQuickFix : CsleLocalQuickFix() {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val newText = getConvertedText(element.text)
        if (newText == element.text) return

        WriteCommandAction.runWriteCommandAction(project) {
            // 文档注释不是表达式，DartElementGenerator 解析不了，按文本范围替换
            val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
                ?: return@runWriteCommandAction
            val range = element.textRange
            document.replaceString(range.startOffset, range.endOffset, newText)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}
