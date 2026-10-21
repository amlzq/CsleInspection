package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.xml.*
import org.jetbrains.annotations.NotNull

/**
 * 汉字检查器：XML / HTML（标记语言家族）的共享基类。
 *
 * HTML 是 XML 的方言（HtmlFileImpl : XmlFileImpl），本基类承载 XML 与 HTML 的全部共享逻辑，
 * 排除语义按文件语言在 [inExcludedCallExpression] 内部分支。
 *
 * 入口由两个薄子类提供，各自对应 Inspections 列表中的独立开关：
 * - [CsleXMLInspection]：XML 组，处理 XML 文件（跳过 HTML）
 * - [CsleHTMLInspection]：HTML 组，处理 HTML 文件
 *
 * HTML 内嵌 `<script>` 的 JS 代码块与 `<style>` 的样式表由 [CsleEmbeddedJSInspection]、
 * [CsleEmbeddedCSSInspection] 复用对应语言的检查逻辑：它们不是注入片段，
 * 注册为 JavaScript / CSS 语言的检查器收不到 HTML 文件。
 */
abstract class CsleMarkupInspection : CsleLocalInspectionTool() {

    /**
     * 检查元素是否在用户设置的排除列表中。
     *
     * HTML：script/style 内容不按标记文本检查（script 的 JS 代码块交给 JS 逻辑），
     * 支持标签、属性、`标签@属性`、`标签.属性` 模式；
     * XML：匹配任意祖先标签名。
     */
    override fun inExcludedCallExpression(element: PsiElement): Boolean {
        val isHtml = element.containingFile?.language?.id.equals("HTML", ignoreCase = true)
        return if (isHtml) {
            inHtmlExcluded(element)
        } else {
            inXmlExcluded(element)
        }
    }

    private fun inHtmlExcluded(element: PsiElement): Boolean {
        val tag = generateSequence(element) { it.parent }
            .filterIsInstance<HtmlTag>()
            .firstOrNull()

        val tagName = tag?.name?.lowercase()
        // script/style 的文本内容不是标记文本（字符串由 JS/CSS 逻辑处理），
        // 但属性值（如 src、style）仍按属性检查，否则配置任意排除项后会被静默跳过
        if (element !is XmlAttributeValue && (tagName == "script" || tagName == "style")) return true

        val excluded = CsleSettings.instance.state.excludedHtml
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
            .toSet()
        if (excluded.isEmpty()) return false

        if (tagName != null && excluded.contains(tagName)) return true

        if (element is XmlAttributeValue) {
            val attribute = element.parent as? XmlAttribute ?: return false
            val attrName = attribute.name.lowercase()
            if (excluded.contains(attrName)) return true
            if (tagName != null) {
                if (excluded.contains("$tagName.$attrName")) return true
                if (excluded.contains("$tagName@$attrName")) return true
            }
        }

        return false
    }

    private fun inXmlExcluded(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            if (parent is XmlTag) {
                val tagName = parent.name.lowercase()
                for (excludedName in CsleSettings.instance.state.excludedXml) {
                    if (excludedName.lowercase() == tagName) {
                        return true
                    }
                }
            }
            parent = parent.parent
        }
        return false
    }

    @NotNull
    override fun checkFile(
        @NotNull file: PsiFile,
        @NotNull manager: InspectionManager,
        isOnTheFly: Boolean
    ): @NotNull Array<ProblemDescriptor>? {
        if (!isOnTheFly) return null
        // HTML 是 XML 的方言，HtmlFile 同样是 XmlFile；具体处理哪种语言由子类守卫决定
        if (file !is XmlFile) return null

        val virtualFile: VirtualFile? = CsleUtils.getRealVirtualFile(file)
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) return null

        val project: Project = file.project
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return null

        val problems = mutableListOf<ProblemDescriptor>()
        val reportedRanges = HashSet<TextRange>()

        // HTML 的 <script>/<style> 内容由对应语言的解析器在 HTML 文件内直接解析，
        // 不是注入片段，注册为 JavaScript / CSS 语言的检查器收不到 HTML 文件，
        // 内嵌脚本与样式表里的字符串因此一直没有告警；这里复用 JS / CSS 检查逻辑
        val isHtml = file.language.id.equals("HTML", ignoreCase = true)
        val inspectEmbeddedJs = isHtml && javaScriptAvailable
        val inspectEmbeddedCss = isHtml && cssAvailable

        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                // 内嵌脚本内部是 JS / JSX，交给 JS 逻辑处理；
                // 不再向下遍历，避免脚本内的 JSX 属性被当成 HTML 属性检查
                if (inspectEmbeddedJs && CsleEmbeddedJSInspection.isEmbeddedContent(element)) {
                    if (CsleSettings.instance.state.checkLiteralExpression) {
                        CsleEmbeddedJSInspection.collectProblems(element, manager, isOnTheFly, problems)
                    }
                    return
                }

                // 内嵌样式表内部是 CSS，交给 CSS 逻辑处理；
                // 内联 style 属性由下面的属性值检查处理，这里不处理
                if (inspectEmbeddedCss && CsleEmbeddedCSSInspection.isEmbeddedString(element)) {
                    if (CsleSettings.instance.state.checkLiteralExpression) {
                        CsleEmbeddedCSSInspection.checkString(element, manager, isOnTheFly)?.let(problems::add)
                    }
                    return
                }

                super.visitElement(element)

                if (!CsleSettings.instance.state.checkLiteralExpression) return

                when (element) {
                    is XmlAttributeValue -> {
                        if (inExcludedCallExpression(element)) return
                        if (!hasConvertibleChinese(element.value)) return
                        if (!reportedRanges.add(element.textRange)) return

                        problems.add(
                            manager.createProblemDescriptor(
                                element,
                                CsleUtils.problemDescription(),
                                XmlAttributeValueQuickFix(),
                                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                                isOnTheFly,
                            )
                        )
                    }

                    is XmlText -> {
                        if (inExcludedCallExpression(element)) return
                        // 用 value 而非 text：HTML 中注释是 XmlText 的子节点，
                        // text 包含注释内容会误报注释，value 只取文本自身内容
                        if (!hasConvertibleChinese(element.value)) return
                        if (!reportedRanges.add(element.textRange)) return

                        // 元素内含注释时按不含注释的文本段分别报告，避免高亮覆盖注释
                        for (range in nonCommentRanges(element)) {
                            problems.add(
                                manager.createProblemDescriptor(
                                    element,
                                    range,
                                    CsleUtils.problemDescription(),
                                    ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                                    isOnTheFly,
                                    XmlTextQuickFix(),
                                )
                            )
                        }
                    }
                }
            }
        })

        return problems.toTypedArray()
    }

    private companion object {
        /**
         * JavaScript 插件是否可用。
         *
         * 没装插件时 `<script>` 的内容只是普通文本，不会出现 JSEmbeddedContent；
         * 按类名探测，避免插件缺失时加载 JavaScript 插件的类。
         */
        val javaScriptAvailable: Boolean by lazy {
            try {
                Class.forName("com.intellij.lang.javascript.psi.JSEmbeddedContent")
                true
            } catch (_: Throwable) {
                false
            }
        }

        /**
         * CSS 插件是否可用。
         *
         * 没装插件时 `<style>` 的内容只是普通文本，不会出现 CssString；
         * 按类名探测，避免插件缺失时加载 CSS 插件的类。
         */
        val cssAvailable: Boolean by lazy {
            try {
                Class.forName("com.intellij.psi.css.CssString")
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun hasConvertibleChinese(original: String): Boolean {
        if (!containsChinese(original)) return false
        val cleaned = cleanPattern.matcher(original).replaceAll("")
        if (!containsChinese(cleaned)) return false

        val cleanedConverted = getConvertedText(cleaned)
        if (cleaned == cleanedConverted) return false

        val converted = getConvertedText(original)
        return converted != original
    }

    /**
     * 计算 XmlText 中不含注释子节点的文本范围（相对于元素的 TextRange）。
     *
     * HTML 中注释是 XmlText 的子节点，检查与高亮都应排除注释；
     * 无注释子节点时返回整个元素的范围。
     */
    private fun nonCommentRanges(element: XmlText): List<TextRange> {
        val comments = element.children.filterIsInstance<XmlComment>()
        if (comments.isEmpty()) return listOf(TextRange(0, element.textLength))

        val ranges = mutableListOf<TextRange>()
        var cursor = 0
        for (comment in comments) {
            val start = comment.startOffsetInParent
            if (start > cursor) {
                ranges.add(TextRange(cursor, start))
            }
            cursor = start + comment.textLength
        }
        if (cursor < element.textLength) {
            ranges.add(TextRange(cursor, element.textLength))
        }
        return ranges
    }
}

class XmlAttributeValueQuickFix : CsleLocalQuickFix() {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? XmlAttributeValue ?: return
        val attribute = element.parent as? XmlAttribute ?: return
        val text = element.value
        val newText = getConvertedText(text)
        if (text == newText) return

        WriteCommandAction.runWriteCommandAction(project) {
            attribute.setValue(newText)
        }
    }
}

class XmlTextQuickFix : CsleLocalQuickFix() {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? XmlText ?: return
        val original = element.value
        val converted = getConvertedText(original)
        if (original == converted) return

        WriteCommandAction.runWriteCommandAction(project) {
            val comments = element.children.filterIsInstance<XmlComment>()
            val newText = if (comments.isEmpty()) {
                // 纯文本节点：converted 是解码后的文本（不含 CDATA），写回时只需转义
                escapeForHtmlDummy(converted)
            } else {
                // HTML 中注释是 XmlText 的子节点：拼回"转换后的文本 + 原注释"
                val fullText = element.text
                buildString {
                    var cursor = 0
                    for (comment in comments) {
                        val start = comment.startOffsetInParent
                        if (start > cursor) {
                            append(escapeForHtmlDummy(getConvertedText(fullText.substring(cursor, start))))
                        }
                        append(comment.text)
                        cursor = start + comment.textLength
                    }
                    if (cursor < fullText.length) {
                        append(escapeForHtmlDummy(getConvertedText(fullText.substring(cursor))))
                    }
                }
            }

            // 按文本范围就地替换：用另一个文件的 XmlText 去 replace 会在注释后插入多余空格
            val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
                ?: return@runWriteCommandAction
            val range = element.textRange
            document.replaceString(range.startOffset, range.endOffset, newText)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }

    /** 转义文本以供写回标记语言，已有的实体引用不重复转义 */
    private fun escapeForHtmlDummy(text: String): String =
        text.replace(AMP_WITHOUT_ENTITY, "&amp;").replace("<", "&lt;")

    private val AMP_WITHOUT_ENTITY = Regex("&(?![A-Za-z]+;|#[0-9]+;|#[xX][0-9A-Fa-f]+;)")
}
