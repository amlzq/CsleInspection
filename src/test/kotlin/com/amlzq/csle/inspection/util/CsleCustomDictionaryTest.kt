package com.amlzq.csle.inspection.util

import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.amlzq.csle.inspection.settings.CsleGlyphs
import com.amlzq.csle.inspection.settings.CsleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class CsleCustomDictionaryTest : CsleBasePlatformTest() {

    fun testParseTabAndCandidates() {
        val result = CsleCustomDictionary.parse("# c\n\n服务器\t伺服端 备选\n软件\t軟體\n")
        assertEquals(listOf("伺服端", "备选"), result.entries["服务器"])
        assertEquals(listOf("軟體"), result.entries["软件"])
        assertEquals(0, result.errors.size)
    }

    fun testParseFallsBackToSpaceWhenNoTab() {
        val result = CsleCustomDictionary.parse("我的 我的TW\n")
        assertEquals(listOf("我的TW"), result.entries["我的"])
    }

    fun testTabAllowsSpacesInSourcePhrase() {
        val result = CsleCustomDictionary.parse("SQL 注入\tSQL隱碼攻擊\n")
        assertEquals(listOf("SQL隱碼攻擊"), result.entries["SQL 注入"])
    }

    fun testParseReportsErrorsAndDuplicates() {
        val result = CsleCustomDictionary.parse("只有原词\n服务器\t甲\n服务器\t乙\n\t无键\n")
        assertEquals(listOf(1, 4), result.errors.map { it.line })
        assertEquals(listOf("乙"), result.entries["服务器"])
        assertEquals(1, result.duplicates)
    }

    fun testTemplateHasNoActiveEntries() {
        val result = CsleCustomDictionary.parse(CsleCustomDictionary.TEMPLATE)
        assertEquals(0, result.entries.size)
        assertEquals(0, result.errors.size)
    }

    fun testTemplateExamplesAreValidWhenUncommented() {
        val uncommented = CsleCustomDictionary.TEMPLATE.lines()
            .filter { it.startsWith("#") && !it.startsWith("# ") && it.length > 1 }
            .joinToString("\n") { it.removePrefix("#") }
        val result = CsleCustomDictionary.parse(uncommented)
        assertEquals(4, result.entries.size)
        assertEquals(0, result.errors.size)
    }

    fun testEmptyDictionaryFallsBack() {
        assertNull(CsleCustomDictionary.convert(CsleGlyphs.TAIWAN, "", "服务器"))
    }

    fun testUserEntryOverridesBuiltIn() {
        val dict = "服务器\t伺服端\n"
        assertEquals("伺服端", CsleCustomDictionary.convert(CsleGlyphs.TAIWAN, dict, "服务器"))
        assertEquals("記憶體", CsleCustomDictionary.convert(CsleGlyphs.TAIWAN, dict, "内存"))
    }

    fun testFirstCandidateIsUsed() {
        assertEquals("軟體", CsleCustomDictionary.convert(CsleGlyphs.TAIWAN, "软件\t軟體 软件\n", "软件"))
    }

    fun testDictionaryChangeTakesEffect() {
        assertEquals("甲", CsleCustomDictionary.convert(CsleGlyphs.TAIWAN, "服务器\t甲\n", "服务器"))
        assertEquals("乙", CsleCustomDictionary.convert(CsleGlyphs.TAIWAN, "服务器\t乙\n", "服务器"))
    }

    fun testSimplifiedTarget() {
        assertEquals("程序", CsleCustomDictionary.convert(CsleGlyphs.SIMPLIFIED, "程式\t程序\n", "程式"))
    }

    fun testSettingsDictionaryIsPerTarget() {
        val state = CsleSettings.instance.state
        val oldFix = state.quickFix
        try {
            state.customDictionaries = mutableMapOf(
                CsleGlyphs.TAIWAN.name to "服务器\t伺服端\n",
                CsleGlyphs.HONGKONG.name to "服务器\t主機端\n",
            )
            state.quickFix = CsleGlyphs.TAIWAN.label
            assertEquals("伺服端", CsleUtils.getConvertedText("服务器"))
            state.quickFix = CsleGlyphs.HONGKONG.label
            assertEquals("主機端", CsleUtils.getConvertedText("服务器"))
            // 没有为该目标导入词典时走内置转换
            state.quickFix = CsleGlyphs.TRADITIONAL.label
            assertEquals("服務器", CsleUtils.getConvertedText("服务器"))
        } finally {
            state.quickFix = oldFix
            state.customDictionaries = mutableMapOf()
        }
    }
}
