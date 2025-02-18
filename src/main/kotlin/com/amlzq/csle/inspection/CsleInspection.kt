package com.amlzq.csle.inspection

import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression
import org.jetbrains.annotations.NotNull
import java.util.regex.Pattern

/**
 * 汉字检查器
 *
 * 在字符串表达式，如果含有汉字则显示警告，并提供简体字/繁体字之间的转换。
 */
class CsleInspection : LocalInspectionTool() {

    /**
     * 匹配字符串中的英文字母、数字、标点、空格，以及中文的标点符号
     *
     * \\p{Punct} 匹配所有的标点符号字符
     * \\s 匹配任何空白字符
     */
    private val cleanPattern =
        Pattern.compile("[A-Za-z0-9\\p{Punct}\\s\u3000\u2014\u2018\u2019\u201C\u201D\uFF08\uFF09\uFF1B\uFF1A\uFF1F\uFF01\u3001\uFF0C\u3002]")


    @NotNull
    override fun checkFile(
        @NotNull file: PsiFile, @NotNull manager: InspectionManager, isOnTheFly: Boolean
    ): @NotNull Array<ProblemDescriptor>? {
        if (!isOnTheFly) return null

        if (file !is DartFile) return null

        if (Utils.isPubActionInProgress()) return null

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
                if (isSpecialCallExpression(element)) return

                var text: String = element.text

                val inspect = CsleSettings.instance.state.inspect
                val quickFix = CsleSettings.instance.state.quickFix

                var containsChinese = when (inspect) {
                    CsleMode.SIMPLIFIED.label -> ZhConverterUtil.containsChinese(text)
                    CsleMode.TRADITIONAL.label -> ZhConverterUtil.containsChinese(text)
                    CsleMode.TAIWAN.label -> ZhTwConverterUtil.containsChinese(text)
                    else -> ZhConverterUtil.containsChinese(text)
                }
                if (!containsChinese) return

                // 去掉引号，获取实际内容
                if (text.startsWith("\"") || text.startsWith("'")) {
                    text = text.substring(1, text.length - 1)
                }

                // 去掉字母数字空格符号
                text = cleanPattern.matcher(text).replaceAll("")

                containsChinese = when (inspect) {
                    CsleMode.SIMPLIFIED.label -> ZhConverterUtil.containsChinese(text)
                    CsleMode.TRADITIONAL.label -> ZhConverterUtil.containsChinese(text)
                    CsleMode.TAIWAN.label -> ZhTwConverterUtil.containsChinese(text)
                    else -> ZhConverterUtil.containsChinese(text)
                }
                if (!containsChinese) return

                // 是否表达式中全部的字都是quickFix
                val already = when (quickFix) {
                    CsleMode.SIMPLIFIED.label -> ZhConverterUtil.isSimple(text)
                    CsleMode.TRADITIONAL.label -> ZhConverterUtil.isTraditional(text)
                    CsleMode.TAIWAN.label -> ZhTwConverterUtil.isTraditional(text)
                    else -> ZhConverterUtil.isSimple(text)
                }
                if (already) return

                // 有inspect的字，但是转换后是同一个字，也就是简繁共用字的情况，比如：“坪”
                val converted = when (quickFix) {
                    CsleMode.SIMPLIFIED.label -> ZhConverterUtil.toSimple(text)
                    CsleMode.TRADITIONAL.label -> ZhConverterUtil.toTraditional(text)
                    CsleMode.TAIWAN.label -> ZhTwConverterUtil.toTraditional(text)
                    else -> ZhConverterUtil.toSimple(text)
                }
                if (text == converted) return

                // 创建问题描述，显示黄色波浪线
                problems.add(
                    manager.createProblemDescriptor(
                        element,
                        CsleBundle.message("convert.to.another.chinese", quickFix),
                        ConvertToAnotherFix(),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                        isOnTheFly,
                    )
                )
            }
        })

        return problems.toTypedArray<ProblemDescriptor>()
    }

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
                        return true
                    }
                }
            }
            parent = parent.parent
        }
        return false
    }
}