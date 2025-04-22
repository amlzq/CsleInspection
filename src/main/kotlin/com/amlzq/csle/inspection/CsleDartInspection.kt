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
import com.jetbrains.lang.dart.ide.actions.DartPubActionBase
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression
import com.jetbrains.lang.dart.util.DartElementGenerator
import org.jetbrains.annotations.NotNull

class CsleDartInspection : CsleLocalInspectionTool() {

    /**
     * 检查字符表达式是否在用户设置的排除方法中，比如：print、dev.log
     */
    private fun isSpecialCallExpression(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            if (parent is DartCallExpression) {
                val text = parent.expression!!.text
                for (functionName in CsleSettings.instance.state.excluded) {
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

        val virtualFile: VirtualFile? = Utils.getRealVirtualFile(file)
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) return null

        val project: Project = file.getProject()
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return null

        val problems: MutableList<ProblemDescriptor> = ArrayList()

        // 遍历文件中的所有 PSI 元素
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                // 检查是否是 Dart 字符串字面量表达式
                if (element !is DartStringLiteralExpression) return

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
                        DartLocalQuickFix(),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                        isOnTheFly,
                    )
                )
            }
        })
        return problems.toTypedArray<ProblemDescriptor>()
    }
}

class DartLocalQuickFix : CsleLocalQuickFix() {

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? DartStringLiteralExpression ?: return
        val text: String = element.text

        // 将简体中文转换为繁体中文
        val converted = when (quickFix) {
            CsleGlyphs.SIMPLIFIED.label -> ZhConverterUtil.toSimple(text)
            CsleGlyphs.TRADITIONAL.label -> ZhConverterUtil.toTraditional(text)
            CsleGlyphs.TAIWAN.label -> ZhTwConverterUtil.toTraditional(text)
            else -> ZhConverterUtil.toSimple(text)
        }

        // 使用 WriteCommandAction 确保写操作发生在正确的上下文中
        WriteCommandAction.runWriteCommandAction(project) {
            // 将新的繁体字符串应用到代码中
            val newText = converted
            val newElement = DartElementGenerator.createExpressionFromText(project, newText)
            newElement?.let { element.replace(it) }
        }
    }
}
