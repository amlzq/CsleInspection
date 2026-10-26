package com.amlzq.csle.inspection.ui

import com.amlzq.csle.inspection.CsleBundle
import com.amlzq.csle.inspection.scan.ScanResult
import com.amlzq.csle.inspection.scan.ScanResultItem
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * 扫描结果面板：顶部是汇总，下面是 文件 → 命中 的树。
 * 双击或回车跳转到源码位置。
 */
class CsleResultPanel(private val project: Project) : JPanel(BorderLayout()) {

    private class FileNode(val file: VirtualFile, val count: Int)

    private val statusLabel = JBLabel(CsleBundle.message("scan.hint")).apply {
        border = JBUI.Borders.empty(4, 8)
    }
    private val tree = Tree(DefaultTreeModel(DefaultMutableTreeNode())).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = Renderer()
    }

    init {
        add(statusLabel, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) navigateToSelection()
            }
        })
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) navigateToSelection()
            }
        })
    }

    fun showResult(result: ScanResult) {
        val root = DefaultMutableTreeNode()
        result.items.groupBy { it.file }.entries
            .sortedBy { it.key.path }
            .forEach { (file, items) ->
                val fileNode = DefaultMutableTreeNode(FileNode(file, items.size))
                items.sortedBy { it.startOffset }.forEach { fileNode.add(DefaultMutableTreeNode(it)) }
                root.add(fileNode)
            }
        tree.model = DefaultTreeModel(root)
        TreeUtil.expandAll(tree)

        val fileCount = root.childCount
        val parts = ArrayList<String>()
        parts.add(
            if (result.items.isEmpty()) CsleBundle.message("scan.empty", result.scannedFiles)
            else CsleBundle.message("scan.status", result.items.size, fileCount, result.scannedFiles)
        )
        if (result.skippedLanguages.isNotEmpty()) {
            parts.add(CsleBundle.message("scan.skipped.languages", result.skippedLanguages.joinToString(", ")))
        }
        if (result.skippedLargeFiles > 0) {
            parts.add(CsleBundle.message("scan.skipped.large", result.skippedLargeFiles))
        }
        statusLabel.text = parts.joinToString(" ")
    }

    private fun navigateToSelection() {
        val item = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject
        when (item) {
            is ScanResultItem -> OpenFileDescriptor(project, item.file, item.startOffset).navigate(true)
            is FileNode -> OpenFileDescriptor(project, item.file).navigate(true)
        }
    }

    private class Renderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ) {
            when (val obj = (value as? DefaultMutableTreeNode)?.userObject) {
                is FileNode -> {
                    icon = obj.file.fileType.icon
                    append(obj.file.name)
                    append("  ${obj.file.parent?.path.orEmpty()}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append("  " + CsleBundle.message("scan.file.count", obj.count), SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                is ScanResultItem -> {
                    icon = AllIcons.General.Warning
                    append("${obj.lineNumber}: ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(oneLine(obj.originalText))
                    append("  →  ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(oneLine(obj.convertedText), SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GREEN.darker()))
                }
            }
        }

        private fun oneLine(text: String): String {
            val flat = text.replace(Regex("\\s+"), " ").trim()
            return if (flat.length > MAX_TEXT) flat.take(MAX_TEXT) + "…" else flat
        }
    }

    private companion object {
        const val MAX_TEXT = 80
    }
}
