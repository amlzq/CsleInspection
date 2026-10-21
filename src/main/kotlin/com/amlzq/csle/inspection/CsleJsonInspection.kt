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
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NotNull

/**
 * 汉字检查器：JSON。
 *
 * 检查字符串值中的汉字；排除列表按字段名或字段路径配置。
 */
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
                        CsleUtils.problemDescription(),
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
        val newText = getConvertedText(element.text)
        if (newText == element.text) return

        // 按文本范围替换：键名字面量与虚拟文件里的值字面量互换 PSI 节点会破坏整个文档
        WriteCommandAction.runWriteCommandAction(project) {
            val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
                ?: return@runWriteCommandAction
            val range = element.textRange
            document.replaceString(range.startOffset, range.endOffset, newText)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}
