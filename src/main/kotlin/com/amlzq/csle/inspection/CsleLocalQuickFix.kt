package com.amlzq.csle.inspection

import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiLiteralExpression
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
        val element = when (descriptor.psiElement) {
            is PsiLiteralExpression -> descriptor.psiElement
            is KtStringTemplateExpression -> descriptor.psiElement
            else -> null
        }
        debugPrintln("element=${element.toString()} applyFix")
        if (element == null) return
        val text: String = when (element) {
            is PsiLiteralExpression -> element.value as String
            is KtStringTemplateExpression -> element.entries.joinToString("") { it.text }
            else -> ""
        }
        if (text.isEmpty()) return
        debugPrintln("text=$text applyFix")

        // 去掉引号
//            if (text.startsWith("\"") || text.startsWith("'")) {
//                text = text.substring(1, text.length - 1)
//            }

        // 将简体中文转换为繁体中文
        val converted = when (quickFix) {
            CsleMode.SIMPLIFIED.label -> ZhConverterUtil.toSimple(text)
            CsleMode.TRADITIONAL.label -> ZhConverterUtil.toTraditional(text)
            CsleMode.TAIWAN.label -> ZhTwConverterUtil.toTraditional(text)
            else -> ZhConverterUtil.toSimple(text)
        }

        // 使用 WriteCommandAction 确保写操作发生在正确的上下文中
        WriteCommandAction.runWriteCommandAction(project) {
            // 将新的繁体字符串应用到代码中
            val newText = "\"" + converted + "\"" // 使用双引号包裹
            val factory = PsiElementFactory.getInstance(project)
            val newElement = factory.createExpressionFromText(newText, element.context)
            element.replace(newElement)
        }
    }
}