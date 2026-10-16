package com.amlzq.csle.inspection

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * Java 端到端高亮测试。
 *
 * 轻量测试工程默认未挂载 JDK，无法解析 String 等类型，
 * 因此使用带模拟 JDK 的工程描述符。
 */
class CsleJavaHighlightingTest : CsleBasePlatformTest() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        LightJavaCodeInsightFixtureTestCase.JAVA_21

    fun testLiteralExpression() {
        myFixture.enableInspections(CsleJavaInspection::class.java)
        myFixture.configureByText(
            "A.java",
            "public class A {\n" +
                "    public static final String SOFTWARE = <warning descr=\"Convert to Chinese (Taiwan)\">\"软件\"</warning>;\n" +
                "    public static final String SHARED = \"中文\";\n" +
                "}"
        )
        myFixture.checkHighlighting()
    }

    fun testExcludedMethod() {
        CsleSettings.instance.state.excludedJava = listOf("println")
        myFixture.enableInspections(CsleJavaInspection::class.java)
        myFixture.configureByText(
            "A.java",
            "public class A {\n" +
                "    public void log() {\n" +
                "        System.out.println(\"软件\");\n" +
                "    }\n" +
                "}"
        )
        myFixture.checkHighlighting()
    }

    fun testDocComment() {
        CsleSettings.instance.state.checkDocComments = true
        myFixture.enableInspections(CsleJavaInspection::class.java)
        myFixture.configureByText(
            "A.java",
            "public class A {\n" +
                "    <warning descr=\"Convert to Chinese (Taiwan)\">/** 软件 */</warning>\n" +
                "    public void m() {}\n" +
                "}"
        )
        myFixture.checkHighlighting()
    }
}
