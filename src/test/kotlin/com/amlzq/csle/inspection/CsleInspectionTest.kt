package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import org.junit.Assert.assertEquals

/**
 * 编程语言 x 自然语言：各编程语言源码中汉字字面量的检查行为。
 *
 * 直接调用 [CsleLocalInspectionTool.checkFile]，避免其他默认检查干扰。
 */
class CsleInspectionTest : CsleBasePlatformTest() {

    private fun inspect(
        fileName: String,
        text: String,
        tool: CsleLocalInspectionTool
    ): Array<ProblemDescriptor> {
        val file = myFixture.configureByText(fileName, text)
        return tool.checkFile(file, InspectionManager.getInstance(file.project), true) ?: emptyArray()
    }

    // ---------- Kotlin ----------

    fun testKotlinLiteralExpressions() {
        val problems = inspect("a.kt", """
            const val SOFTWARE = "软件"
            const val SHARED = "中文"
            const val ASCII = "abc 123 .,"
            const val MIXED = "Hello 软件 world"
        """.trimIndent(), CsleKotlinInspection())

        assertEquals(2, problems.size)
        assertEquals("Convert to Chinese (Taiwan)", problems[0].descriptionTemplate)
    }

    fun testKotlinRawStringLiteral() {
        val problems = inspect("a.kt", "const val RAW = \"\"\"软件\"\"\"", CsleKotlinInspection())
        assertEquals(1, problems.size)
    }

    fun testKotlinExcludedCallExpression() {
        val text = """
            fun main() {
                fun log(message: String) {}
                val kept = "软件"
                log("软件")
                log(kept)
            }
        """.trimIndent()

        assertEquals(2, inspect("a.kt", text, CsleKotlinInspection()).size)

        CsleSettings.instance.state.excludedKotlin = listOf("log")
        assertEquals(1, inspect("a.kt", text, CsleKotlinInspection()).size)
    }

    fun testKotlinDocComments() {
        val text = "/** 软件 */\nfun main() {}"

        assertEquals(0, inspect("a.kt", text, CsleKotlinInspection()).size)

        CsleSettings.instance.state.checkDocComments = true
        val problems = inspect("a.kt", text, CsleKotlinInspection())
        assertEquals(1, problems.size)
        assertEquals("Convert to Chinese (Taiwan)", problems[0].descriptionTemplate)
    }

    fun testKotlinLiteralInspectionDisabled() {
        CsleSettings.instance.state.checkLiteralExpression = false
        val problems = inspect("a.kt", "const val SOFTWARE = \"软件\"", CsleKotlinInspection())
        assertEquals(0, problems.size)
    }

    fun testKotlinTraditionalSourceWithSimplifiedInspection() {
        // 默认 inspect=简体、quickFix=繁体(台湾)：繁体原文转换后相同，不报告
        val problems = inspect("a.kt", "const val T = \"軟體\"", CsleKotlinInspection())
        assertEquals(0, problems.size)
    }

    fun testKotlinTraditionalSourceWithTraditionalInspection() {
        CsleSettings.instance.state.inspect = CsleGlyphs.TRADITIONAL.label
        CsleSettings.instance.state.quickFix = CsleGlyphs.SIMPLIFIED.label
        val problems = inspect("a.kt", "const val T = \"軟體\"", CsleKotlinInspection())
        assertEquals(1, problems.size)
        assertEquals("Convert to Simplified Chinese", problems[0].descriptionTemplate)
    }

    fun testKotlinHongKongQuickFixDescription() {
        CsleSettings.instance.state.quickFix = CsleGlyphs.HONGKONG.label
        val problems = inspect("a.kt", "const val SOFTWARE = \"软件\"", CsleKotlinInspection())
        assertEquals(1, problems.size)
        assertEquals("Convert to Chinese (Hong Kong)", problems[0].descriptionTemplate)
    }

    // ---------- Java ----------

    fun testJavaLiteralExpressions() {
        val problems = inspect("A.java", """
            public class A {
                public static final String SOFTWARE = "软件";
                public static final String SHARED = "中文";
            }
        """.trimIndent(), CsleJavaInspection())
        assertEquals(1, problems.size)
    }

    fun testJavaExcludedMethod() {
        val text = """
            public class A {
                public void log() {
                    System.out.println("软件");
                }
            }
        """.trimIndent()

        assertEquals(1, inspect("A.java", text, CsleJavaInspection()).size)

        CsleSettings.instance.state.excludedJava = listOf("println")
        assertEquals(0, inspect("A.java", text, CsleJavaInspection()).size)
    }

    fun testJavaDocComments() {
        val text = """
            public class A {
                /** 软件 */
                public void m() {}
            }
        """.trimIndent()

        assertEquals(0, inspect("A.java", text, CsleJavaInspection()).size)

        CsleSettings.instance.state.checkDocComments = true
        assertEquals(1, inspect("A.java", text, CsleJavaInspection()).size)
    }

    // ---------- JavaScript / TypeScript ----------

    fun testJsStringAndTemplateLiterals() {
        val problems = inspect("a.js", """
            var single = '软件';
            var double = "软件";
            var template = `软件`;
            var shared = "中文";
        """.trimIndent(), CsleJSInspection())
        assertEquals(3, problems.size)
    }

    fun testJsExcludedCallExpression() {
        val text = """
            console.log("软件");
            var kept = "软件";
        """.trimIndent()

        assertEquals(2, inspect("a.js", text, CsleJSInspection()).size)

        CsleSettings.instance.state.excludedJs = listOf("console.log")
        assertEquals(1, inspect("a.js", text, CsleJSInspection()).size)
    }

    // ---------- JSON ----------

    fun testJsonStringLiterals() {
        val problems = inspect("a.json", """
            {"title": "软件", "note": "中文"}
        """.trimIndent(), CsleJsonInspection())
        assertEquals(1, problems.size)
    }

    fun testJsonExcludedKey() {
        val text = """{"title": "软件"}"""
        CsleSettings.instance.state.excludedJson = listOf("title")
        assertEquals(0, inspect("a.json", text, CsleJsonInspection()).size)
    }

    fun testJsonExcludedNestedPath() {
        val text = """{"a": {"title": "软件"}}"""
        CsleSettings.instance.state.excludedJson = listOf("a.title")
        assertEquals(0, inspect("a.json", text, CsleJsonInspection()).size)
    }

    fun testJsonExcludedPathMismatch() {
        val text = """{"a": {"title": "软件"}}"""
        CsleSettings.instance.state.excludedJson = listOf("b.title")
        assertEquals(1, inspect("a.json", text, CsleJsonInspection()).size)
    }

    // ---------- XML ----------

    fun testXmlTextAndAttributeValues() {
        val problems = inspect("a.xml", """
            <resources>
                <string name="app_name">软件</string>
                <string name="tip" label="软件">中文</string>
            </resources>
        """.trimIndent(), CsleXmlInspection())
        assertEquals(2, problems.size)
    }

    fun testXmlExcludedTag() {
        val text = """
            <resources>
                <string name="app_name">软件</string>
            </resources>
        """.trimIndent()

        assertEquals(1, inspect("a.xml", text, CsleXmlInspection()).size)

        CsleSettings.instance.state.excludedXml = listOf("string")
        assertEquals(0, inspect("a.xml", text, CsleXmlInspection()).size)
    }

    fun testXmlComments() {
        val text = """
            <resources>
                <!-- 软件 -->
            </resources>
        """.trimIndent()

        assertEquals(0, inspect("a.xml", text, CsleXmlInspection()).size)

        CsleSettings.instance.state.checkDocComments = true
        assertEquals(1, inspect("a.xml", text, CsleXmlInspection()).size)
    }

    // ---------- HTML ----------

    fun testHtmlTextAndAttributeValues() {
        val problems = inspect("a.html", """
            <html>
            <body>
            <p title="软件">软件</p>
            <script>var a = "软件";</script>
            </body>
            </html>
        """.trimIndent(), CsleHtmlInspection())
        assertEquals(2, problems.size)
    }

    fun testHtmlExcludedTag() {
        val text = """<p title="软件">软件</p>"""

        assertEquals(2, inspect("a.html", text, CsleHtmlInspection()).size)

        CsleSettings.instance.state.excludedHtml = listOf("p")
        assertEquals(0, inspect("a.html", text, CsleHtmlInspection()).size)
    }

    fun testHtmlExcludedTagAtAttribute() {
        val text = """<p title="软件">软件</p>"""
        CsleSettings.instance.state.excludedHtml = listOf("p@title")
        assertEquals(1, inspect("a.html", text, CsleHtmlInspection()).size)
    }

    // ---------- CSS ----------

    fun testCssStrings() {
        val problems = inspect("a.css", """
            .a::before { content: "软件"; }
            .b { font-family: "中文"; }
        """.trimIndent(), CsleCssInspection())
        assertEquals(1, problems.size)
    }

    fun testCssExcludedProperty() {
        val text = """.a::before { content: "软件"; }"""

        assertEquals(1, inspect("a.css", text, CsleCssInspection()).size)

        CsleSettings.instance.state.excludedCss = listOf("content")
        assertEquals(0, inspect("a.css", text, CsleCssInspection()).size)
    }

    // ---------- Python ----------

    fun testPythonStringLiterals() {
        val problems = inspect("a.py", """
            kept = "软件"
            print(kept)
        """.trimIndent(), CslePythonInspection())
        assertEquals(1, problems.size)
    }

    fun testPythonExcludedFunction() {
        val text = """
            kept = "软件"
            print("软件")
        """.trimIndent()

        assertEquals(2, inspect("a.py", text, CslePythonInspection()).size)

        CsleSettings.instance.state.excludedPython = listOf("print")
        assertEquals(1, inspect("a.py", text, CslePythonInspection()).size)
    }

    // ---------- PHP ----------

    fun testPhpStringLiterals() {
        val problems = inspect("a.php", """
            <?php
            ${'$'}kept = "软件";
        """.trimIndent(), CslePhpInspection())
        assertEquals(1, problems.size)
    }

    fun testPhpExcludedEcho() {
        val text = """
            <?php
            ${'$'}kept = "软件";
            echo "软件";
        """.trimIndent()

        assertEquals(2, inspect("a.php", text, CslePhpInspection()).size)

        CsleSettings.instance.state.excludedPhp = listOf("echo")
        assertEquals(1, inspect("a.php", text, CslePhpInspection()).size)
    }

    // ---------- Dart ----------

    fun testDartStringLiterals() {
        val problems = inspect("a.dart", """
            var kept = "软件";
        """.trimIndent(), CsleDartInspection())
        assertEquals(1, problems.size)
    }

    fun testDartExcludedFunction() {
        val text = """
            void main() {
                var kept = "软件";
                print("软件");
            }
        """.trimIndent()

        assertEquals(2, inspect("a.dart", text, CsleDartInspection()).size)

        CsleSettings.instance.state.excludedDart = listOf("print")
        assertEquals(1, inspect("a.dart", text, CsleDartInspection()).size)
    }

    // ---------- 编程语言 x 自然语言矩阵 ----------

    fun testSameNaturalLanguageTextAcrossProgrammingLanguages() {
        val snippets = mapOf(
            "a.kt" to "const val S = \"软件\"",
            "A.java" to "public class A { public static final String S = \"软件\"; }",
            "a.js" to "var s = \"软件\";",
            "a.json" to "{\"k\": \"软件\"}",
            "a.xml" to "<r><s>软件</s></r>",
            "a.html" to "<p>软件</p>",
            "a.css" to ".a::before { content: \"软件\"; }",
            "a.py" to "s = \"软件\"",
            "a.php" to "<?php \$s = \"软件\";",
            "a.dart" to "var s = \"软件\";",
        )
        val tools = mapOf(
            "a.kt" to CsleKotlinInspection(),
            "A.java" to CsleJavaInspection(),
            "a.js" to CsleJSInspection(),
            "a.json" to CsleJsonInspection(),
            "a.xml" to CsleXmlInspection(),
            "a.html" to CsleHtmlInspection(),
            "a.css" to CsleCssInspection(),
            "a.py" to CslePythonInspection(),
            "a.php" to CslePhpInspection(),
            "a.dart" to CsleDartInspection(),
        )

        for ((fileName, tool) in tools) {
            val problems = inspect(fileName, snippets.getValue(fileName), tool)
            assertEquals("$fileName 应报告 1 个问题", 1, problems.size)
            assertEquals("Convert to Chinese (Taiwan)", problems[0].descriptionTemplate)
        }
    }
}
