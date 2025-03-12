package com.amlzq.csle.inspection

import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiLiteralExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class CsleLocalQuickFix : LocalQuickFix {
    private val quickFix: String = CsleSettings.instance.state.quickFix

    override fun getName(): String {
        return CsleBundle.message("convert.to.another", quickFix)
    }

    override fun getFamilyName(): String {
        return name
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // 使用 WriteCommandAction 确保写操作发生在正确的上下文中
        WriteCommandAction.runWriteCommandAction(project) {
            // 将新字形的字符串应用到代码中
            when (val element = descriptor.psiElement) {
                // 处理 Java 字符串
                is PsiLiteralExpression -> handleJavaString(element, project)
                // 处理 Kotlin 字符串
                is KtStringTemplateExpression -> handleKotlinString(element, project)
            }
        }
    }

    private fun getConvertedText(text: String): String {
        return when (quickFix) {
            CsleGlyphs.SIMPLIFIED.label -> ZhConverterUtil.toSimple(text)
            CsleGlyphs.TRADITIONAL.label -> ZhConverterUtil.toTraditional(text)
            CsleGlyphs.TAIWAN.label -> ZhTwConverterUtil.toTraditional(text)
            else -> ZhConverterUtil.toSimple(text)
        }
    }

    private fun handleJavaString(element: PsiLiteralExpression, project: Project) {
        val text = (element.value as String)
        val newText = getConvertedText(text)
        val newElement =
            JavaPsiFacade.getElementFactory(project).createExpressionFromText("\"$newText\"", element.context)
//        val newElement = PsiElementFactory.getInstance(project).createExpressionFromText(newText, element.context)
        element.replace(newElement)
    }

    private fun handleKotlinString(element: KtStringTemplateExpression, project: Project) {
        val isRaw = element.text.startsWith("\"\"\"")
        val entries = element.entries
        val newText = buildString {
            if (isRaw) append("\"\"\"") else append("\"")
            entries.forEach { entry ->
                when (entry) {
                    is KtLiteralStringTemplateEntry -> append(getConvertedText(entry.text))
                    else -> append(entry.text)
                }
            }
            if (isRaw) append("\"\"\"") else append("\"")
        }
        val newElement = KtPsiFactory(project).createExpression(newText)
        element.replace(newElement)
    }
}