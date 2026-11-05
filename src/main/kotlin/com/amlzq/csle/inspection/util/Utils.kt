package com.amlzq.csle.inspection.util

import com.amlzq.csle.inspection.settings.CsleGlyphs
import com.amlzq.csle.inspection.settings.CsleSettings
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.github.houbb.opencc4j.util.ZhHkConverterUtil
import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

object CsleUtils {
    fun getRealVirtualFile(psiFile: PsiFile?): VirtualFile? {
        return psiFile?.originalFile?.virtualFile
    }

    fun getInspect(project: Project? = CsleContext.project()): String {
        return CsleSettings.effective(project).resolvedInspect()
    }

    fun getInspectGlyphs(project: Project? = CsleContext.project()): CsleGlyphs {
        val label: String = getInspect(project)
        return CsleGlyphs.fromLabel(label) ?: CsleGlyphs.SIMPLIFIED
    }

    fun getQuickFix(project: Project? = CsleContext.project()): String {
        return CsleSettings.effective(project).resolvedQuickFix()
    }

    fun getQuickFixGlyphs(project: Project? = CsleContext.project()): CsleGlyphs {
        val label: String = getQuickFix(project)
        return CsleGlyphs.fromLabel(label) ?: CsleGlyphs.SIMPLIFIED
    }

    /**
     * 检查结果的描述：说明“发现了什么”“为什么会报告”以及“可以转换为什么”。
     *
     * {0} 为当前检查的字形（设置里的 Inspect），{1} 为快速修复的目标字形（Quick fix）。
     * 文案里点明“按检查设置”，让用户知道报告范围来自设置项，可以在 Settings 中调整。
     */
    fun problemDescription(project: Project? = CsleContext.project()): String {
        return CsleBundle.message("problem.description", getInspect(project), getQuickFix(project))
    }

    /** 快速修复在 Alt+Enter 菜单里的名字，只是一个动作名（“转换为……”）。 */
    fun quickFixName(project: Project? = CsleContext.project()): String {
        return CsleBundle.message("quickfix.name", getQuickFix(project))
    }

    fun getConvertedText(text: String, project: Project? = CsleContext.project()): String {
        val glyphs = getQuickFixGlyphs(project)
        CsleCustomDictionary.convert(glyphs, CsleSettings.effective(project).customDictionaryFor(glyphs), text)?.let { return it }
        return when (glyphs) {
            CsleGlyphs.SIMPLIFIED -> ZhConverterUtil.toSimple(text)
            CsleGlyphs.TAIWAN -> ZhTwConverterUtil.toTraditional(text)
            CsleGlyphs.HONGKONG -> ZhHkConverterUtil.toTraditional(text)
            CsleGlyphs.TRADITIONAL -> ZhConverterUtil.toTraditional(text)
        }
    }
}

private const val debug = false

fun debugPrintln(message: Any?) {
    if (debug) println("CsleInspection $message")
}