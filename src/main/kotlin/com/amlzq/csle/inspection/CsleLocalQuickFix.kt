package com.amlzq.csle.inspection

import com.amlzq.csle.inspection.util.CsleUtils
import com.intellij.codeInspection.LocalQuickFix

/**
 * 快速修复基类。
 *
 * 统一使用设置中的转换目标（简体/繁体/香港繁体）转换文本，
 * 修复在写命令中执行，子类只需实现 [applyFix]。
 */
abstract class CsleLocalQuickFix : LocalQuickFix {

    override fun getName(): String {
        // 菜单里显示的是动作（“转换为中文（台湾）”），而不是问题描述
        return CsleUtils.quickFixName()
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