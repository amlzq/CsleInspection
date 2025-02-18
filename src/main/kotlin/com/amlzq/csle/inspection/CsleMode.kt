package com.amlzq.csle.inspection

enum class CsleMode(val label: String) {
    SIMPLIFIED("Simplified"), TRADITIONAL("Traditional"), TAIWAN("Traditional (Taiwan)");

    companion object {
        fun label(value: String): CsleMode {
            return entries.firstOrNull { it.label == value } ?: SIMPLIFIED
        }
    }
}