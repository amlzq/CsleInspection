package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.xml.XmlTokenType
import org.jetbrains.annotations.NotNull

/**
 * 汉字检查器：JavaScript / TypeScript / JSX / TSX。
 *
 * JSFile 覆盖 js/ts/jsx/tsx 等整个 JS 家族语言，本检查器统一处理整个家族文件：
 * 字符串字面量（含模板字符串与 JSX 文本节点）始终参与检查；
 * JSDoc 文档注释是否参与检查由设置中的 "Doc comments" 开关控制。
 */
class CsleJSInspection : CsleLocalInspectionTool() {
    /**
     * 检查字符表达式是否在用户设置的排除方法中，比如：console.log
     */
    override fun inExcludedCallExpression(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            if (parent is JSCallExpression) {
                val text = parent.methodExpression?.text ?: ""
                for (functionName in CsleSettings.instance.state.excludedJs) {
                    if (functionName == text || text.contains(".$functionName")) {
                        debugPrintln("$text is on the exclusion list.")
                        return true
                    }
                }
            }
            parent = parent.parent
        }
        return false
    }

//    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
//        return object : JSElementVisitor() {
//            override fun visitJSLiteralExpression(node: JSLiteralExpression) {
//                super.visitJSLiteralExpression(node)
//                val value: String? = node.stringValue
//                debugPrintln("value=$value, visitJSLiteralExpression")
//            }
//
//            override fun visitJSStringTemplateExpression(stringTemplateExpression: JSStringTemplateExpression) {
//                super.visitJSStringTemplateExpression(stringTemplateExpression)
//                val value: String? = stringTemplateExpression.stringValue
//                debugPrintln("value=$value, visitJSStringTemplateExpression")
//            }
//
//            override fun visitJSXmlLiteralExpression(node: JSXmlLiteralExpression) {
//                super.visitJSXmlLiteralExpression(node)
//                val value: String? = node.stringValue
//                debugPrintln("value=$value, visitJSXmlLiteralExpression")
//            }
//        }
//    }

    @NotNull
    override fun checkFile(
        @NotNull file: PsiFile, @NotNull manager: InspectionManager, isOnTheFly: Boolean
    ): @NotNull Array<ProblemDescriptor>? {
        // debugPrintln("checkFile")

        if (!isOnTheFly) return null

        // 判断是否为 JS/TS 文件
        // debugPrintln("file.language.id=${file.language.id}")
        // file.language.id=ECMAScript 6
        // JavaScriptFileType.INSTANCE.name || TypeScriptFileType.INSTANCE.name
        if (file !is JSFile) {
            debugPrintln("file is not JSFile")
            return null
        }

        val virtualFile: VirtualFile? = CsleUtils.getRealVirtualFile(file)
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) return null

        val project: Project = file.project
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return null

        val problems: MutableList<ProblemDescriptor> = ArrayList()
        collectProblems(file, manager, isOnTheFly, problems)
        return problems.toTypedArray<ProblemDescriptor>()
    }

    /**
     * 收集 root 下 JS 字符串字面量的汉字问题。
     *
     * 同时供 HTML 检查器复用：HTML 中 <script> 的内容由 JavaScript 解析器在 HTML 文件内
     * 直接解析成 JSEmbeddedContent（不是注入片段），HTML 文件不会交给注册为 JavaScript
     * 语言的检查器，只能由 [CsleEmbeddedJSInspection] 在这里调用。
     */
    internal fun collectProblems(
        root: PsiElement,
        manager: InspectionManager,
        isOnTheFly: Boolean,
        problems: MutableList<ProblemDescriptor>
    ) {
        // 遍历文件中的所有 PSI 元素
        // JSRecursiveElementWalkingVisitor
        root.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                // debugPrintln("element:$element")

                if (CsleSettings.instance.state.checkDocComments && element is JSDocComment) {
                    val text = element.text
                    if (!containsChinese(text)) {
                        return
                    }

                    val converted = getConvertedText(text)
                    if (text == converted) {
                        return
                    }

                    problems.add(
                        manager.createProblemDescriptor(
                            element,
                            CsleUtils.problemDescription(),
                            JSDocCommentQuickFix(),
                            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                            isOnTheFly,
                        )
                    )
                    return
                }

                if (!CsleSettings.instance.state.checkLiteralExpression) return

                // 检查是否是 JS 字符串字面量表达式
                // JSLiteralExpression.isStringLiteral 单引号或双引号包裹字符串
                // JSStringTemplateExpression 模版字符串 反引号（`）声明支持换行和插值的字符串‌
                // XmlAttributeValue JSX/E4X 属性值，例如：<div title="中文"/>
                // （JS 插件的实现类直接继承 XmlAttributeValueImpl，没有单独的接口）
                // XmlToken:XML_DATA_CHARACTERS JSX文件，例如：“<div>中文</div>”

                val isJsString =
                    (element is JSLiteralExpression && element.isStringLiteral) ||
                            element is JSStringTemplateExpression ||
                            element is XmlAttributeValue

                val isJsxText = element.node.elementType == XmlTokenType.XML_DATA_CHARACTERS

                if (!isJsString && !isJsxText) {
                    debugPrintln("element is not JSLiteralExpression or not JSXText")
                    return
                }

                // 是否在特殊方法中
                if (inExcludedCallExpression(element)) {
                    return
                }

                var text: String = element.text
                if (!containsChinese(text)) {
                    debugPrintln("containsChinese=false")
                    return
                }
                debugPrintln("visit text=$text")

                // 去掉引号，获取实际内容
                if (text.startsWith("\"") || text.startsWith("'")) {
                    text = text.substring(1, text.length - 1)
                }

                // 去掉字母数字空格符号
                text = cleanPattern.matcher(text).replaceAll("")

                if (!containsChinese(text)) {
                    debugPrintln("containsChinese again=false")
                    return
                }

                // 有inspect的字，但是转换后是同一个字，也就是简繁共用字的情况，比如：“坪”
                val converted = getConvertedText(text)
                if (text == converted) {
                    debugPrintln("converted=true")
                    return
                }

                // 创建问题描述，显示黄色波浪线
                problems.add(
                    manager.createProblemDescriptor(
                        element,
                        CsleUtils.problemDescription(),
                        JSLiteralExpressionQuickFix(),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                        isOnTheFly,
                    )
                )
            }
        })
    }
}

class JSLiteralExpressionQuickFix : CsleLocalQuickFix() {

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        // 字符串、模板字符串、JSX 文本节点统一按原文范围替换：
        // 用工厂重建会丢掉多行 JSX 文本的换行，甚至包成 <![CDATA[...]]>
        val newText = getConvertedText(element.text)
        if (newText == element.text) return

        WriteCommandAction.runWriteCommandAction(project) {
            val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
                ?: return@runWriteCommandAction
            val range = element.textRange
            document.replaceString(range.startOffset, range.endOffset, newText)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}

class JSDocCommentQuickFix : CsleLocalQuickFix() {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        WriteCommandAction.runWriteCommandAction(project) {
            val element = descriptor.psiElement as? JSDocComment ?: return@runWriteCommandAction
            val newText = getConvertedText(element.text)
            if (newText == element.text) return@runWriteCommandAction
            // 文档注释不是表达式，JSPsiElementFactory.createJSExpression 解析不了，按文本范围替换
            val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
                ?: return@runWriteCommandAction
            val range = element.textRange
            document.replaceString(range.startOffset, range.endOffset, newText)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}
