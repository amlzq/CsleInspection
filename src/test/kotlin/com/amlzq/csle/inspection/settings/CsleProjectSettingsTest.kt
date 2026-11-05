package com.amlzq.csle.inspection.settings

import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.amlzq.csle.inspection.lang.CsleJavaInspection
import com.amlzq.csle.inspection.util.CsleContext
import com.amlzq.csle.inspection.util.CsleUtils
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import java.awt.Component
import java.awt.Container

/**
 * 应用级（IDE 方案）与项目级（项目方案）设置的切换、读取与持久化。
 */
class CsleProjectSettingsTest : CsleBasePlatformTest() {

    private fun projectSettings() = project.service<CsleProjectSettings>()

    override fun getProjectDescriptor() = com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21

    override fun tearDown() {
        projectSettings().enabled = false
        projectSettings().scheme = CsleSettings()
        super.tearDown()
    }

    fun testIdeSchemeIsUsedByDefault() {
        assertFalse(projectSettings().enabled)
        assertSame(CsleSettings.instance, CsleSettings.effective(project))
    }

    fun testProjectSchemeIsUsedWhenEnabled() {
        projectSettings().enabled = true
        projectSettings().scheme.inspect = CsleGlyphs.TRADITIONAL.label
        projectSettings().scheme.quickFix = CsleGlyphs.HONGKONG.label

        assertSame(projectSettings().scheme, CsleSettings.effective(project))
        assertEquals(CsleGlyphs.TRADITIONAL.label, CsleUtils.getInspect(project))
        assertEquals(CsleGlyphs.HONGKONG.label, CsleUtils.getQuickFix(project))
        // 没有项目上下文时仍读取 IDE 方案
        assertEquals(CsleGlyphs.SIMPLIFIED.label, CsleUtils.getInspect())
    }

    fun testContextSelectsTheScheme() {
        projectSettings().enabled = true
        projectSettings().scheme.inspect = CsleGlyphs.TAIWAN.label
        CsleContext.with(project) {
            assertEquals(CsleGlyphs.TAIWAN, CsleUtils.getInspectGlyphs())
        }
        assertEquals(CsleGlyphs.SIMPLIFIED, CsleUtils.getInspectGlyphs())
    }

    fun testUnsetProjectValuesStillResolveFromLocale() {
        projectSettings().enabled = true
        assertEquals(CsleSettings.defaultInspect().label, CsleUtils.getInspect(project))
    }

    fun testProjectSchemeDrivesInspection() {
        projectSettings().enabled = true
        projectSettings().scheme.inspect = CsleGlyphs.TRADITIONAL.label
        projectSettings().scheme.quickFix = CsleGlyphs.SIMPLIFIED.label
        myFixture.enableInspections(CsleJavaInspection::class.java)
        // 项目方案检查繁体：只有繁体字面量报警，IDE 方案下会报警的简体不再报
        myFixture.configureByText(
            "Demo.java",
            "class Demo {\n" +
                "    String a = <warning descr=\"Traditional Chinese characters found per the inspection settings; convert to Simplified Chinese\">\"軟件\"</warning>;\n" +
                "    String b = \"软件\";\n" +
                "}"
        )
        myFixture.checkHighlighting()
    }

    fun testStateRoundTripsThroughXml() {
        val settings = CsleProjectSettings()
        settings.enabled = true
        settings.scheme.inspect = CsleGlyphs.HONGKONG.label
        settings.scheme.excludedJava = listOf("print")
        settings.scheme.customDictionaries = mutableMapOf(CsleGlyphs.TAIWAN.name to "程式\t程序\n")

        val element = XmlSerializer.serialize(settings)
        val loaded = CsleProjectSettings()
        loaded.loadState(XmlSerializer.deserialize(element, CsleProjectSettings::class.java))

        assertTrue(loaded.enabled)
        assertEquals(CsleGlyphs.HONGKONG.label, loaded.scheme.inspect)
        assertEquals(listOf("print"), loaded.scheme.excludedJava)
        assertEquals("程式\t程序\n", loaded.scheme.customDictionaries[CsleGlyphs.TAIWAN.name])
    }

    private fun findAll(container: Container, out: MutableList<Component> = mutableListOf()): List<Component> {
        container.components.forEach {
            out.add(it)
            if (it is Container) findAll(it, out)
        }
        return out
    }

    private fun schemeComboBox(root: Container): ComboBox<*> =
        findAll(root).filterIsInstance<ComboBox<*>>()
            .first { it.itemCount > 0 && it.getItemAt(0)?.javaClass?.simpleName == "SettingsScheme" }

    private fun glyphComboBoxes(root: Container): List<ComboBox<*>> =
        findAll(root).filterIsInstance<ComboBox<*>>().filter { it.itemCount == CsleGlyphs.entries.size }

    fun testConfigurableSwitchesSchemeAndApplies() {
        val configurable = CsleSettingsConfigurable(project)
        val root = configurable.createComponent() as Container
        val scheme = schemeComboBox(root)
        val (inspect, _) = glyphComboBoxes(root)

        // 初始为 IDE 方案
        assertEquals(CsleGlyphs.SIMPLIFIED.label, inspect.selectedItem)
        assertFalse(configurable.isModified)

        // 切到项目方案并改字形：只写入项目方案，IDE 方案不变
        scheme.selectedIndex = (0 until scheme.itemCount).first { scheme.getItemAt(it).toString() == "Project" }
        assertTrue(configurable.isModified)
        inspect.selectedItem = CsleGlyphs.HONGKONG.label
        configurable.apply()

        assertTrue(projectSettings().enabled)
        assertEquals(CsleGlyphs.HONGKONG.label, projectSettings().scheme.inspect)
        assertEquals(CsleGlyphs.SIMPLIFIED.label, CsleSettings.instance.inspect)
        assertFalse(configurable.isModified)
    }

    fun testConfigurableResetRestoresStoredScheme() {
        projectSettings().enabled = true
        projectSettings().scheme.inspect = CsleGlyphs.TAIWAN.label
        val configurable = CsleSettingsConfigurable(project)
        val root = configurable.createComponent() as Container

        assertEquals("Project", schemeComboBox(root).selectedItem.toString())
        assertEquals(CsleGlyphs.TAIWAN.label, glyphComboBoxes(root)[0].selectedItem)
        assertFalse(configurable.isModified)
    }

    fun testCopyToProjectAndBackToIde() {
        CsleSettings.instance.inspect = CsleGlyphs.TAIWAN.label
        CsleSettings.instance.excludedJava = listOf("print")
        val configurable = CsleSettingsConfigurable(project)
        val root = configurable.createComponent() as Container
        val scheme = schemeComboBox(root)

        // IDE → 项目：先只改界面，Apply 之后才写入项目方案
        configurable.copyToScheme(SettingsScheme.PROJECT)
        assertEquals("Project", scheme.selectedItem.toString())
        assertTrue(configurable.isModified)
        assertFalse(projectSettings().enabled)
        configurable.apply()
        assertTrue(projectSettings().enabled)
        assertEquals(CsleGlyphs.TAIWAN.label, projectSettings().scheme.inspect)
        assertEquals(listOf("print"), projectSettings().scheme.excludedJava)

        // 项目 → IDE：把项目方案的改动带到 IDE 方案
        projectSettings().scheme.inspect = CsleGlyphs.HONGKONG.label
        configurable.reset()
        configurable.copyToScheme(SettingsScheme.IDE)
        assertEquals("Default (IDE)", scheme.selectedItem.toString())
        configurable.apply()
        assertFalse(projectSettings().enabled)
        assertEquals(CsleGlyphs.HONGKONG.label, CsleSettings.instance.inspect)
    }
}
