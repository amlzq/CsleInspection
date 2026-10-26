package com.amlzq.csle.inspection.scan

import com.amlzq.csle.inspection.CsleLocalInspectionTool
import com.amlzq.csle.inspection.CsleUtils
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.lang.Language
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager

/**
 * 全项目扫描：对项目内容根下的每个文件，运行已注册的汉字检查器，收集命中。
 *
 * 不重复实现检查逻辑，排除列表、文档注释开关、简繁共用字过滤等全部沿用检查器本身。
 * 应在后台线程调用；每个文件单独占用一次读锁，扫描过程中不会长时间阻塞写操作。
 */
class CsleProjectScanner(private val project: Project) {

    /** 超过该大小的文件不扫描（字节）。 */
    private val maxFileSize = 1024 * 1024

    private class ToolEntry(val tool: CsleLocalInspectionTool, val language: Language, val label: String)

    fun scan(indicator: ProgressIndicator? = null): ScanResult {
        val skippedLanguages = linkedSetOf<String>()
        val tools = collectTools(skippedLanguages)

        indicator?.isIndeterminate = false
        val files = ArrayList<VirtualFile>()
        ReadAction.run<RuntimeException> {
            ProjectFileIndex.getInstance(project).iterateContent { file ->
                if (!file.isDirectory && file.isInLocalFileSystem) files.add(file)
                true
            }
        }

        val manager = InspectionManager.getInstance(project)
        val items = ArrayList<ScanResultItem>()
        var scanned = 0
        var skippedLarge = 0

        for ((index, file) in files.withIndex()) {
            ProgressManager.checkCanceled()
            indicator?.let {
                it.text2 = file.name
                it.fraction = index.toDouble() / files.size
            }
            if (file.length > maxFileSize) {
                skippedLarge++
                continue
            }
            val found = ReadAction.compute<List<ScanResultItem>, RuntimeException> {
                scanFile(file, tools, manager)
            }
            scanned++
            items.addAll(found)
        }
        return ScanResult(items, scanned, skippedLarge, skippedLanguages.toList())
    }

    private fun collectTools(skippedLanguages: MutableSet<String>): List<ToolEntry> {
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val result = ArrayList<ToolEntry>()
        for (ep in LocalInspectionEP.LOCAL_INSPECTION.extensionList) {
            val className = ep.implementationClass ?: continue
            if (!className.startsWith(PACKAGE)) continue
            val tool = ep.instantiateTool() as? CsleLocalInspectionTool ?: continue
            val language = ep.language?.let { Language.findLanguageByID(it) } ?: continue

            val key = ep.shortName?.let { HighlightDisplayKey.find(it) }
            if (key != null && !profile.isToolEnabled(key)) {
                skippedLanguages.add(language.displayName)
                continue
            }
            result.add(ToolEntry(tool, language, language.displayName))
        }
        return result
    }

    private fun scanFile(file: VirtualFile, tools: List<ToolEntry>, manager: InspectionManager): List<ScanResultItem> {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return emptyList()
        val languages = psiFile.viewProvider.languages
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
        val found = ArrayList<ScanResultItem>()

        for (entry in tools) {
            if (languages.none { it.isKindOf(entry.language) }) continue
            val problems = entry.tool.checkFile(psiFile, manager, true) ?: continue
            for (descriptor in problems) {
                val element = descriptor.psiElement ?: continue
                val text = element.text
                val start = element.textRange.startOffset
                found.add(
                    ScanResultItem(
                        file = file,
                        language = entry.label,
                        lineNumber = document?.getLineNumber(start)?.plus(1) ?: 0,
                        startOffset = start,
                        originalText = text,
                        convertedText = CsleUtils.getConvertedText(text),
                        descriptor = descriptor,
                    )
                )
            }
        }
        return found
    }

    private companion object {
        const val PACKAGE = "com.amlzq.csle.inspection."
    }
}
