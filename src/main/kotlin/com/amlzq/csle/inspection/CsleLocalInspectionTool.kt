package com.amlzq.csle.inspection

import com.amlzq.csle.inspection.settings.CsleGlyphs
import com.amlzq.csle.inspection.util.CsleUtils
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.github.houbb.opencc4j.util.ZhHkConverterUtil
import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import com.amlzq.csle.inspection.util.CsleContext
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiElement
import java.util.regex.Pattern

/**
 * 汉字检查器
 *
 * 在字符串表达式，如果含有汉字则显示警告，并提供简体字/繁体字之间的转换。
 */
abstract class CsleLocalInspectionTool : LocalInspectionTool() {

    /**
     * 匹配字符串中的英文字母、数字、标点、空格，以及中文的标点符号
     *
     * \\p{Punct} 匹配所有的标点符号字符
     * \\s 匹配任何空白字符
     */
    val cleanPattern: Pattern =
        Pattern.compile("[A-Za-z0-9\\p{Punct}\\s\u3000\u2014\u2018\u2019\u201C\u201D\uFF08\uFF09\uFF1B\uFF1A\uFF1F\uFF01\u3001\uFF0C\u3002]")

    /**
     * 检查入口：记录当前项目后交给子类的 [doCheckFile]，让设置按项目方案或 IDE 方案读取。
     */
    final override fun checkFile(
        file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean
    ): Array<ProblemDescriptor>? = CsleContext.with(file.project) { doCheckFile(file, manager, isOnTheFly) }

    abstract fun doCheckFile(
        file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean
    ): Array<ProblemDescriptor>?

    abstract fun inExcludedCallExpression(element: PsiElement): Boolean

    fun containsChinese(text: String): Boolean {
        return when (CsleUtils.getInspectGlyphs()) {
            CsleGlyphs.SIMPLIFIED -> ZhConverterUtil.containsSimple(text)
            CsleGlyphs.TAIWAN -> ZhTwConverterUtil.containsTraditional(text)
            CsleGlyphs.HONGKONG -> ZhHkConverterUtil.containsTraditional(text)
            CsleGlyphs.TRADITIONAL -> ZhConverterUtil.containsTraditional(text)
        }
    }

    fun getConvertedText(text: String): String {
        return CsleUtils.getConvertedText(text)
    }

    /**
     * 创建问题描述：除了转换修复，再附带“添加到自定义词典”修复（原词为被标出的字符串内容）。
     */
    fun InspectionManager.createCsleDescriptor(
        element: PsiElement,
        description: String,
        fix: LocalQuickFix,
        highlightType: ProblemHighlightType,
        isOnTheFly: Boolean,
    ): ProblemDescriptor = createProblemDescriptor(
        element,
        description,
        isOnTheFly,
        arrayOf(fix, CsleAddToDictionaryQuickFix(CsleAddToDictionaryQuickFix.sourceOf(element.text))),
        highlightType,
    )
}