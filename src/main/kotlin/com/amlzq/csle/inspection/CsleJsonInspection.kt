package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NotNull

class CsleJsonInspection : CsleLocalInspectionTool() {

    override fun inExcludedCallExpression(element: PsiElement): Boolean {
        val excluded = CsleSettings.instance.state.excludedJson
        if (excluded.isEmpty()) return false

        val property = PsiTreeUtil.getParentOfType(element, JsonProperty::class.java, false) ?: return false
        val name = property.name
        if (excluded.contains(name)) return true

        val path = buildPropertyPath(property)
        return path.isNotEmpty() && excluded.contains(path)
    }

    private fun buildPropertyPath(property: JsonProperty): String {
        val parts = ArrayList<String>()
        var current: JsonProperty? = property
        while (current != null) {
            val name = current.name
            if (name.isNotBlank()) {
                parts.add(name)
            }
            current = PsiTreeUtil.getParentOfType(current.parent, JsonProperty::class.java, true)
        }
        if (parts.isEmpty()) return ""
        parts.reverse()
        return parts.joinToString(".")
    }

    @NotNull
    override fun checkFile(
        @NotNull file: PsiFile, @NotNull manager: InspectionManager, isOnTheFly: Boolean
    ): @NotNull Array<ProblemDescriptor>? {
        if (!isOnTheFly) return null

        if (file !is JsonFile) return null

        val virtualFile: VirtualFile? = CsleUtils.getRealVirtualFile(file)
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) return null

        val project: Project = file.project
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)) return null

        if (!CsleSettings.instance.state.checkLiteralExpression) return null

        val problems: MutableList<ProblemDescriptor> = ArrayList()

        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(@NotNull element: PsiElement) {
                super.visitElement(element)

                if (element !is JsonStringLiteral) return
                if (inExcludedCallExpression(element)) return

                var text: String = element.text
                if (!containsChinese(text)) return

                if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                    text = text.substring(1, text.length - 1)
                }

                text = cleanPattern.matcher(text).replaceAll("")
                if (!containsChinese(text)) return

                val converted = getConvertedText(text)
                if (text == converted) return

                problems.add(
                    manager.createProblemDescriptor(
                        element,
                        CsleBundle.message("convert.to.another", CsleUtils.getQuickFix()),
                        JsonStringLiteralQuickFix(),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                        isOnTheFly,
                    )
                )
            }
        })

        return problems.toTypedArray<ProblemDescriptor>()
    }
}

class JsonStringLiteralQuickFix : CsleLocalQuickFix() {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? JsonStringLiteral ?: return
        val text = element.text
        val newText = getConvertedText(text)
        if (text == newText) return

        WriteCommandAction.runWriteCommandAction(project) {
            val replacement = createJsonStringReplacement(project, element, newText) ?: return@runWriteCommandAction
            element.replace(replacement)
        }
    }

    private fun createJsonStringReplacement(
        project: Project,
        context: JsonStringLiteral,
        newText: String
    ): JsonStringLiteral? {
        val fileType = context.containingFile.fileType
        val ext = fileType.defaultExtension.ifBlank { "json" }
        val dummyFile =
            PsiFileFactory.getInstance(project).createFileFromText("csle_dummy.$ext", fileType, "{\"k\":$newText}")

        val candidates = PsiTreeUtil.findChildrenOfType(dummyFile, JsonStringLiteral::class.java)
        return candidates.firstOrNull { it.text == newText } ?: candidates.firstOrNull()
    }
}
