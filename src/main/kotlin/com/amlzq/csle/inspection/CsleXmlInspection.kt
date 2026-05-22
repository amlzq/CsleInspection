package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.*
import org.jetbrains.annotations.NotNull

class CsleXmlInspection : CsleLocalInspectionTool() {

    override fun inExcludedCallExpression(element: PsiElement): Boolean {
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
        if (file !is XmlFile) return null

        val virtualFile: VirtualFile? = CsleUtils.getRealVirtualFile(file)
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) return null

        val project: Project = file.project
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return null

        val problems = mutableListOf<ProblemDescriptor>()
        val reportedRanges = HashSet<TextRange>()

        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                if (CsleSettings.instance.state.checkDocComments && element is XmlComment) {
                    val text = element.text
                    if (!containsChinese(text)) return

                    val converted = getConvertedText(text)
                    if (text == converted) return
                    if (!reportedRanges.add(element.textRange)) return

                    problems.add(
                        manager.createProblemDescriptor(
                            element,
                            CsleBundle.message("convert.to.another", CsleUtils.getQuickFix()),
                            XmlCommentQuickFix(),
                            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                            isOnTheFly,
                        )
                    )
                    return
                }

                if (!CsleSettings.instance.state.checkLiteralExpression) return

                if (element is XmlAttributeValue) {
                    if (inExcludedCallExpression(element)) return

                    val text = element.value
                    if (!containsChinese(text)) return

                    val cleanText = cleanPattern.matcher(text).replaceAll("")
                    if (!containsChinese(cleanText)) return

                    val cleanConverted = getConvertedText(cleanText)
                    if (cleanText == cleanConverted) return
                    if (!reportedRanges.add(element.textRange)) return

                    problems.add(
                        manager.createProblemDescriptor(
                            element,
                            CsleBundle.message("convert.to.another", CsleUtils.getQuickFix()),
                            XmlAttributeValueQuickFix(),
                            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                            isOnTheFly,
                        )
                    )
                    return
                }

                if (element is XmlText) {
                    if (inExcludedCallExpression(element)) return

                    val text = element.text
                    if (!containsChinese(text)) return

                    val cleanText = cleanPattern.matcher(text).replaceAll("")
                    if (!containsChinese(cleanText)) return

                    val cleanConverted = getConvertedText(cleanText)
                    if (cleanText == cleanConverted) return
                    if (!reportedRanges.add(element.textRange)) return

                    problems.add(
                        manager.createProblemDescriptor(
                            element,
                            CsleBundle.message("convert.to.another", CsleUtils.getQuickFix()),
                            XmlTextQuickFix(),
                            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                            isOnTheFly,
                        )
                    )
                    return
                }
            }
        })

        return problems.toTypedArray()
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
        val text = element.text
        val newText = getConvertedText(text)
        if (text == newText) return

        WriteCommandAction.runWriteCommandAction(project) {
            val dummyFile = PsiFileFactory.getInstance(project).createFileFromText(
                "dummy.xml",
                XmlFileType.INSTANCE,
                "<a>$newText</a>"
            )
            val replacement =
                PsiTreeUtil.findChildOfType(dummyFile, XmlText::class.java) ?: return@runWriteCommandAction
            element.replace(replacement)
        }
    }
}

class XmlCommentQuickFix : CsleLocalQuickFix() {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? XmlComment ?: return
        val text = element.text
        val newText = getConvertedText(text)
        if (text == newText) return

        WriteCommandAction.runWriteCommandAction(project) {
            val dummyFile = PsiFileFactory.getInstance(project).createFileFromText(
                "dummy.xml",
                XmlFileType.INSTANCE,
                "<a>$newText</a>"
            )
            val newComment =
                PsiTreeUtil.findChildOfType(dummyFile, XmlComment::class.java) ?: return@runWriteCommandAction
            element.replace(newComment)
        }
    }
}
