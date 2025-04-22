package com.amlzq.csle.inspection

import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.github.houbb.opencc4j.util.ZhTwConverterUtil
import com.intellij.codeInspection.LocalQuickFix

abstract class CsleLocalQuickFix : LocalQuickFix {
    val quickFix: String = CsleSettings.instance.state.quickFix

    override fun getName(): String {
        return CsleBundle.message("convert.to.another", quickFix)
    }

    override fun getFamilyName(): String {
        return name
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    fun getConvertedText(text: String): String {
        return when (quickFix) {
            CsleGlyphs.SIMPLIFIED.label -> ZhConverterUtil.toSimple(text)
            CsleGlyphs.TRADITIONAL.label -> ZhConverterUtil.toTraditional(text)
            CsleGlyphs.TAIWAN.label -> ZhTwConverterUtil.toTraditional(text)
            else -> ZhConverterUtil.toSimple(text)
        }
    }
}