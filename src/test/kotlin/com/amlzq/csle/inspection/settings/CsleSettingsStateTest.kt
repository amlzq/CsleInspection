package com.amlzq.csle.inspection.settings

import com.amlzq.csle.inspection.CsleBasePlatformTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * [CsleSettings] 的默认值与状态加载回退逻辑。
 */
class CsleSettingsStateTest : CsleBasePlatformTest() {

    fun testDefaultState() {
        val state = CsleSettings.instance.state
        assertEquals(CsleGlyphs.SIMPLIFIED.label, state.inspect)
        assertEquals(CsleGlyphs.TAIWAN.label, state.quickFix)
        assertTrue(state.checkLiteralExpression)
        assertFalse(state.checkDocComments)
        assertTrue(state.excluded.isEmpty())
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
