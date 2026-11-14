package com.amlzq.csle.inspection.util

import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.amlzq.csle.inspection.settings.CsleGlyphs
import com.amlzq.csle.inspection.settings.CsleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class CsleCustomDictionaryTest : CsleBasePlatformTest() {

    fun testParseTabAndOnlyFirstCandidate() {
        val result = CsleCustomDictionary.parse("# c\n\n服务器\t伺服端 备选\n软件\t軟體\n")
        assertEquals("伺服端", result.entries["服务器"])
        assertEquals("軟體", result.entries["软件"])
        assertEquals(0, result.errors.size)
    }

    fun testParseFallsBackToSpaceWhenNoTab() {
        val result = CsleCustomDictionary.parse("我的 我的TW\n")
        assertEquals("我的TW", result.entries["我的"])
    }

    fun testTabAllowsSpacesInSourcePhrase() {
        val result = CsleCustomDictionary.parse("SQL 注入\tSQL隱碼攻擊\n")
        assertEquals("SQL隱碼攻擊", result.entries["SQL 注入"])
    }

    fun testParseReportsErrorsAndDuplicates() {
        val result = CsleCustomDictionary.parse("只有原词\n服务器\t甲\n服务器\t乙\n\t无键\n")
        assertEquals(listOf(1, 4), result.errors.map { it.line })
        assertEquals("乙", result.entries["服务器"])
        assertEquals(1, result.duplicates)
    }

    fun testTemplateHasNoActiveEntries() {
        for (glyph in CsleGlyphs.entries) {
            val result = CsleCustomDictionary.parse(CsleCustomDictionary.templateFor(glyph))
            assertEquals(0, result.entries.size)
            assertEquals(0, result.errors.size)
        }
    }

    fun testTemplateHasOneExampleSetForItsGlyph() {
        for (glyph in CsleGlyphs.entries) {
            val template = CsleCustomDictionary.templateFor(glyph)
            assertTrue(template.contains(glyph.label))
            val uncommented = template.lines()
                .filter { it.startsWith("#") && !it.startsWith("# ") && it.length > 1 }
                .joinToString("\n") { it.removePrefix("#") }
            val result = CsleCustomDictionary.parse(uncommented)
            assertEquals(3, result.entries.size)
            assertEquals(0, result.errors.size)
        }
        assertTrue(CsleCustomDictionary.templateFor(CsleGlyphs.SIMPLIFIED).contains("#程式\t程序"))
        assertTrue(CsleCustomDictionary.templateFor(CsleGlyphs.TAIWAN).contains("#软件\t軟體"))
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

    fun testTraditionalPivotPrefersUserEntriesOnOriginalText() {
        // 原词是台湾原文，命中则直接用用户值；其余片段经简体转成香港繁体
        assertEquals(
            "伺服端網絡",
            CsleCustomDictionary.convert(CsleGlyphs.HONGKONG, "伺服器\t伺服端\n", "伺服器網路", CsleGlyphs.TAIWAN)
        )
        // 简体形态的原词在中转后同样生效
        assertEquals(
            "主機網絡",
            CsleCustomDictionary.convert(CsleGlyphs.HONGKONG, "服务器\t主機\n", "伺服器網路", CsleGlyphs.TAIWAN)
        )
    }

    fun testSimplifiedTargetFromTaiwanKeepsRegionalWords() {
        // 有用户词典时也要用台湾内置词典：網路 → 网络
        assertEquals(
            "程序网络",
            CsleCustomDictionary.convert(CsleGlyphs.SIMPLIFIED, "程式\t程序\n", "程式網路", CsleGlyphs.TAIWAN)
        )
    }

    fun testValidate() {
        assertNull(CsleCustomDictionary.validate("服务器", "伺服器"))
        assertEquals(CsleCustomDictionary.ValidationError.EMPTY_SOURCE, CsleCustomDictionary.validate(" ", "伺服器"))
        assertEquals(CsleCustomDictionary.ValidationError.EMPTY_TARGET, CsleCustomDictionary.validate("服务器", ""))
        assertEquals(CsleCustomDictionary.ValidationError.COMMENT_PREFIX, CsleCustomDictionary.validate("#号", "號"))
        assertEquals(CsleCustomDictionary.ValidationError.INVALID_CHARACTER, CsleCustomDictionary.validate("服务\t器", "伺服器"))
        assertEquals(CsleCustomDictionary.ValidationError.TARGET_HAS_SPACE, CsleCustomDictionary.validate("服务器", "伺服 器"))
    }

    fun testPutAppendsAndOverwrites() {
        val (first, overwrittenFirst) = CsleCustomDictionary.put("# 注释\n", "服务器", "伺服器")
        assertEquals("服务器\t伺服器\n", first)
        assertEquals(false, overwrittenFirst)
        val (second, overwritten) = CsleCustomDictionary.put(first, "服务器", "伺服端")
        assertEquals("服务器\t伺服端\n", second)
        assertEquals(true, overwritten)
    }

    fun testFormatRoundTripKeepsSpacesInSource() {
        val text = CsleCustomDictionary.format(mapOf("SQL 注入" to "SQL隱碼攻擊", "软件" to "軟體"))
        val parsed = CsleCustomDictionary.parse(text)
        assertEquals("SQL隱碼攻擊", parsed.entries["SQL 注入"])
        assertEquals("軟體", parsed.entries["软件"])
        assertEquals(0, parsed.errors.size)
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
