package com.amlzq.csle.inspection

/**
 * 端到端高亮测试：经 daemon 在编辑器中渲染的黄色波浪线（WARNING 级问题）。
 *
 * 预期描述与默认设置对应：inspect=简体中文，quick fix=中文（台湾）。
 */
class CsleHighlightingTest : CsleBasePlatformTest() {

    fun testKotlin() {
        myFixture.enableInspections(CsleKotlinInspection::class.java)
        myFixture.configureByText(
            "a.kt",
            "const val SOFTWARE = <warning descr=\"Convert to Chinese (Taiwan)\">\"软件\"</warning>"
        )
        myFixture.checkHighlighting()
    }

    fun testJavaScript() {
        myFixture.enableInspections(CsleJSInspection::class.java)
        myFixture.configureByText(
            "a.js",
            "var s = <warning descr=\"Convert to Chinese (Taiwan)\">'软件'</warning>;"
        )
        myFixture.checkHighlighting()
    }

    fun testTypeScript() {
        myFixture.enableInspections(CsleJSInspection::class.java)
        myFixture.configureByText(
            "a.ts",
            "var s: string = <warning descr=\"Convert to Chinese (Taiwan)\">'软件'</warning>;"
        )
        myFixture.checkHighlighting()
    }

    fun testJsx() {
        myFixture.enableInspections(CsleJSInspection::class.java)
        myFixture.configureByText(
            "a.jsx",
            "const e = <div><warning descr=\"Convert to Chinese (Taiwan)\">软件</warning></div>;"
        )
        myFixture.checkHighlighting()
    }

    fun testJson() {
        myFixture.enableInspections(CsleJsonInspection::class.java)
        myFixture.configureByText(
            "a.json",
            "{\"title\": <warning descr=\"Convert to Chinese (Taiwan)\">\"软件\"</warning>, \"note\": \"中文\"}"
        )
        myFixture.checkHighlighting()
    }

    fun testXml() {
        myFixture.enableInspections(CsleXMLInspection::class.java)
        myFixture.configureByText(
            "a.xml",
            "<resources>\n    <string name=\"app_name\"><warning descr=\"Convert to Chinese (Taiwan)\">软件</warning></string>\n</resources>"
        )
        myFixture.checkHighlighting()
    }

    fun testHtml() {
        myFixture.enableInspections(CsleHTMLInspection::class.java)
        myFixture.configureByText(
            "a.html",
            "<p title=<warning descr=\"Convert to Chinese (Taiwan)\">\"软件\"</warning>><warning descr=\"Convert to Chinese (Taiwan)\">软件</warning></p>"
        )
        myFixture.checkHighlighting()
    }

    fun testHtmlCommentNotChecked() {
        // 回归：HTML 注释并非编程语言的文档注释，不参与检查。
        // HTML 注释在 PSI 中被 XmlText 包裹，必须用 value（不含注释）而非 text 检查，
        // 否则空白包围的注释会随 XmlText 一起被误报。
        myFixture.enableInspections(CsleHTMLInspection::class.java)
        myFixture.configureByText("a.html", "<p>\n  <!-- 软件 -->\n</p>")
        myFixture.checkHighlighting()
    }

    fun testHtmlFileWithCommentAndText() {
        // 回归：HTML 文件仅标记属性值与文本，注释不标记
        myFixture.enableInspections(CsleHTMLInspection::class.java)
        myFixture.configureByText(
            "a.html",
            "<p title=<warning descr=\"Convert to Chinese (Taiwan)\">\"属性\"</warning>>" +
                    "<!-- 注释 --><warning descr=\"Convert to Chinese (Taiwan)\">文本</warning></p>"
        )
        myFixture.checkHighlighting()
    }

    fun testXmlCommentNotChecked() {
        // 回归：用户调试场景，XML 注释不参与检查，仅字面量被标记
        myFixture.enableInspections(CsleXMLInspection::class.java)
        myFixture.configureByText(
            "myxml.xml",
            "<tag>\n" +
                    "    <name><warning descr=\"Convert to Chinese (Taiwan)\">雪柜</warning></name>\n" +
                    "<!--    注释启用检查了吗-->\n" +
                    "    <label name=<warning descr=\"Convert to Chinese (Taiwan)\">\"雪柜\"</warning>>" +
                    "<warning descr=\"Convert to Chinese (Taiwan)\">雪柜</warning></label>\n" +
                    "</tag>"
        )
        myFixture.checkHighlighting()
    }

    fun testCss() {
        myFixture.enableInspections(CsleCssInspection::class.java)
        myFixture.configureByText(
            "a.css",
            ".a::before { content: <warning descr=\"Convert to Chinese (Taiwan)\">\"软件\"</warning>; }"
        )
        myFixture.checkHighlighting()
    }

    fun testPython() {
        myFixture.enableInspections(CslePythonInspection::class.java)
        myFixture.configureByText(
            "a.py",
            "s = <warning descr=\"Convert to Chinese (Taiwan)\">\"软件\"</warning>"
        )
        myFixture.checkHighlighting()
    }

    fun testPhp() {
        myFixture.enableInspections(CslePhpInspection::class.java)
        myFixture.configureByText(
            "a.php",
            "<?php \$s = <warning descr=\"Convert to Chinese (Taiwan)\">\"软件\"</warning>;"
        )
        myFixture.checkHighlighting()
    }

    fun testDart() {
        myFixture.enableInspections(CsleDartInspection::class.java)
        myFixture.configureByText(
            "a.dart",
            "var s = <warning descr=\"Convert to Chinese (Taiwan)\">\"软件\"</warning>;"
        )
        myFixture.checkHighlighting()
    }

    fun testNoChineseNoHighlighting() {
        myFixture.enableInspections(
            CsleKotlinInspection::class.java,
            CsleJavaInspection::class.java,
            CsleJSInspection::class.java,
            CsleJsonInspection::class.java,
            CsleXMLInspection::class.java,
            CsleHTMLInspection::class.java,
            CsleCssInspection::class.java,
            CslePythonInspection::class.java,
            CslePhpInspection::class.java,
            CsleDartInspection::class.java,
        )
        myFixture.configureByText("a.kt", "const val EMPTY = \"no natural language here\"")
        myFixture.checkHighlighting()
    }
}
