package com.amlzq.csle.inspection.settings

import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.amlzq.csle.inspection.util.CsleUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * 字形枚举与简繁转换方向（自然语言处理核心）。
 */
class CsleGlyphsTest : CsleBasePlatformTest() {

    fun testFromLabelRoundTrip() {
        for (glyphs in CsleGlyphs.entries) {
            assertEquals(glyphs, CsleGlyphs.fromLabel(glyphs.label))
        }
    }

    fun testFromLabelWithUnknownLabel() {
        assertNull(CsleGlyphs.fromLabel("不存在的字形"))
    }

    fun testInspectGlyphsFallsBackToSimplified() {
        CsleSettings.instance.state.inspect = "不存在的字形"
        assertEquals(CsleGlyphs.SIMPLIFIED, CsleUtils.getInspectGlyphs())
    }

    fun testQuickFixGlyphsFallsBackToSimplified() {
        CsleSettings.instance.state.quickFix = "不存在的字形"
        assertEquals(CsleGlyphs.SIMPLIFIED, CsleUtils.getQuickFixGlyphs())
    }

    fun testInspectGlyphsFollowsSettings() {
        for (glyphs in CsleGlyphs.entries) {
            CsleSettings.instance.state.inspect = glyphs.label
            assertEquals(glyphs, CsleUtils.getInspectGlyphs())
        }
    }

    fun testQuickFixGlyphsFollowsSettings() {
        for (glyphs in CsleGlyphs.entries) {
            CsleSettings.instance.state.quickFix = glyphs.label
            assertEquals(glyphs, CsleUtils.getQuickFixGlyphs())
        }
    }

    fun testConvertToSimplified() {
        CsleSettings.instance.state.quickFix = CsleGlyphs.SIMPLIFIED.label
        assertEquals("软体", CsleUtils.getConvertedText("軟體"))
    }

    fun testConvertToTaiwan() {
        CsleSettings.instance.state.quickFix = CsleGlyphs.TAIWAN.label
        assertEquals("軟體", CsleUtils.getConvertedText("软件"))
    }

    fun testConvertToHongKong() {
        CsleSettings.instance.state.quickFix = CsleGlyphs.HONGKONG.label
        assertEquals("軟件", CsleUtils.getConvertedText("软件"))
    }

    fun testConvertToTraditional() {
        CsleSettings.instance.state.quickFix = CsleGlyphs.TRADITIONAL.label
        assertEquals("軟件", CsleUtils.getConvertedText("软件"))
    }
}
