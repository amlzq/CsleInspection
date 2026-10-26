package com.amlzq.csle.inspection.lang

import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.amlzq.csle.inspection.CsleLocalInspectionTool
import com.amlzq.csle.inspection.settings.CsleGlyphs
import com.amlzq.csle.inspection.settings.CsleSettings
import com.amlzq.csle.inspection.util.CsleBundle
import com.amlzq.csle.inspection.util.CsleUtils
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

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
        assertEquals("Simplified Chinese characters found per the inspection settings; convert to Chinese (Taiwan)", problems[0].descriptionTemplate)
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

    fun testKotlinInterpolationIsChecked() {
        // 带插值的字符串整体检查，插值表达式本身不影响
        val text = """
            fun main() {
                val name = "x"
                val s = "软件${'$'}{name}软体"
            }
        """.trimIndent()

        assertEquals(1, inspect("a.kt", text, CsleKotlinInspection()).size)
    }

    fun testKotlinDocComments() {
        val text = "/** 软件 */\nfun main() {}"

        assertEquals(0, inspect("a.kt", text, CsleKotlinInspection()).size)

        CsleSettings.instance.state.checkDocComments = true
        val problems = inspect("a.kt", text, CsleKotlinInspection())
        assertEquals(1, problems.size)
        assertEquals("Simplified Chinese characters found per the inspection settings; convert to Chinese (Taiwan)", problems[0].descriptionTemplate)
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
        assertEquals(
            CsleBundle.message("problem.description", CsleGlyphs.TRADITIONAL.label, CsleGlyphs.SIMPLIFIED.label),
            problems[0].descriptionTemplate,
        )
    }

    fun testKotlinHongKongQuickFixDescription() {
        CsleSettings.instance.state.quickFix = CsleGlyphs.HONGKONG.label
        val problems = inspect("a.kt", "const val SOFTWARE = \"软件\"", CsleKotlinInspection())
        assertEquals(1, problems.size)
        assertEquals(
            CsleBundle.message("problem.description", CsleGlyphs.SIMPLIFIED.label, CsleGlyphs.HONGKONG.label),
            problems[0].descriptionTemplate,
        )
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

    fun testJavaCharLiteral() {
        // 字符字面量也是 PsiLiteralExpression，同样要检查
        assertEquals(1, inspect("A.java", "public class A { char c = '软'; }", CsleJavaInspection()).size)
    }

    fun testJavaExcludedMethodMatchesAncestorCall() {
        // 嵌套调用：外层方法在排除列表中时，内层参数也不检查
        val text = """
            public class A {
                void outer(String s) {}
                void inner(String s) {}
                void m() {
                    outer(inner("软件"));
                }
            }
        """.trimIndent()

        assertEquals(1, inspect("A.java", text, CsleJavaInspection()).size)

        CsleSettings.instance.state.excludedJava = listOf("outer")
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

    fun testJsDocComments() {
        val text = "/**\n * 软件\n */\nvar a = 1;"

        assertEquals(0, inspect("a.js", text, CsleJSInspection()).size)

        CsleSettings.instance.state.checkDocComments = true
        assertEquals(1, inspect("a.js", text, CsleJSInspection()).size)
    }

    fun testJsxAttributeValue() {
        // JSX 属性值在 PSI 里是 JS 字符串字面量
        assertEquals(1, inspect("a.jsx", """<div title="软件"></div>""", CsleJSInspection()).size)
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

    fun testJsonArrayElementExcludedByParentPath() {
        // 数组元素没有自己的字段名，路径取其所属字段
        val text = """{"a": ["软件"]}"""
        assertEquals(1, inspect("a.json", text, CsleJsonInspection()).size)

        CsleSettings.instance.state.excludedJson = listOf("a")
        assertEquals(0, inspect("a.json", text, CsleJsonInspection()).size)
    }

    fun testJsonKeyIsChecked() {
        // 键名同样是字符串字面量
        assertEquals(1, inspect("a.json", """{"软件": "a"}""", CsleJsonInspection()).size)
    }

    // ---------- XML ----------

    fun testXmlTextAndAttributeValues() {
        val problems = inspect("a.xml", """
            <resources>
                <string name="app_name">软件</string>
                <string name="tip" label="软件">中文</string>
            </resources>
        """.trimIndent(), CsleXMLInspection())
        assertEquals(2, problems.size)
    }

    fun testXmlExcludedTag() {
        val text = """
            <resources>
                <string name="app_name">软件</string>
            </resources>
        """.trimIndent()

        assertEquals(1, inspect("a.xml", text, CsleXMLInspection()).size)

        CsleSettings.instance.state.excludedXml = listOf("string")
        assertEquals(0, inspect("a.xml", text, CsleXMLInspection()).size)
    }

    fun testXmlExcludedAncestorTag() {
        // 标签排除沿祖先链匹配
        val text = "<a><b>软件</b></a>"

        assertEquals(1, inspect("a.xml", text, CsleXMLInspection()).size)

        CsleSettings.instance.state.excludedXml = listOf("a")
        assertEquals(0, inspect("a.xml", text, CsleXMLInspection()).size)
    }

    fun testXmlComments() {
        val text = """
            <resources>
                <!-- 软件 -->
            </resources>
        """.trimIndent()

        // XML 注释并非编程语言的文档注释，不参与检查
        assertEquals(0, inspect("a.xml", text, CsleXMLInspection()).size)

        CsleSettings.instance.state.checkDocComments = true
        assertEquals(0, inspect("a.xml", text, CsleXMLInspection()).size)
    }

    fun testXmlInspectionSkipsHtmlFile() {
        // 回归：HTML 是 XML 的方言，XML 检查器不处理 HTML 文件（由 CsleHTMLInspection 负责），
        // 避免两个检查项重复报告
        val text = """
            <html>
            <body>
            <p title="软件"><!-- 软件 -->软件</p>
            </body>
            </html>
        """.trimIndent()

        assertEquals(0, inspect("a.html", text, CsleXMLInspection()).size)
    }

    fun testHtmlInspectionHandlesHtmlFile() {
        // HTML 文件由 CsleHTMLInspection 处理：属性值与文本参与检查，注释不参与
        val text = """
            <html>
            <body>
            <p title="软件"><!-- 软件 -->软件</p>
            </body>
            </html>
        """.trimIndent()

        // 属性 + 文本 = 2，注释不计入
        assertEquals(2, inspect("a.html", text, CsleHTMLInspection()).size)
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
        """.trimIndent(), CsleHTMLInspection())
        // 属性 + 文本 + 内嵌脚本中的字符串字面量
        assertEquals(3, problems.size)
    }

    fun testHtmlInlineScriptStringLiterals() {
        // 回归：HTML 中 <script> 的内容由 JavaScript 解析器在 HTML 文件内直接解析成
        // JSEmbeddedContent，不是注入片段，注册为 JavaScript 语言的 CsleJSInspection
        // 收不到 HTML 文件，必须由 HTML 检查器复用 JS 检查逻辑
        val text = """
            <script>
                var double = "软件";
                var single = '软件';
                var template = `软件`;
                var shared = "中文";
            </script>
        """.trimIndent()

        val problems = inspect("a.html", text, CsleHTMLInspection())
        assertEquals(3, problems.size)
        assertEquals("Simplified Chinese characters found per the inspection settings; convert to Chinese (Taiwan)", problems[0].descriptionTemplate)
    }

    fun testHtmlInlineScriptExcludedCallExpression() {
        // 内嵌脚本沿用 JavaScript 的排除列表
        val text = """
            <script>
                console.log("软件");
                var kept = "软件";
            </script>
        """.trimIndent()

        assertEquals(2, inspect("a.html", text, CsleHTMLInspection()).size)

        CsleSettings.instance.state.excludedJs = listOf("console.log")
        assertEquals(1, inspect("a.html", text, CsleHTMLInspection()).size)
    }

    fun testHtmlInlineStyleStringLiterals() {
        // 回归：HTML 中 <style> 的内容由 CSS 解析器在 HTML 文件内直接解析成 CssString，
        // 不是注入片段，注册为 CSS 语言的 CsleCSSInspection 收不到 HTML 文件，
        // 必须由 HTML 检查器复用 CSS 检查逻辑
        val text = """
            <html>
            <head>
                <style>
                    .before::before { content: "软件"; }
                    .font { font-family: '软件'; }
                </style>
            </head>
            </html>
        """.trimIndent()

        val problems = inspect("a.html", text, CsleHTMLInspection())
        assertEquals(2, problems.size)
        assertEquals("Simplified Chinese characters found per the inspection settings; convert to Chinese (Taiwan)", problems[0].descriptionTemplate)
    }

    fun testHtmlInlineStyleExcludedProperty() {
        // 内嵌样式表沿用 CSS 的排除列表（按属性名）
        val text = "<html><head><style>.a{content:\"软件\";font-family:'软件'}</style></head></html>"

        assertEquals(2, inspect("a.html", text, CsleHTMLInspection()).size)

        CsleSettings.instance.state.excludedCss = listOf("content")
        assertEquals(1, inspect("a.html", text, CsleHTMLInspection()).size)
    }

    fun testHtmlStyleAttributeReportedOnce() {
        // style="..." 内联样式由属性值检查处理，不能被内嵌样式表逻辑重复报告
        val text = "<p style=\"content:'软件'\">x</p>"
        assertEquals(1, inspect("a.html", text, CsleHTMLInspection()).size)
    }

    fun testHtmlExcludedTag() {
        val text = """<p title="软件">软件</p>"""

        assertEquals(2, inspect("a.html", text, CsleHTMLInspection()).size)

        CsleSettings.instance.state.excludedHtml = listOf("p")
        assertEquals(0, inspect("a.html", text, CsleHTMLInspection()).size)
    }

    fun testHtmlComments() {
        // HTML 注释并非编程语言的文档注释，不参与检查。
        // 注意：HTML 中注释被 XmlText 包裹，必须用 value（不含注释）而非 text 检查，
        // 否则空白包围的注释会随 XmlText 一起被误报。
        val text = """
            <html>
            <body>
            <p>
              <!-- 软件 -->
            </p>
            </body>
            </html>
        """.trimIndent()

        assertEquals(0, inspect("a.html", text, CsleHTMLInspection()).size)

        CsleSettings.instance.state.checkDocComments = true
        assertEquals(0, inspect("a.html", text, CsleHTMLInspection()).size)
    }

    fun testHtmlExcludedTagAtAttribute() {
        val text = """<p title="软件">软件</p>"""
        CsleSettings.instance.state.excludedHtml = listOf("p@title")
        assertEquals(1, inspect("a.html", text, CsleHTMLInspection()).size)
    }

    fun testHtmlExcludedAttributeName() {
        // 只写属性名即可排除该属性，且排除项会去掉首尾空白、忽略大小写
        val text = """<p title="软件">软件</p>"""
        CsleSettings.instance.state.excludedHtml = listOf(" TITLE ")
        assertEquals(1, inspect("a.html", text, CsleHTMLInspection()).size)
    }

    fun testHtmlExcludedTagIgnoreCase() {
        val text = """<p title="软件">软件</p>"""
        CsleSettings.instance.state.excludedHtml = listOf("P")
        assertEquals(0, inspect("a.html", text, CsleHTMLInspection()).size)
    }

    fun testHtmlExcludedTagDotAttribute() {
        // tag.attr 写法只排除该标签的属性值，标签内的文本仍检查
        val text = """<p title="软件">软件</p>"""
        CsleSettings.instance.state.excludedHtml = listOf("p.title")
        assertEquals(1, inspect("a.html", text, CsleHTMLInspection()).size)
    }

    fun testHtmlTextAfterComment() {
        val text = """<p><!-- 注释 -->软件</p>"""
        assertEquals(1, inspect("a.html", text, CsleHTMLInspection()).size)
    }

    fun testHtmlScriptAttributeCheckedWithExclusionsConfigured() {
        // script 的文本内容不按标记文本检查，但它的属性值（src 等）仍要检查，
        // 否则配置了任意排除项后这些属性会被静默跳过
        val text = """<script src="软件.js"></script>"""
        assertEquals(1, inspect("a.html", text, CsleHTMLInspection()).size)

        CsleSettings.instance.state.excludedHtml = listOf("p")
        assertEquals(1, inspect("a.html", text, CsleHTMLInspection()).size)
    }

    fun testHtmlEmbeddedScriptAndStyleRespectLiteralSwitch() {
        // 总开关关闭时，内嵌脚本与样式表也不能报告
        val text = """
            <html><head><style>.a{content:"软件"}</style></head>
            <body><script>var s = "软件";</script></body></html>
        """.trimIndent()

        assertEquals(2, inspect("a.html", text, CsleHTMLInspection()).size)

        CsleSettings.instance.state.checkLiteralExpression = false
        assertEquals(0, inspect("a.html", text, CsleHTMLInspection()).size)
    }

    // ---------- CSS ----------

    fun testCssStrings() {
        val problems = inspect("a.css", """
            .a::before { content: "软件"; }
            .b { font-family: "中文"; }
        """.trimIndent(), CsleCSSInspection())
        assertEquals(1, problems.size)
    }

    fun testCssExcludedProperty() {
        val text = """.a::before { content: "软件"; }"""

        assertEquals(1, inspect("a.css", text, CsleCSSInspection()).size)

        CsleSettings.instance.state.excludedCss = listOf("content")
        assertEquals(0, inspect("a.css", text, CsleCSSInspection()).size)
    }

    fun testCssExcludedPropertyNormalized() {
        // 属性名忽略大小写与首尾空白；自定义属性的 -- 前缀可省略
        val text = """.a { Content: "软件"; --Brand: "软体"; }"""
        assertEquals(2, inspect("a.css", text, CsleCSSInspection()).size)

        CsleSettings.instance.state.excludedCss = listOf(" CONTENT ")
        assertEquals(1, inspect("a.css", text, CsleCSSInspection()).size)

        CsleSettings.instance.state.excludedCss = listOf("content", "brand")
        assertEquals(0, inspect("a.css", text, CsleCSSInspection()).size)
    }

    fun testCssLikeLanguages() {
        // CSS 检查器同时处理 SCSS / LESS
        val text = """.a::before { content: "软件"; }"""
        assertEquals(1, inspect("a.scss", text, CsleCSSInspection()).size)
        assertEquals(1, inspect("a.less", text, CsleCSSInspection()).size)
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

    fun testPythonDocstringCheckedAsLiteral() {
        // Python 没有单独的文档注释语法，文档字符串就是字符串字面量
        val text = "def f():\n    \"\"\"软件\"\"\""

        assertEquals(1, inspect("a.py", text, CslePythonInspection()).size)

        CsleSettings.instance.state.checkLiteralExpression = false
        assertEquals(0, inspect("a.py", text, CslePythonInspection()).size)
    }

    fun testPythonDocstringIgnoresDocCommentsSwitch() {
        val text = "def f():\n    \"\"\"软件\"\"\""

        // “文档注释”开关对 Python 不适用：docstring 始终按字符串字面量处理
        CsleSettings.instance.state.checkDocComments = true
        assertEquals(1, inspect("a.py", text, CslePythonInspection()).size)

        CsleSettings.instance.state.checkDocComments = false
        assertEquals(1, inspect("b.py", text, CslePythonInspection()).size)

        // 三引号单引号（'''...'''）同样是 PyStringLiteralExpression
        assertEquals(1, inspect("c.py", "def f():\n    '''软件'''", CslePythonInspection()).size)
    }

    // ---------- PHP ----------

    fun testPhpStringLiterals() {
        val problems = inspect("a.php", """
            <?php
            ${'$'}kept = "软件";
        """.trimIndent(), CslePHPInspection())
        assertEquals(1, problems.size)
    }

    fun testPhpExcludedEcho() {
        val text = """
            <?php
            ${'$'}kept = "软件";
            echo "软件";
        """.trimIndent()

        assertEquals(2, inspect("a.php", text, CslePHPInspection()).size)

        CsleSettings.instance.state.excludedPhp = listOf("echo")
        assertEquals(1, inspect("a.php", text, CslePHPInspection()).size)
    }

    fun testPhpDocComments() {
        val text = "<?php\n/** 软件 */\nfunction f() {}"

        assertEquals(0, inspect("a.php", text, CslePHPInspection()).size)

        CsleSettings.instance.state.checkDocComments = true
        assertEquals(1, inspect("a.php", text, CslePHPInspection()).size)
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

    fun testDartDocComments() {
        // `///` 是普通 PsiComment，`/** */` 才是 DartDocComment，两者都要覆盖
        val lineDoc = "/// 软件\nvoid main() {}"
        val blockDoc = "/** 软件 */\nvoid main() {}"

        assertEquals(0, inspect("a.dart", lineDoc, CsleDartInspection()).size)
        assertEquals(0, inspect("b.dart", blockDoc, CsleDartInspection()).size)

        CsleSettings.instance.state.checkDocComments = true
        assertEquals(1, inspect("c.dart", lineDoc, CsleDartInspection()).size)
        assertEquals(1, inspect("d.dart", blockDoc, CsleDartInspection()).size)
    }

    // ---------- 编程语言 x 自然语言矩阵 ----------

    fun testLiteralSwitchDisablesAllLanguages() {
        // 字面量总开关对所有语言的检查器都生效
        val snippets = mapOf(
            "a.kt" to "const val S = \"软件\"",
            "A.java" to "public class A { String s = \"软件\"; }",
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
            "a.xml" to CsleXMLInspection(),
            "a.html" to CsleHTMLInspection(),
            "a.css" to CsleCSSInspection(),
            "a.py" to CslePythonInspection(),
            "a.php" to CslePHPInspection(),
            "a.dart" to CsleDartInspection(),
        )

        CsleSettings.instance.state.checkLiteralExpression = false
        for ((fileName, tool) in tools) {
            val problems = inspect(fileName, snippets.getValue(fileName), tool)
            assertEquals("$fileName 关闭字面量检查后不应报告", 0, problems.size)
        }
    }

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
            "a.xml" to CsleXMLInspection(),
            "a.html" to CsleHTMLInspection(),
            "a.css" to CsleCSSInspection(),
            "a.py" to CslePythonInspection(),
            "a.php" to CslePHPInspection(),
            "a.dart" to CsleDartInspection(),
        )

        for ((fileName, tool) in tools) {
            val problems = inspect(fileName, snippets.getValue(fileName), tool)
            assertEquals("$fileName 应报告 1 个问题", 1, problems.size)
            assertEquals("Simplified Chinese characters found per the inspection settings; convert to Chinese (Taiwan)", problems[0].descriptionTemplate)
        }
    }

    fun testProblemDescriptionNamesInspectAndTargetGlyphs() {
        // 描述要同时点明“检查到的字形”和“可转换成的目标字形”
        val description = inspect("a.kt", "const val S = \"软件\"", CsleKotlinInspection())
            .single().descriptionTemplate

        assertTrue(description.contains(CsleGlyphs.SIMPLIFIED.label))
        assertTrue(description.contains(CsleGlyphs.TAIWAN.label))
        // 点明报告来自“检查设置”，用户才知道可以到设置里调整检查目标
        assertTrue(description.contains("inspection settings"))
        assertEquals(
            CsleBundle.message(
                "problem.description",
                CsleGlyphs.SIMPLIFIED.label,
                CsleGlyphs.TAIWAN.label,
            ),
            description,
        )
    }

    fun testQuickFixNameIsAnActionNotADescription() {
        // Alt+Enter 菜单里应是动作名，不能直接复用“……可转换为……”的问题描述
        val quickFixName = CsleUtils.quickFixName()

        assertEquals(CsleBundle.message("quickfix.name", CsleGlyphs.TAIWAN.label), quickFixName)
        assertNotEquals(quickFixName, CsleUtils.problemDescription())
    }
}
