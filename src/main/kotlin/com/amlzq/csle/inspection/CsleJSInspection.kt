package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlTokenType
import org.jetbrains.annotations.NotNull

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

        // 遍历文件中的所有 PSI 元素
        // JSRecursiveElementWalkingVisitor
        file.accept(object : PsiRecursiveElementVisitor() {
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
                            CsleBundle.message("convert.to.another", CsleUtils.getQuickFix()),
                            JSDocCommentQuickFix(),
                            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                            isOnTheFly,
                        )
                    )
                    return
                }

                // 检查是否是 JS 字符串字面量表达式
                // JSLiteralExpression.isStringLiteral 单引号或双引号包裹字符串
                // JSStringTemplateExpression 模版字符串 反引号（`）声明支持换行和插值的字符串‌
                // XmlToken:XML_DATA_CHARACTERS JSX文件，例如：“<div>中文</div>”

                val isJsString =
                    (element is JSLiteralExpression && element.isStringLiteral) || element is JSStringTemplateExpression

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
                        CsleBundle.message("convert.to.another", CsleUtils.getQuickFix()),
                        JSLiteralExpressionQuickFix(),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                        isOnTheFly,
                    )
                )
            }
        })
        return problems.toTypedArray<ProblemDescriptor>()
    }
}

class JSLiteralExpressionQuickFix : CsleLocalQuickFix() {

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val text = element.text

        // 将原中文字形转换为目标中文字形
        val newText = getConvertedText(text)

        // 使用 WriteCommandAction 确保写操作发生在正确的上下文中
        WriteCommandAction.runWriteCommandAction(project) {
            when {
                element is JSLiteralExpression -> {
                    // 普通字符串字面量（'中文' 或 "中文"）
                    val newElement = JSPsiElementFactory.createJSExpression(newText, element.context!!)
                    element.replace(newElement)
                }

                element.node.elementType == XmlTokenType.XML_DATA_CHARACTERS -> {
                    // JSX XML 字符节点，例如：<div>中文</div>
                    element.replace(
                        XmlElementFactory.getInstance(project).createDisplayText(newText)
                    )
                }

                else -> {
                    // 不支持的类型（理论上不会进入）
                    debugPrintln("Unsupported element type for fix: ${element.javaClass.name}")
                }
            }
        }
    }
}

class JSDocCommentQuickFix : CsleLocalQuickFix() {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        WriteCommandAction.runWriteCommandAction(project) {
            val element = descriptor.psiElement as? JSDocComment ?: return@runWriteCommandAction
            val newText = getConvertedText(element.text)
            val newElement = JSPsiElementFactory.createJSExpression(newText, element.context!!)
            element.replace(newElement)
        }
    }
}
