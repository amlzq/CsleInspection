package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlComment
import com.intellij.psi.xml.XmlText
import org.jetbrains.annotations.NotNull

class CsleHtmlInspection : CsleLocalInspectionTool() {

    override fun inExcludedCallExpression(element: PsiElement): Boolean {
        val excluded = CsleSettings.instance.state.excluded
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
            .toSet()
        if (excluded.isEmpty()) return false

        val tag = generateSequence(element) { it.parent }
            .filterIsInstance<HtmlTag>()
            .firstOrNull()

        val tagName = tag?.name?.lowercase()
        if (tagName == "script" || tagName == "style") return true
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

    @NotNull
    override fun checkFile(
        @NotNull file: PsiFile, @NotNull manager: InspectionManager, isOnTheFly: Boolean
    ): @NotNull Array<ProblemDescriptor>? {
        if (!isOnTheFly) return null

        if (file !is XmlFile) return null
        if (!file.language.id.equals("HTML", ignoreCase = true)) return null

        val virtualFile: VirtualFile = CsleUtils.getRealVirtualFile(file) ?: return null
        if (!virtualFile.isInLocalFileSystem) return null

        val project: Project = file.project
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return null

        val shouldCheckLiteral = CsleSettings.instance.state.checkLiteralExpression
        val shouldCheckComments = CsleSettings.instance.state.checkDocComments
        if (!shouldCheckLiteral && !shouldCheckComments) return null

        val problems: MutableList<ProblemDescriptor> = ArrayList()

        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                if (shouldCheckComments && element is XmlComment) {
                    if (!hasConvertibleChinese(element.text)) return
                    problems.add(
                        manager.createProblemDescriptor(
                            element,
                            CsleBundle.message("convert.to.another", CsleUtils.getQuickFix()),
                            HtmlQuickFix(),
                            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                            isOnTheFly,
                        )
                    )
                    return
                }

                if (!shouldCheckLiteral) return

                when (element) {
                    is XmlAttributeValue -> {
                        if (inExcludedCallExpression(element)) return
                        if (!hasConvertibleChinese(element.value)) return
                        problems.add(
                            manager.createProblemDescriptor(
                                element,
                                CsleBundle.message("convert.to.another", CsleUtils.getQuickFix()),
                                HtmlQuickFix(),
                                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                                isOnTheFly,
                            )
                        )
                    }

                    is XmlText -> {
                        if (inExcludedCallExpression(element)) return
                        if (!hasConvertibleChinese(element.value)) return
                        problems.add(
                            manager.createProblemDescriptor(
                                element,
                                CsleBundle.message("convert.to.another", CsleUtils.getQuickFix()),
                                HtmlQuickFix(),
                                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                                isOnTheFly,
                            )
                        )
                    }
                }
            }
        })

        return problems.toTypedArray()
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
}

private class HtmlQuickFix : CsleLocalQuickFix() {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            when (element) {
                is XmlText -> {
                    val original = element.value
                    val converted = getConvertedText(original)
                    if (original == converted) return@runWriteCommandAction

                    val replacement = XmlElementFactory.getInstance(project).createDisplayText(converted)
                    element.replace(replacement)
                }

                is XmlAttributeValue -> {
                    val original = element.value
                    val converted = getConvertedText(original)
                    if (original == converted) return@runWriteCommandAction

                    val attribute = element.parent as? XmlAttribute ?: return@runWriteCommandAction
                    attribute.setValue(converted)
                }

                is XmlComment -> {
                    val original = element.text
                    val converted = getConvertedText(original)
                    if (original == converted) return@runWriteCommandAction

                    val dummyFile = PsiFileFactory.getInstance(project).createFileFromText(
                        "csle_dummy.xml",
                        XmlFileType.INSTANCE,
                        converted,
                    )
                    val replacement = PsiTreeUtil.findChildOfType(dummyFile, XmlComment::class.java)
                        ?: return@runWriteCommandAction
                    element.replace(replacement)
                }
            }
        }
    }
}
