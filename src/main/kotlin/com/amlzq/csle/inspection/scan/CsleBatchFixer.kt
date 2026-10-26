package com.amlzq.csle.inspection.scan

import com.amlzq.csle.inspection.CsleLocalQuickFix
import com.amlzq.csle.inspection.util.CsleBundle
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ReadonlyStatusHandler

/** 批量转换的结果：[fixed] 处已转换，[failed] 是未能转换的条目（元素失效、文件只读等）。 */
class BatchFixResult(val fixed: Int, val fixedFiles: Int, val failed: List<ScanResultItem>)

/**
 * 批量接受：复用每条结果自带的快速修复，把一批转换合并为一次 Undo。
 *
 * 每个文件内按偏移量从后往前处理，避免前面的替换改变后面元素的位置。
 * 快速修复自身带有 WriteCommandAction，嵌套在外层命令里时并入同一个撤销步骤。
 * 必须在 EDT 调用。
 */
object CsleBatchFixer {

    fun apply(project: Project, items: List<ScanResultItem>): BatchFixResult {
        ApplicationManager.getApplication().assertIsDispatchThread()

        val failed = ArrayList<ScanResultItem>()
        val byFile = items.groupBy { it.file }

        val status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(byFile.keys)
        val readonly = status.readonlyFiles.toSet()

        var fixed = 0
        val fixedFiles = HashSet<Any>()
        CommandProcessor.getInstance().executeCommand(project, {
            for ((file, fileItems) in byFile) {
                if (file in readonly) {
                    failed.addAll(fileItems)
                    continue
                }
                for (item in fileItems.sortedByDescending { it.startOffset }) {
                    if (applyOne(project, item)) {
                        fixed++
                        fixedFiles.add(file)
                    } else {
                        failed.add(item)
                    }
                }
            }
        }, CsleBundle.message("batch.command.name"), null)

        return BatchFixResult(fixed, fixedFiles.size, failed)
    }

    private fun applyOne(project: Project, item: ScanResultItem): Boolean {
        val descriptor = item.descriptor
        val element = descriptor.psiElement ?: return false
        // 扫描之后用户可能改过文件：元素失效或文本已变化时不动它
        if (!element.isValid || element.text != item.originalText) return false

        val fix = descriptor.fixes?.firstOrNull { it is CsleLocalQuickFix } as? LocalQuickFix ?: return false
        return try {
            fix.applyFix(project, descriptor)
            true
        } catch (e: Exception) {
            false
        }
    }
}
