package com.amlzq.csle.inspection.settings

import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.amlzq.csle.inspection.util.CsleUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * [CsleSettings] 的默认值与状态加载回退逻辑。
 */
class CsleSettingsStateTest : CsleBasePlatformTest() {

    fun testDefaultState() {
        val state = CsleSettings()
        // 未设置时存储层是空串，读取时才按 界面语言 > 回落 解析
        assertEquals("", state.inspect)
        assertEquals("", state.quickFix)
        assertTrue(state.checkLiteralExpression)
        assertFalse(state.checkDocComments)
        assertTrue(state.excluded.isEmpty())
    }

    fun testGlyphsForLocale() {
        fun g(l: String, c: String) = CsleSettings.glyphsForLocale(java.util.Locale(l, c))
        assertEquals(CsleGlyphs.SIMPLIFIED, g("zh", "CN"))
        assertEquals(CsleGlyphs.SIMPLIFIED, g("zh", "SG"))
        assertEquals(CsleGlyphs.SIMPLIFIED, g("zh", "MY"))
        assertEquals(CsleGlyphs.TAIWAN, g("zh", "TW"))
        assertEquals(CsleGlyphs.HONGKONG, g("zh", "HK"))
        assertEquals(null, g("en", "US"))
    }

    fun testUnsetValuesResolveFromLocale() {
        val state = CsleSettings.instance.state
        state.inspect = ""
        state.quickFix = ""
        assertEquals(CsleSettings.defaultInspect().label, CsleUtils.getInspect())
        assertEquals(CsleSettings.defaultQuickFix(CsleSettings.defaultInspect()).label, CsleUtils.getQuickFix())
    }

    fun testUnsetQuickFixFollowsSavedInspect() {
        if (CsleSettings.glyphsForLocale(com.intellij.DynamicBundle.getLocale()) != null) return
        val state = CsleSettings.instance.state
        state.inspect = CsleGlyphs.TRADITIONAL.label
        state.quickFix = ""
        assertEquals(CsleGlyphs.TRADITIONAL.label, CsleUtils.getQuickFix())
    }

    fun testSavedValuesWinOverLocale() {
        val state = CsleSettings.instance.state
        state.inspect = CsleGlyphs.HONGKONG.label
        state.quickFix = CsleGlyphs.SIMPLIFIED.label
        assertEquals(CsleGlyphs.HONGKONG.label, CsleUtils.getInspect())
        assertEquals(CsleGlyphs.SIMPLIFIED.label, CsleUtils.getQuickFix())
    }

    fun testQuickFixFallsBackToInspect() {
        // 测试环境的界面语言不是中文变体时，修复目标回落为检查字形
        if (CsleSettings.glyphsForLocale(com.intellij.DynamicBundle.getLocale()) == null) {
            CsleGlyphs.entries.forEach { assertEquals(it, CsleSettings.defaultQuickFix(it)) }
        }
    }

    fun testGetStateReturnsItself() {
        val settings = CsleSettings()
        assertTrue(settings.state === settings)
    }

    fun testLoadStateFallsBackToGenericExcluded() {
        val stored = CsleSettings().apply {
            inspect = "自定义检查项"
            quickFix = "自定义修复项"
            excluded = listOf("logger", "println")
            checkDocComments = true
        }
        val target = CsleSettings()
        target.loadState(stored)

        val expected = listOf("logger", "println")
        assertEquals(expected, target.excludedJs)
        assertEquals(expected, target.excludedJava)
        assertEquals(expected, target.excludedKotlin)
        assertEquals(expected, target.excludedPython)
        assertEquals(expected, target.excludedPhp)
        assertEquals(expected, target.excludedDart)
        assertEquals(expected, target.excludedHtml)
        assertEquals(expected, target.excludedCss)
        assertEquals(expected, target.excludedXml)
        assertEquals(expected, target.excludedJson)
        assertEquals("自定义检查项", target.inspect)
        assertEquals("自定义修复项", target.quickFix)
        assertTrue(target.checkLiteralExpression)
        assertTrue(target.checkDocComments)
    }

    fun testLoadStateKeepsPerLanguageExcluded() {
        val stored = CsleSettings().apply {
            excluded = listOf("generic")
            excludedKotlin = listOf("println")
            excludedJson = listOf("a.b.c")
        }
        val target = CsleSettings()
        target.loadState(stored)

        assertEquals(listOf("println"), target.excludedKotlin)
        assertEquals(listOf("a.b.c"), target.excludedJson)
        assertEquals(listOf("generic"), target.excludedJava)
        assertEquals(listOf("generic"), target.excludedHtml)
    }
}
