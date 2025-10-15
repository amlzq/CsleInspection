package com.amlzq.csle.inspection

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
}