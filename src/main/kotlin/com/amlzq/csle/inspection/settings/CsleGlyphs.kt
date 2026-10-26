package com.amlzq.csle.inspection.settings

import com.amlzq.csle.inspection.util.CsleBundle

enum class CsleGlyphs(val label: String) {
    SIMPLIFIED(CsleBundle.message("simplified")),
    TAIWAN(CsleBundle.message("traditional.tw")),
    HONGKONG(CsleBundle.message("traditional.hk")),
    TRADITIONAL(CsleBundle.message("traditional"));

    companion object {
        fun fromLabel(label: String): CsleGlyphs? {
            return entries.find { it.label == label }
        }
    }
}