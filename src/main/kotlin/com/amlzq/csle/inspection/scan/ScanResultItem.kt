package com.amlzq.csle.inspection.scan

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.vfs.VirtualFile

/**
 * 扫描结果中的一条命中。
 *
 * [descriptor] 内含对应语言的快速修复，批量接受时直接复用。
 */
class ScanResultItem(
    val file: VirtualFile,
    val language: String,
    val lineNumber: Int,
    val startOffset: Int,
    val originalText: String,
    val convertedText: String,
    val descriptor: ProblemDescriptor,
)

/**
 * 一次扫描的汇总。
 *
 * [skippedLanguages] 是在 Inspections 页被用户禁用、因此被跳过的语言。
 */
class ScanResult(
    val items: List<ScanResultItem>,
    val scannedFiles: Int,
    val skippedLargeFiles: Int,
    val skippedLanguages: List<String>,
)
