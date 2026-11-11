package com.amlzq.csle.inspection.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.amlzq.csle.inspection.util.CsleContext

@State(name = "CseSettings", storages = [Storage("CseSettings.xml")])
@Service
class CsleSettings : PersistentStateComponent<CsleSettings> {
    /** 检查字形的标签；空串表示用户没设置过，读取时按 界面语言 > 简体 解析（见 CsleUtils.getInspect） */
    var inspect: String = ""

    /** 修复目标字形的标签；空串表示用户没设置过，读取时按 界面语言 > 检查字形 解析（见 CsleUtils.getQuickFix） */
    var quickFix: String = ""
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

    /** 是否检查文档注释（KDoc/Javadoc/JSDoc/PHPDoc/DartDoc），默认关闭，只检查字符串字面量 */
    var checkDocComments: Boolean = false

    /** 项目打开后是否自动扫描全项目，默认关闭 */
    var autoScanOnOpen: Boolean = false

    /** 用户导入的自定义惯用语词典（OpenCC 词典格式的原文），按快速修复目标字形（[CsleGlyphs.name]）分别保存 */
    var customDictionaries: MutableMap<String, String> = mutableMapOf()

    fun customDictionaryFor(glyph: CsleGlyphs): String = customDictionaries[glyph.name].orEmpty()

    override fun getState(): CsleSettings = this

    /** 解析后的检查字形标签：用户设置 > 界面语言 > 简体 */
    fun resolvedInspect(): String = inspect.ifBlank { defaultInspect().label }

    /** 解析后的修复目标标签：用户设置 > 界面语言 > 检查字形 */
    fun resolvedQuickFix(): String = quickFix.ifBlank {
        defaultQuickFix(CsleGlyphs.fromLabel(resolvedInspect()) ?: CsleGlyphs.SIMPLIFIED).label
    }

    /** 把另一份设置的全部内容复制过来（“复制到项目”使用） */
    fun copyFrom(other: CsleSettings) = loadState(other)

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
        checkLiteralExpression = state.checkLiteralExpression
        checkDocComments = state.checkDocComments
        autoScanOnOpen = state.autoScanOnOpen
        customDictionaries = state.customDictionaries.filterValues { it.isNotBlank() }.toMutableMap()
    }

    companion object {
        /** 由 IDE 界面语言推断检查字形：zh_CN/zh_SG/zh_MY 为简体，zh_TW 为台湾繁体，zh_HK 为香港繁体，其余回落为简体。 */
        fun glyphsForLocale(locale: java.util.Locale): CsleGlyphs? {
            if (locale.language != "zh") return null
            return when (locale.country) {
                "CN", "SG", "MY" -> CsleGlyphs.SIMPLIFIED
                "TW" -> CsleGlyphs.TAIWAN
                "HK" -> CsleGlyphs.HONGKONG
                else -> null
            }
        }

        fun defaultInspect(): CsleGlyphs =
            glyphsForLocale(com.intellij.DynamicBundle.getLocale()) ?: CsleGlyphs.SIMPLIFIED

        /** 修复目标的默认值：先取界面语言对应的字形，回落为检查字形。 */
        fun defaultQuickFix(inspect: CsleGlyphs): CsleGlyphs =
            glyphsForLocale(com.intellij.DynamicBundle.getLocale()) ?: inspect

        /** 应用级（IDE 方案）设置 */
        val instance: CsleSettings
            get() = service()

        /** 项目已启用项目方案时返回项目级设置，否则返回应用级设置；默认按当前检查所属的项目判断。 */
        fun effective(project: com.intellij.openapi.project.Project? = CsleContext.project()): CsleSettings {
            if (project == null || project.isDisposed || project.isDefault) return instance
            val projectSettings = project.service<CsleProjectSettings>()
            return if (projectSettings.enabled) projectSettings.scheme else instance
        }
    }
}
