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

    override fun getState(): CsleSettings = this

    override fun loadState(state: CsleSettings) {
        inspect = state.inspect
        quickFix = state.quickFix
        excluded = state.excluded
    }

    companion object {
        val instance: CsleSettings
            get() = com.intellij.openapi.components.service()
    }
}
