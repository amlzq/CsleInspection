package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

/**
 * 快速修复：把源码中的汉字转换为设置的目标字形。
 */
class CsleQuickFixTest : CsleBasePlatformTest() {

    private fun fixAndCheckResult(
        fileName: String,
        text: String,
        expected: String,
        tool: CsleLocalInspectionTool
    ) {
        val file = myFixture.configureByText(fileName, text)
        val problems =
            tool.checkFile(file, InspectionManager.getInstance(file.project), true) ?: emptyArray()
        assertEquals("应报告 1 个问题", 1, problems.size)
        applyQuickFix(problems[0])
        myFixture.checkResult(expected)
    }

    private fun applyQuickFix(problem: ProblemDescriptor) {
        val quickFix = (problem.fixes?.firstOrNull() as? LocalQuickFix)
            ?: error("问题未附带快速修复: ${problem.descriptionTemplate}")
        val project: Project = myFixture.project
        WriteCommandAction.runWriteCommandAction(project) {
            quickFix.applyFix(project, problem)
        }
    }

    fun testQuickFixNameAndFamilyName() {
        val quickFix = object : CsleLocalQuickFix() {
            override fun applyFix(project: Project, descriptor: ProblemDescriptor) = Unit
        }
        assertEquals("Convert to Chinese (Taiwan)", quickFix.name)
        assertEquals(quickFix.name, quickFix.familyName)
        assertFalse(quickFix.startInWriteAction())
    }

    // ---------- Kotlin ----------

    fun testKotlinLiteralQuickFix() {
        fixAndCheckResult(
            "a.kt",
            "const val SOFTWARE = \"软件\"",
            "const val SOFTWARE = \"軟體\"",
            CsleKotlinInspection()
        )
    }

    fun testKotlinDocCommentQuickFix() {
        CsleSettings.instance.state.checkDocComments = true
        fixAndCheckResult(
            "a.kt",
            "/** 软件 */\nfun main() {}",
            "/** 軟體 */\nfun main() {}",
            CsleKotlinInspection()
        )
    }

    fun testKotlinHongKongQuickFix() {
        CsleSettings.instance.state.quickFix = CsleGlyphs.HONGKONG.label
        fixAndCheckResult(
            "a.kt",
            "const val SOFTWARE = \"软件\"",
            "const val SOFTWARE = \"軟件\"",
            CsleKotlinInspection()
        )
    }

    // ---------- Java ----------

    fun testJavaLiteralQuickFix() {
        fixAndCheckResult(
            "A.java",
            "public class A { public static final String S = \"软件\"; }",
            "public class A { public static final String S = \"軟體\"; }",
            CsleJavaInspection()
        )
    }

    fun testJavaDocCommentQuickFix() {
        CsleSettings.instance.state.checkDocComments = true
        fixAndCheckResult(
            "A.java",
            "public class A {\n    /** 软件 */\n    void m() {}\n}",
            "public class A {\n    /** 軟體 */\n    void m() {}\n}",
            CsleJavaInspection()
        )
    }

    // ---------- JavaScript ----------

    fun testJsLiteralQuickFix() {
        fixAndCheckResult(
            "a.js",
            "var s = '软件';",
            "var s = '軟體';",
            CsleJSInspection()
        )
    }

    // ---------- JSON ----------

    fun testJsonLiteralQuickFix() {
        fixAndCheckResult(
            "a.json",
            "{\"title\": \"软件\"}",
            "{\"title\": \"軟體\"}",
            CsleJsonInspection()
        )
    }

    // ---------- XML ----------

    fun testXmlTextQuickFix() {
        fixAndCheckResult(
            "a.xml",
            "<resources><string name=\"a\">软件</string></resources>",
            "<resources><string name=\"a\">軟體</string></resources>",
            CsleXmlInspection()
        )
    }

    fun testXmlAttributeValueQuickFix() {
        fixAndCheckResult(
            "a.xml",
            "<resources><string name=\"a\" label=\"软件\"/></resources>",
            "<resources><string name=\"a\" label=\"軟體\"/></resources>",
            CsleXmlInspection()
        )
    }

    // ---------- HTML ----------

    fun testHtmlTextQuickFix() {
        fixAndCheckResult(
            "a.html",
            "<p>软件</p>",
            "<p>軟體</p>",
            CsleHtmlInspection()
        )
    }

    fun testHtmlAttributeValueQuickFix() {
        fixAndCheckResult(
            "a.html",
            "<p title=\"软件\">x</p>",
            "<p title=\"軟體\">x</p>",
            CsleHtmlInspection()
        )
    }

    // ---------- CSS ----------

    fun testCssStringQuickFix() {
        fixAndCheckResult(
            "a.css",
            ".a::before { content: \"软件\"; }",
            ".a::before { content: \"軟體\"; }",
            CsleCssInspection()
        )
    }

    // ---------- Python ----------

    fun testPythonLiteralQuickFix() {
        fixAndCheckResult(
            "a.py",
            "s = \"软件\"",
            "s = \"軟體\"",
            CslePythonInspection()
        )
    }

    // ---------- PHP ----------

    fun testPhpLiteralQuickFix() {
        fixAndCheckResult(
            "a.php",
            "<?php \$s = \"软件\";",
            "<?php \$s = \"軟體\";",
            CslePhpInspection()
        )
    }

    // ---------- Dart ----------

    fun testDartLiteralQuickFix() {
        fixAndCheckResult(
            "a.dart",
            "var s = \"软件\";",
            "var s = \"軟體\";",
            CsleDartInspection()
        )
    }
}
