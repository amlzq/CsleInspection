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

    fun testConvertTaiwanToSimplifiedUsesMainlandWords() {
        CsleSettings.instance.state.inspect = CsleGlyphs.TAIWAN.label
        CsleSettings.instance.state.quickFix = CsleGlyphs.SIMPLIFIED.label
        assertEquals("网络", CsleUtils.getConvertedText("網路"))
        assertEquals("软件", CsleUtils.getConvertedText("軟體"))
        assertEquals("电脑网络连接", CsleUtils.getConvertedText("電腦網路連線"))
    }

    fun testConvertHongKongToSimplified() {
        CsleSettings.instance.state.inspect = CsleGlyphs.HONGKONG.label
        CsleSettings.instance.state.quickFix = CsleGlyphs.SIMPLIFIED.label
        assertEquals("软件网络", CsleUtils.getConvertedText("軟件網絡"))
    }

    private fun convert(source: CsleGlyphs, target: CsleGlyphs, text: String): String {
        CsleSettings.instance.state.inspect = source.label
        CsleSettings.instance.state.quickFix = target.label
        return CsleUtils.getConvertedText(text)
    }

    fun testConvertBetweenTraditionalVariants() {
        assertEquals("網絡軟件 裏面 打印機 服務器", convert(CsleGlyphs.TAIWAN, CsleGlyphs.HONGKONG, "網路軟體 裡面 印表機 伺服器"))
        assertEquals("網絡軟件 後臺 臺灣", convert(CsleGlyphs.TAIWAN, CsleGlyphs.TRADITIONAL, "網路軟體 後臺 臺灣"))
        assertEquals("網路軟體 裡面 印表機 伺服器", convert(CsleGlyphs.HONGKONG, CsleGlyphs.TAIWAN, "網絡軟件 裏面 打印機 服務器"))
        assertEquals("後臺 臺灣", convert(CsleGlyphs.HONGKONG, CsleGlyphs.TRADITIONAL, "後台 台灣"))
        assertEquals("網路軟體 滑鼠", convert(CsleGlyphs.TRADITIONAL, CsleGlyphs.TAIWAN, "網絡軟件 鼠標"))
        assertEquals("後台 台灣", convert(CsleGlyphs.TRADITIONAL, CsleGlyphs.HONGKONG, "後臺 臺灣"))
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
