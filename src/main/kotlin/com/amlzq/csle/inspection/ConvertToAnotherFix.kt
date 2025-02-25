package com.amlzq.csle.inspection

import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementFactory
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression

class ConvertToAnotherFix : LocalQuickFix {
    private val quickFix = CsleSettings.instance.state.quickFix

    override fun getName(): String {
        return CsleBundle.message("convert.to.another.chinese", quickFix)
    }

    override fun getFamilyName(): String {
        return name
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? DartStringLiteralExpression ?: return
        val text: String = element.text

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
            val newText = converted // "\"" + converted + "\"" // 使用双引号包裹
            val factory = PsiElementFactory.getInstance(project)
            val newElement = factory.createExpressionFromText(newText, element.context)
            element.replace(newElement)
        }
    }
}