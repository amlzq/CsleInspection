package com.amlzq.csle.inspection.settings

import com.amlzq.csle.inspection.util.CsleBundle
import com.amlzq.csle.inspection.util.CsleCustomDictionary
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * 自定义惯用语词典的编辑弹窗：每行一个“原词 - 目标词”，两列都可直接编辑。
 *
 * 弹窗里编辑的是副本，点确定后才通过 [resultText] 交回设置页；注释和空行不会保留。
 */
class CsleCustomDictionaryDialog(
    parent: Component,
    private val glyph: CsleGlyphs,
    initialText: String,
) : DialogWrapper(parent, true) {

    /** 点确定后的词典文本（OpenCC 格式）；取消时无意义 */
    var resultText: String = initialText
        private set

    private val model = RowsModel(
        CsleCustomDictionary.parse(initialText).entries.map { arrayOf(it.key, it.value) },
    )
    internal val table: JTable = object : JBTable(model) {
        private val addRowRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
            ): Component {
                super.getTableCellRendererComponent(table, null, isSelected, false, row, column)
                // 渲染器会被各列复用，每次都要重置图标和文字，否则“+”会出现在其它列
                icon = null
                text = ""
                horizontalAlignment = SwingConstants.LEFT
                when (column) {
                    0 -> {
                        icon = AllIcons.General.Add
                        horizontalAlignment = SwingConstants.CENTER
                    }
                    1 -> {
                        text = CsleBundle.message("custom.dictionary.add.row")
                        if (!isSelected) foreground = UIUtil.getContextHelpForeground()
                    }
                }
                return this
            }
        }

        // 最后一行是“新增行”条目，用专用渲染
        override fun getCellRenderer(row: Int, column: Int): TableCellRenderer =
            if (row == this@CsleCustomDictionaryDialog.model.rows.size) addRowRenderer else super.getCellRenderer(row, column)
    }

    init {
        title = CsleBundle.message("custom.dictionary.dialog.title", glyph.label)
        configureTable()
        init()
    }

    private fun configureTable() {
        table.setShowGrid(true)
        table.rowHeight = JBUI.scale(24)
        table.putClientProperty("terminateEditOnFocusLost", true)
        val deleteColumn = table.columnModel.getColumn(0)
        deleteColumn.minWidth = JBUI.scale(32)
        deleteColumn.maxWidth = JBUI.scale(32)
        deleteColumn.cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
            ): Component {
                super.getTableCellRendererComponent(table, null, isSelected, false, row, column)
                icon = AllIcons.General.Remove
                horizontalAlignment = SwingConstants.CENTER
                toolTipText = CsleBundle.message("custom.dictionary.delete.row")
                return this
            }
        }
        // 选中末尾“新增行”条目时按回车新增一行；其它行保持表格默认的回车行为
        val enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
        val inputMap = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        // 不同外观下回车的默认绑定不一定存在，取不到时退回到“选中下一行”
        val defaultEnter = inputMap.get(enterKey)?.let { table.actionMap.get(it) }
        inputMap.put(enterKey, "csle.addRowOrNext")
        table.actionMap.put("csle.addRowOrNext", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                when {
                    table.selectedRow == model.rows.size -> addRow()
                    defaultEnter != null -> defaultEnter.actionPerformed(e)
                    else -> {
                        val next = minOf(table.selectedRow + 1, table.rowCount - 1)
                        if (next >= 0) table.setRowSelectionInterval(next, next)
                    }
                }
            }
        })
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                if (row < 0) return
                when {
                    row == model.rows.size -> addRow()
                    table.columnAtPoint(e.point) == 0 -> {
                        table.cellEditor?.stopCellEditing()
                        model.removeRow(row)
                    }
                }
            }
        })
    }

    override fun createCenterPanel(): JComponent {
        // 列表标题栏：左侧标题，右侧是 IDE 标准的“选项”菜单（齿轮图标）
        val optionsGroup = DefaultActionGroup().apply {
            isPopup = true
            templatePresentation.icon = AllIcons.General.GearPlain
            templatePresentation.description = CsleBundle.message("custom.dictionary.more")
            templatePresentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
            add(menuAction("custom.dictionary.import") { importFile() })
            add(menuAction("custom.dictionary.export") { exportFile() })
            add(menuAction("custom.dictionary.template") { saveTemplate() })
            addSeparator()
            add(menuAction("custom.dictionary.clear") {
                table.cellEditor?.stopCellEditing()
                model.clear()
            })
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("CsleCustomDictionary", DefaultActionGroup(optionsGroup), true)
            .apply { targetComponent = table }
        val titleBar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 2)
            add(JBLabel(CsleBundle.message("custom.dictionary.entries")), BorderLayout.WEST)
            add(toolbar.component, BorderLayout.EAST)
        }

        val note = JBLabel(
            "<html><body style='width:${JBUI.scale(440)}px'>${CsleBundle.message("custom.dictionary.dialog.note")}</body></html>",
            UIUtil.ComponentStyle.SMALL,
            UIUtil.FontColor.BRIGHTER,
        )
        return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(titleBar, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(note, BorderLayout.SOUTH)
            preferredSize = Dimension(JBUI.scale(480), JBUI.scale(360))
        }
    }

    private fun menuAction(key: String, action: () -> Unit): AnAction =
        object : DumbAwareAction(CsleBundle.message(key)) {
            override fun actionPerformed(e: AnActionEvent) = action()
        }

    private fun addRow() {
        table.cellEditor?.stopCellEditing()
        val row = model.addRow()
        table.setRowSelectionInterval(row, row)
        table.scrollRectToVisible(table.getCellRect(row, 1, true))
        table.editCellAt(row, 1)
        table.editorComponent?.requestFocusInWindow()
    }

    override fun getPreferredFocusedComponent(): JComponent = table

    override fun doValidate(): ValidationInfo? {
        table.cellEditor?.stopCellEditing()
        for (i in 0 until model.rows.size) {
            val (source, target) = model.rows[i]
            if (source.isBlank() && target.isBlank()) continue
            val error = CsleCustomDictionary.validate(source, target) ?: continue
            table.setRowSelectionInterval(i, i)
            val key = when (error) {
                CsleCustomDictionary.ValidationError.COMMENT_PREFIX -> "custom.dictionary.invalid.comment.entry"
                CsleCustomDictionary.ValidationError.INVALID_CHARACTER -> "custom.dictionary.invalid.character"
                CsleCustomDictionary.ValidationError.TARGET_HAS_SPACE -> "custom.dictionary.invalid.target.space"
                else -> "custom.dictionary.row.incomplete"
            }
            return ValidationInfo(CsleBundle.message(key, i + 1), table)
        }
        return null
    }

    override fun doOKAction() {
        resultText = CsleCustomDictionary.format(currentEntries())
        super.doOKAction()
    }

    /** 当前表格里的有效词条；原词重复时以后面的为准，全空的行忽略 */
    private fun currentEntries(): Map<String, String> {
        val entries = LinkedHashMap<String, String>()
        for ((source, target) in model.rows) {
            if (CsleCustomDictionary.validate(source, target) != null) continue
            entries[source.trim()] = target.trim()
        }
        return entries
    }

    private fun importFile() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle(CsleBundle.message("custom.dictionary.import"))
        val file = FileChooser.chooseFile(descriptor, null, null) ?: return
        val result = CsleCustomDictionary.parse(String(file.contentsToByteArray(), Charsets.UTF_8))
        val importTitle = CsleBundle.message("custom.dictionary.import")
        if (result.entries.isEmpty() && result.errors.isEmpty()) {
            Messages.showErrorDialog(CsleBundle.message("custom.dictionary.no.entries"), importTitle)
            return
        }
        if (result.errors.isNotEmpty()) {
            val shown = result.errors.take(5).joinToString("\n") { "${it.line}: ${it.content}" }
            Messages.showErrorDialog(
                CsleBundle.message("custom.dictionary.invalid", result.errors.size, shown),
                importTitle,
            )
            return
        }
        table.cellEditor?.stopCellEditing()
        val existing = currentEntries()
        val merged = LinkedHashMap<String, String>()
        if (existing.isNotEmpty()) {
            when (
                Messages.showYesNoCancelDialog(
                    CsleBundle.message("custom.dictionary.import.prompt", existing.size, result.entries.size),
                    importTitle,
                    CsleBundle.message("custom.dictionary.import.append"),
                    CsleBundle.message("custom.dictionary.import.replace"),
                    Messages.getCancelButton(),
                    Messages.getQuestionIcon(),
                )
            ) {
                Messages.YES -> merged.putAll(existing)
                Messages.NO -> Unit
                else -> return
            }
        }
        merged.putAll(result.entries)
        model.setRows(merged.map { arrayOf(it.key, it.value) })
    }

    private fun exportFile() {
        table.cellEditor?.stopCellEditing()
        val descriptor = FileSaverDescriptor(CsleBundle.message("custom.dictionary.export"), "", "txt")
        val wrapper = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, contentPanel)
            .save(null as VirtualFile?, CsleCustomDictionary.TEMPLATE_FILE_NAME) ?: return
        wrapper.file.writeText(CsleCustomDictionary.format(currentEntries()), Charsets.UTF_8)
    }

    private fun saveTemplate() {
        val descriptor = FileSaverDescriptor(CsleBundle.message("custom.dictionary.template"), "", "txt")
        val wrapper = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, contentPanel)
            .save(null as VirtualFile?, CsleCustomDictionary.TEMPLATE_FILE_NAME) ?: return
        wrapper.file.writeText(CsleCustomDictionary.templateFor(glyph), Charsets.UTF_8)
    }

    /** 三列：删除按钮、原词、目标词；只有后两列可编辑 */
    private class RowsModel(initial: List<Array<String>>) : AbstractTableModel() {
        val rows: MutableList<Array<String>> = initial.map { it.copyOf() }.toMutableList()

        /** 比实际词条多一行：末尾的“新增行”条目 */
        override fun getRowCount() = rows.size + 1
        override fun getColumnCount() = 3
        override fun getColumnName(column: Int): String = when (column) {
            1 -> CsleBundle.message("custom.dictionary.source")
            2 -> CsleBundle.message("custom.dictionary.target")
            else -> ""
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any =
            if (columnIndex == 0 || rowIndex >= rows.size) "" else rows[rowIndex][columnIndex - 1]

        override fun isCellEditable(rowIndex: Int, columnIndex: Int) = columnIndex > 0 && rowIndex < rows.size
        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            if (columnIndex > 0) rows[rowIndex][columnIndex - 1] = aValue?.toString().orEmpty()
        }

        fun addRow(): Int {
            rows += arrayOf("", "")
            fireTableRowsInserted(rows.size - 1, rows.size - 1)
            return rows.size - 1
        }

        fun removeRow(index: Int) {
            rows.removeAt(index)
            fireTableRowsDeleted(index, index)
        }

        fun clear() = setRows(emptyList())

        fun setRows(newRows: List<Array<String>>) {
            rows.clear()
            rows.addAll(newRows.map { it.copyOf() })
            fireTableDataChanged()
        }
    }
}
