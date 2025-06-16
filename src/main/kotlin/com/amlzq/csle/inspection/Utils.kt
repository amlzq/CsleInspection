package com.amlzq.csle.inspection

import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.github.houbb.opencc4j.util.ZhHkConverterUtil
import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

object CsleUtils {
    fun getRealVirtualFile(psiFile: PsiFile?): VirtualFile? {
        return psiFile?.originalFile?.virtualFile
    }

    fun getInspect(): String {
        return CsleSettings.instance.state.inspect
    }

    fun getInspectGlyphs(): CsleGlyphs {
        val label: String = getInspect()
        return CsleGlyphs.fromLabel(label) ?: CsleGlyphs.SIMPLIFIED
    }

    fun getQuickFix(): String {
        return CsleSettings.instance.state.quickFix
    }

    fun getQuickFixGlyphs(): CsleGlyphs {
        val label: String = getQuickFix()
        return CsleGlyphs.fromLabel(label) ?: CsleGlyphs.SIMPLIFIED
    }

    fun getConvertedText(text: String): String {
        return when (getQuickFixGlyphs()) {
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