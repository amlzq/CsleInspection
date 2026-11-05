package com.amlzq.csle.inspection.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * 项目级设置：是否启用项目方案，以及一份与应用级同形的设置。
 *
 * 启用后检查、快速修复、扫描都读取 [scheme]；未启用时读取应用级的 [CsleSettings]。
 * 存储在 .idea 下，可以随项目提交。
 */
@State(name = "CsleProjectSettings", storages = [Storage("csleInspection.xml")])
@Service(Service.Level.PROJECT)
class CsleProjectSettings : PersistentStateComponent<CsleProjectSettings> {
    /** 是否使用项目方案；false 表示使用 IDE（应用级）方案 */
    var enabled: Boolean = false

    var scheme: CsleSettings = CsleSettings()

    override fun getState(): CsleProjectSettings = this

    override fun loadState(state: CsleProjectSettings) {
        enabled = state.enabled
        scheme.loadState(state.scheme)
    }
}
