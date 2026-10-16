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
        myFixture.enableInspections(CsleXmlInspection::class.java)
        myFixture.configureByText(
            "a.xml",
            "<resources>\n    <string name=\"app_name\"><warning descr=\"Convert to Chinese (Taiwan)\">软件</warning></string>\n</resources>"
        )
        myFixture.checkHighlighting()
    }

    fun testHtml() {
        myFixture.enableInspections(CsleHtmlInspection::class.java)
        myFixture.configureByText(
            "a.html",
            "<p title=<warning descr=\"Convert to Chinese (Taiwan)\">\"软件\"</warning>><warning descr=\"Convert to Chinese (Taiwan)\">软件</warning></p>"
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
            CsleXmlInspection::class.java,
            CsleHtmlInspection::class.java,
            CsleCssInspection::class.java,
            CslePythonInspection::class.java,
            CslePhpInspection::class.java,
            CsleDartInspection::class.java,
        )
        myFixture.configureByText("a.kt", "const val EMPTY = \"no natural language here\"")
        myFixture.checkHighlighting()
    }
}
