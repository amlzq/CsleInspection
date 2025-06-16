package com.amlzq.csle.inspection

import com.intellij.codeInspection.LocalQuickFix

abstract class CsleLocalQuickFix : LocalQuickFix {

    override fun getName(): String {
        return CsleBundle.message("convert.to.another", CsleUtils.getQuickFix())
    }

    override fun getFamilyName(): String {
        return name
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    fun getConvertedText(text: String): String {
        return CsleUtils.getConvertedText(text)
    }
}