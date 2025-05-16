package com.amlzq.csle.inspection

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