package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.css.CssDeclaration
import com.intellij.psi.css.CssString
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NotNull

class CsleCssInspection : CsleLocalInspectionTool() {
    
    override fun inExcludedCallExpression(element: PsiElement): Boolean {
        val excluded = CsleSettings.instance.state.excluded
        if (excluded.isEmpty()) return false

        val declaration = PsiTreeUtil.getParentOfType(element, CssDeclaration::class.java, false) ?: return false
        val declarationText = declaration.text
        val colonIndex = declarationText.indexOf(':')
        if (colonIndex <= 0) return false

        val propertyName = declarationText.substring(0, colonIndex).trim()
        if (propertyName.isEmpty()) return false

        val propertyNameNormalized = normalizeCssPropertyName(propertyName)
        for (raw in excluded) {
            val excludedName = normalizeCssPropertyName(raw)
            if (excludedName.isEmpty()) continue
            if (propertyNameNormalized == excludedName) return true
            if (propertyNameNormalized.removePrefix("--") == excludedName.removePrefix("--")) return true
        }
        return false
    }

    private fun normalizeCssPropertyName(name: String): String = name.trim().lowercase()

    @NotNull
    override fun checkFile(
        @NotNull file: PsiFile, @NotNull manager: InspectionManager, isOnTheFly: Boolean
    ): @NotNull Array<ProblemDescriptor>? {
        if (!isOnTheFly) return null

        val languageId = file.language.id.lowercase()
        if (languageId != "css" && languageId != "scss" && languageId != "less") {
            debugPrintln("file is not CSS/SCSS/LESS language, languageId=$languageId")
            return null
        }

        val virtualFile: VirtualFile? = CsleUtils.getRealVirtualFile(file)
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) return null

        val project: Project = file.project
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return null

        val problems: MutableList<ProblemDescriptor> = ArrayList()

        if (!CsleSettings.instance.state.checkLiteralExpression) return null

        // 遍历文件中的所有 PSI 元素
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                if (element !is CssString) return
                if (inExcludedCallExpression(element)) return

                val text = element.text
                if (!containsChinese(text)) {
                    debugPrintln("containsChinese=false")
                    return
                }

                debugPrintln("visit CSS text=$text")

                val innerText = text.removeSurrounding("\"").removeSurrounding("'")

                // 清理符号
                val cleanText = cleanPattern.matcher(innerText).replaceAll("")
                if (!containsChinese(cleanText)) {
                    debugPrintln("containsChinese again=false")
                    return
                }

                val cleanConverted = getConvertedText(cleanText)
                if (cleanText == cleanConverted) {
                    debugPrintln("converted=true")
                    return
                }

                val converted = getConvertedText(text)
                if (text == converted) return

                // 创建问题描述，显示黄色波浪线
                problems.add(
                    manager.createProblemDescriptor(
                        element,
                        CsleBundle.message("convert.to.another", CsleUtils.getQuickFix()),
                        CssStringQuickFix(),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                        isOnTheFly,
                    )
                )
            }
        })
        return problems.toTypedArray<ProblemDescriptor>()
    }
}

class CssStringQuickFix : CsleLocalQuickFix() {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? CssString ?: return
        val text = element.text
        val newText = getConvertedText(text)

        if (text == newText) return

        WriteCommandAction.runWriteCommandAction(project) {
            val replacement = createCssStringReplacement(project, element, newText) ?: return@runWriteCommandAction
            element.replace(replacement)
        }
    }

    private fun createCssStringReplacement(project: Project, context: CssString, newText: String): CssString? {
        val fileType: FileType = context.containingFile.fileType
        val ext = fileType.defaultExtension.ifBlank { "css" }
        val dummyFile =
            PsiFileFactory.getInstance(project).createFileFromText("csle_dummy.$ext", fileType, "a{content:$newText;}")

        val candidates = PsiTreeUtil.findChildrenOfType(dummyFile, CssString::class.java)
        return candidates.firstOrNull { it.text == newText } ?: candidates.firstOrNull()
    }
}
