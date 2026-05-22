package com.amlzq.csle.inspection

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "CseSettings", storages = [Storage("CseSettings.xml")])
@Service
class CsleSettings : PersistentStateComponent<CsleSettings> {
    var inspect: String = CsleGlyphs.SIMPLIFIED.label
    var quickFix: String = CsleGlyphs.TAIWAN.label
    var excluded: List<String> = listOf()
    var excludedJs: List<String> = listOf()
    var excludedJava: List<String> = listOf()
    var excludedKotlin: List<String> = listOf()
    var excludedPython: List<String> = listOf()
    var excludedPhp: List<String> = listOf()
    var excludedDart: List<String> = listOf()
    var excludedHtml: List<String> = listOf()
    var excludedCss: List<String> = listOf()
    var excludedXml: List<String> = listOf()
    var excludedJson: List<String> = listOf()
    var checkLiteralExpression: Boolean = true
    var checkDocComments: Boolean = false

    override fun getState(): CsleSettings = this

    override fun loadState(state: CsleSettings) {
        inspect = state.inspect
        quickFix = state.quickFix
        excluded = state.excluded
        excludedJs = if (state.excludedJs.isNotEmpty()) state.excludedJs else state.excluded
        excludedJava = if (state.excludedJava.isNotEmpty()) state.excludedJava else state.excluded
        excludedKotlin = if (state.excludedKotlin.isNotEmpty()) state.excludedKotlin else state.excluded
        excludedPython = if (state.excludedPython.isNotEmpty()) state.excludedPython else state.excluded
        excludedPhp = if (state.excludedPhp.isNotEmpty()) state.excludedPhp else state.excluded
        excludedDart = if (state.excludedDart.isNotEmpty()) state.excludedDart else state.excluded
        excludedHtml = if (state.excludedHtml.isNotEmpty()) state.excludedHtml else state.excluded
        excludedCss = if (state.excludedCss.isNotEmpty()) state.excludedCss else state.excluded
        excludedXml = if (state.excludedXml.isNotEmpty()) state.excludedXml else state.excluded
        excludedJson = if (state.excludedJson.isNotEmpty()) state.excludedJson else state.excluded
        checkLiteralExpression = true
        checkDocComments = state.checkDocComments
    }

    companion object {
        val instance: CsleSettings
            get() = com.intellij.openapi.components.service()
    }
}
