package com.amlzq.csle.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * CSLE 平台测试基类。
 *
 * [CsleSettings] 是应用级服务，多个用例之间会相互影响，
 * 因此在每个用例前后统一恢复默认设置。
 */
abstract class CsleBasePlatformTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        restoreDefaultSettings()
    }

    override fun tearDown() {
        restoreDefaultSettings()
        super.tearDown()
    }

    private fun restoreDefaultSettings() {
        val state = CsleSettings.instance.state
        state.inspect = CsleGlyphs.SIMPLIFIED.label
        state.quickFix = CsleGlyphs.TAIWAN.label
        state.excluded = emptyList()
        state.excludedJs = emptyList()
        state.excludedJava = emptyList()
        state.excludedKotlin = emptyList()
        state.excludedPython = emptyList()
        state.excludedPhp = emptyList()
        state.excludedDart = emptyList()
        state.excludedHtml = emptyList()
        state.excludedCss = emptyList()
        state.excludedXml = emptyList()
        state.excludedJson = emptyList()
        state.checkLiteralExpression = true
        state.checkDocComments = false
    }
}
