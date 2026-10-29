package com.amlzq.csle.inspection.util

import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.amlzq.csle.inspection.settings.CsleGlyphs
import com.amlzq.csle.inspection.settings.CsleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class CsleCustomDictionaryTest : CsleBasePlatformTest() {

    fun testParseSkipsCommentsAndReportsErrors() {
        val result = CsleCustomDictionary.parse("# c\n\n服务器\t伺服器\t\t服務器\n只有一列\n甲\t乙\t丙\t丁\t戊\n")
        assertEquals(1, result.entries.size)
        assertEquals(listOf("服务器", "伺服器", null, "服務器"), result.entries[0].cells)
        assertEquals(listOf(4, 5), result.errors.map { it.line })
    }

    fun testTemplateHasNoActiveEntries() {
        val result = CsleCustomDictionary.parse(CsleCustomDictionary.TEMPLATE)
        assertEquals(0, result.entries.size)
        assertEquals(0, result.errors.size)
    }

    fun testTemplateExamplesAreValidWhenUncommented() {
        val uncommented = CsleCustomDictionary.TEMPLATE.lines()
            .filter { it.startsWith("# ") && it.contains('\t') }
            .joinToString("\n") { it.removePrefix("# ") }
        val result = CsleCustomDictionary.parse(uncommented)
        assertEquals(4, result.entries.size)
        assertEquals(0, result.errors.size)
    }

    fun testEmptyDictionaryFallsBack() {
        assertNull(CsleCustomDictionary.convert(CsleGlyphs.TAIWAN, "", "服务器"))
    }

    fun testColumnPicksTargetGlyph() {
        val dict = "服务器\t伺服端\t主機端\t服務端\n"
        assertEquals("伺服端", CsleCustomDictionary.convert(CsleGlyphs.TAIWAN, dict, "服务器"))
        assertEquals("主機端", CsleCustomDictionary.convert(CsleGlyphs.HONGKONG, dict, "服务器"))
        assertEquals("服務端", CsleCustomDictionary.convert(CsleGlyphs.TRADITIONAL, dict, "服务器"))
        assertEquals("服务器", CsleCustomDictionary.convert(CsleGlyphs.SIMPLIFIED, dict, "服务器"))
        // 其它词条仍走内置词典
        assertEquals("記憶體", CsleCustomDictionary.convert(CsleGlyphs.TAIWAN, dict, "内存"))
    }

    fun testEmptyTargetColumnSkipsRow() {
        val dict = "服务器\t\t主機端\t\n"
        assertEquals("伺服器", CsleUtils_convert(CsleGlyphs.TAIWAN, "服务器", dict))
        assertEquals("主機端", CsleUtils_convert(CsleGlyphs.HONGKONG, "服务器", dict))
    }

    fun testTraditionalToSimplified() {
        val dict = "程序\t程式\t\t\n"
        assertEquals("程序", CsleCustomDictionary.convert(CsleGlyphs.SIMPLIFIED, dict, "程式"))
    }

    fun testSettingsDictionaryUsedByQuickFix() {
        val state = CsleSettings.instance.state
        val oldFix = state.quickFix
        try {
            state.quickFix = CsleGlyphs.TAIWAN.label
            state.customDictionary = "服务器\t伺服端\t\t\n"
            assertEquals("伺服端", CsleUtils.getConvertedText("服务器"))
            state.customDictionary = ""
            assertEquals("伺服器", CsleUtils.getConvertedText("服务器"))
        } finally {
            state.quickFix = oldFix
            state.customDictionary = ""
        }
    }

    private fun CsleUtils_convert(glyph: CsleGlyphs, text: String, dict: String): String {
        val state = CsleSettings.instance.state
        val oldFix = state.quickFix
        state.quickFix = glyph.label
        state.customDictionary = dict
        try {
            return CsleUtils.getConvertedText(text)
        } finally {
            state.quickFix = oldFix
        }
    }
}
