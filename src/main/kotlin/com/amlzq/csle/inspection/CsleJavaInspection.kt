package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import org.jetbrains.annotations.NotNull

class CsleJavaInspection : CsleLocalInspectionTool() {

    /**
     * 检查字符表达式是否在用户设置的排除方法中，比如：print
     */
    override fun inExcludedCallExpression(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            if (parent is PsiMethodCallExpression) {
                val text = parent.methodExpression.referenceName ?: continue
                debugPrintln("text=$text isSpecialCallExpression")
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

        if (file !is PsiJavaFile) return null

        val virtualFile: VirtualFile? = CsleUtils.getRealVirtualFile(file)
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) return null

        val project: Project = file.getProject()
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return null

        val problems: MutableList<ProblemDescriptor> = ArrayList()

        // 遍历文件中的所有 PSI 元素
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                // 检查是否是 Java 字符串字面量表达式
                if (element !is PsiLiteralExpression) return

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
                        CsleBundle.message("convert.to.another", CsleUtils.getQuickFix()),
                        JavaLocalQuickFix(),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                        isOnTheFly,
                    )
                )
            }
        })
        return problems.toTypedArray<ProblemDescriptor>()
    }
}

class JavaLocalQuickFix : CsleLocalQuickFix() {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // 使用 WriteCommandAction 确保写操作发生在正确的上下文中
        WriteCommandAction.runWriteCommandAction(project) {
            // 将新字形的字符串应用到代码中
            val element = descriptor.psiElement
            if (element is PsiLiteralExpression) {
                val text = (element.value as String)
                val newText = getConvertedText(text)
                val newElement =
                    JavaPsiFacade.getElementFactory(project).createExpressionFromText("\"$newText\"", element.context)
//        val newElement = PsiElementFactory.getInstance(project).createExpressionFromText(newText, element.context)
                element.replace(newElement)
            }
        }
    }
}
