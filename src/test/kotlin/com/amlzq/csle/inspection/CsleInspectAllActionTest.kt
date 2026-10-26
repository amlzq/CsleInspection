package com.amlzq.csle.inspection

import com.amlzq.csle.inspection.scan.CsleProjectScanner
import com.amlzq.csle.inspection.ui.CsleResultPanel
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class CsleInspectAllActionTest : CsleBasePlatformTest() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        LightJavaCodeInsightFixtureTestCase.JAVA_21

    fun testActionRegisteredUnderToolsMenu() {
        val manager = ActionManager.getInstance()
        assertNotNull(manager.getAction("Csle.InspectAllFiles"))
        val group = manager.getAction("Csle.ToolsGroup")
        assertNotNull(group)
        assertEquals("Chinese Expression Inspection", group.templatePresentation.text)
        assertEquals("Inspect All Files", manager.getAction("Csle.InspectAllFiles").templatePresentation.text)
    }

    fun testPanelShowsStatus() {
        myFixture.addFileToProject("A.java", "public class A { String a = \"软件\"; }")
        val result = CsleProjectScanner(project).scan()
        val panel = CsleResultPanel(project)
        panel.showResult(result)
        assertTrue(panel.statusText, panel.statusText.contains("Found 1 occurrence(s) in 1 file(s)"))
        assertEquals(1, panel.checkedItems().size)
        panel.checkAll(false)
        assertTrue(panel.checkedItems().isEmpty())
    }
}
