package com.amlzq.csle.inspection.scan

import com.amlzq.csle.inspection.util.CsleBundle
import com.amlzq.csle.inspection.util.CsleUtils
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * 扫描结果面板：顶部是工具栏、汇总和 Accept All 按钮，下面是 文件 → 命中 的勾选树。
 * 双击或回车跳转到源码位置；Accept All 只转换已勾选的条目。
 */
class CsleResultPanel(private val project: Project) : JPanel(BorderLayout()) {

    private class FileNode(val file: VirtualFile, val count: Int)

    private val statusLabel = JBLabel(CsleBundle.message("scan.hint")).apply {
        border = JBUI.Borders.empty(4, 8)
    }
    private val acceptButton = JButton().apply {
        addActionListener { acceptAll() }
    }
    private val root = CheckedTreeNode()
    private val tree = CheckboxTree(Renderer(), root).apply {
        isRootVisible = false
        showsRootHandles = true
        addCheckboxTreeListener(object : com.intellij.ui.CheckboxTreeListener {
            override fun nodeStateChanged(node: CheckedTreeNode) = updateAcceptButton()
        })
    }

    /** 当前汇总文字，主要给测试用。 */
    val statusText: String get() = statusLabel.text

    init {
        val toolbar = ActionManager.getInstance().createActionToolbar(
            "CsleResultToolbar", DefaultActionGroup(RescanAction(), SelectAllAction(true), SelectAllAction(false)), true
        )
        toolbar.targetComponent = this

        val top = JPanel(BorderLayout())
        top.add(toolbar.component, BorderLayout.WEST)
        top.add(statusLabel, BorderLayout.CENTER)
        top.add(acceptButton, BorderLayout.EAST)
        add(top, BorderLayout.NORTH)
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
        updateAcceptButton()
    }

    fun showResult(result: ScanResult, notice: String? = null) {
        root.removeAllChildren()
        result.items.groupBy { it.file }.entries
            .sortedBy { it.key.path }
            .forEach { (file, items) ->
                val fileNode = CheckedTreeNode(FileNode(file, items.size))
                items.sortedBy { it.startOffset }.forEach { fileNode.add(CheckedTreeNode(it)) }
                root.add(fileNode)
            }
        (tree.model as DefaultTreeModel).reload()
        checkAll(true)
        TreeUtil.expandAll(tree)

        val parts = ArrayList<String>()
        notice?.let { parts.add(it) }
        parts.add(
            if (result.items.isEmpty()) CsleBundle.message("scan.empty", result.scannedFiles)
            else CsleBundle.message("scan.status", result.items.size, root.childCount, result.scannedFiles)
        )
        if (result.skippedLanguages.isNotEmpty()) {
            parts.add(CsleBundle.message("scan.skipped.languages", result.skippedLanguages.joinToString(", ")))
        }
        if (result.skippedLargeFiles > 0) {
            parts.add(CsleBundle.message("scan.skipped.large", result.skippedLargeFiles))
        }
        statusLabel.text = parts.joinToString(" ")
        updateAcceptButton()
    }

    /** 已勾选的命中条目。 */
    fun checkedItems(): List<ScanResultItem> {
        val result = ArrayList<ScanResultItem>()
        for (i in 0 until root.childCount) {
            val fileNode = root.getChildAt(i) as CheckedTreeNode
            for (j in 0 until fileNode.childCount) {
                val node = fileNode.getChildAt(j) as CheckedTreeNode
                val item = node.userObject as? ScanResultItem ?: continue
                if (node.isChecked) result.add(item)
            }
        }
        return result
    }

    fun checkAll(checked: Boolean) {
        for (i in 0 until root.childCount) {
            val fileNode = root.getChildAt(i) as CheckedTreeNode
            fileNode.isChecked = checked
            for (j in 0 until fileNode.childCount) (fileNode.getChildAt(j) as CheckedTreeNode).isChecked = checked
        }
        tree.repaint()
        updateAcceptButton()
    }

    private fun updateAcceptButton() {
        val count = checkedItems().size
        acceptButton.text = CsleBundle.message("accept.button", count)
        acceptButton.isEnabled = count > 0
    }

    private fun acceptAll() {
        val items = checkedItems()
        if (items.isEmpty()) return

        val fileCount = items.map { it.file }.distinct().size
        val answer = Messages.showYesNoDialog(
            project,
            CsleBundle.message("accept.confirm.message", items.size, fileCount, CsleUtils.getQuickFix()),
            CsleBundle.message("accept.confirm.title"),
            Messages.getQuestionIcon()
        )
        if (answer != Messages.YES) return

        val result = CsleBatchFixer.apply(project, items)
        val notice = CsleBundle.message("accept.done", result.fixed, result.fixedFiles) +
            if (result.failed.isNotEmpty()) " " + CsleBundle.message("accept.failed", result.failed.size) else ""
        // 先把结果说明显示出来；重扫完成后会带着同一段说明刷新列表
        statusLabel.text = notice
        // 重新扫描：成功的不再出现，失败的仍留在列表里
        CsleScanRunner.start(project, notice)
    }

    private fun navigateToSelection() {
        when (val obj = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject) {
            is ScanResultItem -> OpenFileDescriptor(project, obj.file, obj.startOffset).navigate(true)
            is FileNode -> OpenFileDescriptor(project, obj.file).navigate(true)
        }
    }

    private inner class RescanAction : AnAction(
        CsleBundle.message("action.rescan"), null, AllIcons.Actions.Refresh
    ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) = CsleScanRunner.start(project)
    }

    private inner class SelectAllAction(private val checked: Boolean) : AnAction(
        CsleBundle.message(if (checked) "action.select.all" else "action.select.none"),
        null,
        if (checked) AllIcons.Actions.Selectall else AllIcons.Actions.Unselectall
    ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) = checkAll(checked)
    }

    private class Renderer : CheckboxTree.CheckboxTreeCellRenderer() {
        override fun customizeRenderer(
            tree: JTree, value: Any, selected: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ) {
            when (val obj = (value as? DefaultMutableTreeNode)?.userObject) {
                is FileNode -> {
                    textRenderer.icon = obj.file.fileType.icon
                    textRenderer.append(obj.file.name)
                    textRenderer.append("  ${obj.file.parent?.path.orEmpty()}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    textRenderer.append(
                        "  " + CsleBundle.message("scan.file.count", obj.count), SimpleTextAttributes.GRAYED_ATTRIBUTES
                    )
                }

                is ScanResultItem -> {
                    textRenderer.icon = AllIcons.General.Warning
                    textRenderer.append("${obj.lineNumber}: ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    textRenderer.append(oneLine(obj.originalText))
                    textRenderer.append("  →  ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    textRenderer.append(
                        oneLine(obj.convertedText),
                        SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GREEN.darker())
                    )
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
