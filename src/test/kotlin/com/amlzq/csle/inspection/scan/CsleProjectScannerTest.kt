package com.amlzq.csle.inspection.scan

import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.amlzq.csle.inspection.settings.CsleSettings
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/** 全项目扫描：在批量模式（非 on-the-fly）下也能产出与编辑器内一致的结果。 */
class CsleProjectScannerTest : CsleBasePlatformTest() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        LightJavaCodeInsightFixtureTestCase.JAVA_21

    fun testScanFindsLiteralsAndSkipsShared() {
        myFixture.addFileToProject(
            "A.java",
            "public class A {\n" +
                "    String a = \"软件\";\n" +
                "    String b = \"中文\";\n" +
                "}"
        )
        val result = CsleProjectScanner(project).scan()
        assertEquals(1, result.items.size)
        val item = result.items.single()
        assertEquals("\"软件\"", item.originalText)
        assertEquals(2, item.lineNumber)
    }

    fun testScanRespectsExcludedMethod() {
        CsleSettings.instance.state.excludedJava = listOf("println")
        myFixture.addFileToProject(
            "B.java",
            "public class B {\n" +
                "    void log() { System.out.println(\"软件\"); }\n" +
                "}"
        )
        assertTrue(CsleProjectScanner(project).scan().items.isEmpty())
    }
}
