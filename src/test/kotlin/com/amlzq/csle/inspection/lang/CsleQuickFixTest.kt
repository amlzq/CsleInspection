package com.amlzq.csle.inspection.lang

import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.amlzq.csle.inspection.CsleLocalInspectionTool
import com.amlzq.csle.inspection.CsleLocalQuickFix
import com.amlzq.csle.inspection.settings.CsleGlyphs
import com.amlzq.csle.inspection.settings.CsleSettings
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

    fun testKotlinRawStringLiteralQuickFix() {
        fixAndCheckResult(
            "a.kt",
            "const val RAW = \"\"\"软件\"\"\"",
            "const val RAW = \"\"\"軟體\"\"\"",
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

    fun testKotlinInterpolationQuickFix() {
        // 只转换字面量部分，插值表达式原样保留
        fixAndCheckResult(
            "a.kt",
            "val s = \"软件\${name}软体\"",
            "val s = \"軟體\${name}軟體\"",
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

    fun testJavaCharLiteralQuickFix() {
        // 回归：字符字面量取值为 Char，之前按 String 强转会抛 ClassCastException
        fixAndCheckResult(
            "A.java",
            "public class A { char c = '软'; }",
            "public class A { char c = '軟'; }",
            CsleJavaInspection()
        )
    }

    fun testJavaTextBlockQuickFix() {
        // 文本块保留三引号形式
        fixAndCheckResult(
            "A.java",
            "public class A {\n    String s = \"\"\"\n        软件\n        \"\"\";\n}",
            "public class A {\n    String s = \"\"\"\n        軟體\n        \"\"\";\n}",
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

    fun testJsTemplateLiteralQuickFix() {
        // 回归：模版字符串也有告警，快速修复不能静默失效
        fixAndCheckResult(
            "a.js",
            "var s = `软件`;",
            "var s = `軟體`;",
            CsleJSInspection()
        )
    }

    fun testJsxTextQuickFix() {
        fixAndCheckResult(
            "a.jsx",
            "const e = <div>软件</div>;",
            "const e = <div>軟體</div>;",
            CsleJSInspection()
        )
    }

    fun testJsxMultiLineTextQuickFix() {
        // 多行 JSX 文本修复后不能丢换行或被包成 CDATA
        fixAndCheckResult(
            "a.jsx",
            "const e = (\n    <div>\n        软件\n    </div>\n);",
            "const e = (\n    <div>\n        軟體\n    </div>\n);",
            CsleJSInspection()
        )
    }

    fun testJsxAttributeValueQuickFix() {
        fixAndCheckResult(
            "a.jsx",
            "const e = <div title=\"软件\" />;",
            "const e = <div title=\"軟體\" />;",
            CsleJSInspection()
        )
    }

    fun testJsDocCommentQuickFix() {
        CsleSettings.instance.state.checkDocComments = true
        fixAndCheckResult(
            "a.js",
            "/**\n * 软件\n */\nvar a = 1;",
            "/**\n * 軟體\n */\nvar a = 1;",
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

    fun testJsonKeyQuickFix() {
        fixAndCheckResult(
            "a.json",
            "{\"软件\": \"a\"}",
            "{\"軟體\": \"a\"}",
            CsleJsonInspection()
        )
    }

    // ---------- XML ----------

    fun testXmlTextQuickFix() {
        fixAndCheckResult(
            "a.xml",
            "<resources><string name=\"a\">软件</string></resources>",
            "<resources><string name=\"a\">軟體</string></resources>",
            CsleXMLInspection()
        )
    }

    fun testXmlAttributeValueQuickFix() {
        fixAndCheckResult(
            "a.xml",
            "<resources><string name=\"a\" label=\"软件\"/></resources>",
            "<resources><string name=\"a\" label=\"軟體\"/></resources>",
            CsleXMLInspection()
        )
    }

    // ---------- HTML ----------

    fun testHtmlTextQuickFix() {
        fixAndCheckResult(
            "a.html",
            "<p>软件</p>",
            "<p>軟體</p>",
            CsleHTMLInspection()
        )
    }

    fun testHtmlTextWithCommentQuickFix() {
        // 回归：HTML 中注释是 XmlText 的子节点，快速修复只转换文本、原样保留注释
        fixAndCheckResult(
            "a.html",
            "<p>软件<!-- 注释 --></p>",
            "<p>軟體<!-- 注释 --></p>",
            CsleHTMLInspection()
        )
    }

    fun testHtmlMultiLineTextQuickFix() {
        // 回归：多行文本节点修复后不应被包成 <![CDATA[...]]>
        fixAndCheckResult(
            "a.html",
            "<p>\n    这是一段带有内联样式的文本。\n</p>",
            "<p>\n    這是一段帶有內聯樣式的文字。\n</p>",
            CsleHTMLInspection()
        )
    }

    fun testHtmlTextWithEntityQuickFix() {
        // 文本中的实体引用不能被二次转义
        fixAndCheckResult(
            "a.html",
            "<p>软件 &amp; 软体</p>",
            "<p>軟體 &amp; 軟體</p>",
            CsleHTMLInspection()
        )
    }

    fun testHtmlTextWithMultipleCommentsQuickFix() {
        // 一个 XmlText 内多个注释：按不含注释的文本段分别报告，修复时注释原样保留
        val file = myFixture.configureByText("a.html", "<p>软件<!-- a -->软体<!-- b --></p>")
        val problems = CsleHTMLInspection().checkFile(
            file, InspectionManager.getInstance(file.project), true
        ) ?: emptyArray()
        assertEquals(2, problems.size)
        applyQuickFix(problems[0])
        myFixture.checkResult("<p>軟體<!-- a -->軟體<!-- b --></p>")
    }

    fun testHtmlTextAfterCommentQuickFix() {
        // 注释位于文本开头时，只转换其后的文本
        fixAndCheckResult(
            "a.html",
            "<p><!-- 注释 -->软件</p>",
            "<p><!-- 注释 -->軟體</p>",
            CsleHTMLInspection()
        )
    }

    fun testHtmlStyleAttributeQuickFix() {
        fixAndCheckResult(
            "a.html",
            "<p style=\"content:'软件'\">x</p>",
            "<p style=\"content:'軟體'\">x</p>",
            CsleHTMLInspection()
        )
    }

    fun testHtmlAttributeValueQuickFix() {
        fixAndCheckResult(
            "a.html",
            "<p title=\"软件\">x</p>",
            "<p title=\"軟體\">x</p>",
            CsleHTMLInspection()
        )
    }

    fun testHtmlInlineScriptQuickFix() {
        // 回归：HTML 内嵌 <script> 中的字符串字面量必须有快速修复
        fixAndCheckResult(
            "a.html",
            "<script>var s = \"软件\";</script>",
            "<script>var s = \"軟體\";</script>",
            CsleHTMLInspection()
        )
    }

    fun testHtmlInlineScriptTemplateLiteralQuickFix() {
        fixAndCheckResult(
            "a.html",
            "<script>var s = `软件`;</script>",
            "<script>var s = `軟體`;</script>",
            CsleHTMLInspection()
        )
    }

    fun testHtmlInlineStyleQuickFix() {
        // 回归：HTML 内嵌 <style> 中的字符串字面量必须有快速修复
        fixAndCheckResult(
            "a.html",
            "<html><head><style>.a{content:'软件'}</style></head></html>",
            "<html><head><style>.a{content:'軟體'}</style></head></html>",
            CsleHTMLInspection()
        )
    }

    // ---------- CSS ----------

    fun testCssStringQuickFix() {
        fixAndCheckResult(
            "a.css",
            ".a::before { content: \"软件\"; }",
            ".a::before { content: \"軟體\"; }",
            CsleCSSInspection()
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

    fun testPythonDocstringQuickFix() {
        // 三引号文档字符串保持原样
        fixAndCheckResult(
            "a.py",
            "def f():\n    \"\"\"软件\"\"\"",
            "def f():\n    \"\"\"軟體\"\"\"",
            CslePythonInspection()
        )
    }

    fun testPythonPrefixedStringQuickFix() {
        // 原始字符串前缀 r 保留
        fixAndCheckResult(
            "a.py",
            "s = r'软件'",
            "s = r'軟體'",
            CslePythonInspection()
        )
    }

    // ---------- PHP ----------

    fun testPhpLiteralQuickFix() {
        fixAndCheckResult(
            "a.php",
            "<?php \$s = \"软件\";",
            "<?php \$s = \"軟體\";",
            CslePHPInspection()
        )
    }

    fun testPhpDocCommentQuickFix() {
        CsleSettings.instance.state.checkDocComments = true
        fixAndCheckResult(
            "a.php",
            "<?php\n/** 软件 */\nfunction f() {}",
            "<?php\n/** 軟體 */\nfunction f() {}",
            CslePHPInspection()
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

    fun testDartDocCommentQuickFix() {
        CsleSettings.instance.state.checkDocComments = true
        fixAndCheckResult(
            "a.dart",
            "/// 软件\nvoid main() {}",
            "/// 軟體\nvoid main() {}",
            CsleDartInspection()
        )
    }
}
