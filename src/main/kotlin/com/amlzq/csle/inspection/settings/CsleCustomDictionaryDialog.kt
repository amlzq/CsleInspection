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
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent
import javax.swing.table.TableRowSorter
import com.intellij.ui.DocumentAdapter
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
class CsleCustomDictionaryDialog @JvmOverloads constructor(
    parent: Component,
    private val glyph: CsleGlyphs,
    initialText: String,
    /** 其它字形当前的词典文本（键为字形名），弹窗内可通过下拉框切换编辑；缺省时只有 [glyph] 一份 */
    otherDictionaries: Map<String, String> = emptyMap(),
) : DialogWrapper(parent, true) {

    /** 点确定后 [glyph] 的词典文本（OpenCC 格式）；取消时无意义 */
    var resultText: String = initialText
        private set

    /** 点确定后所有字形的词典文本（键为字形名）；取消时无意义 */
    var resultTexts: Map<String, String> = emptyMap()
        private set

    private fun parseRows(text: String): List<Array<String>> =
        CsleCustomDictionary.parse(text).entries.map { arrayOf(it.key, it.value) }

    /** 各字形的编辑副本；当前显示的字形以 [model] 为准，切换时回写到这里 */
    private val stored: MutableMap<CsleGlyphs, List<Array<String>>> =
        CsleGlyphs.entries.associateWithTo(LinkedHashMap()) { g ->
            parseRows(if (g == glyph) initialText else otherDictionaries[g.name].orEmpty())
        }
    private var currentGlyph: CsleGlyphs = glyph

    private val model = RowsModel(stored.getValue(glyph))
    private var query: String = ""
    private val sorter = TableRowSorter(model).apply {
        rowFilter = object : RowFilter<RowsModel, Int>() {
            override fun include(entry: Entry<out RowsModel, out Int>): Boolean {
                val index = entry.identifier
                // “新增行”条目、尚未填写的空行始终显示，否则新增后看不到
                if (index >= model.rows.size || query.isEmpty()) return true
                val (source, target) = model.rows[index]
                if (source.isBlank() && target.isBlank()) return true
                return source.contains(query, ignoreCase = true) || target.contains(query, ignoreCase = true)
            }
        }
    }
    private val glyphCombo = ComboBox(CsleGlyphs.entries.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create("") { it.label }
        selectedItem = glyph
    }
    private val searchCards = JPanel(java.awt.CardLayout())
    private val searchField = SearchTextField(false).apply {
        textEditor.emptyText.text = CsleBundle.message("custom.dictionary.search")
    }
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
            if (convertRowIndexToModel(row) == this@CsleCustomDictionaryDialog.model.rows.size) addRowRenderer else super.getCellRenderer(row, column)
    }

    init {
        title = CsleBundle.message("custom.dictionary.dialog.title", glyph.label)
        table.rowSorter = sorter
        glyphCombo.addActionListener {
            val target = glyphCombo.selectedItem as? CsleGlyphs ?: return@addActionListener
            if (target != currentGlyph) switchGlyph(target)
        }
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                table.cellEditor?.stopCellEditing()
                query = searchField.text.trim()
                sorter.allRowsChanged()
            }
        })
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
                    selectedModelRow() == model.rows.size -> addRow()
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
                val viewRow = table.rowAtPoint(e.point)
                if (viewRow < 0) return
                val row = table.convertRowIndexToModel(viewRow)
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
        // 顶部栏：字形下拉、搜索框，右侧是 IDE 标准的“选项”菜单（齿轮图标）
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
        // 搜索区：收起时只显示搜索图标，点击后展开成输入框；失焦且无内容、或按 Esc 时收起
        val expandAction = object : DumbAwareAction(
            CsleBundle.message("custom.dictionary.search"), null, AllIcons.Actions.Search,
        ) {
            override fun actionPerformed(e: AnActionEvent) = setSearchExpanded(true)
        }
        val searchToolbar = ActionManager.getInstance()
            .createActionToolbar("CsleCustomDictionarySearch", DefaultActionGroup(expandAction), true)
            .apply { targetComponent = table }
        searchCards.add(JPanel(BorderLayout()).apply { add(searchToolbar.component, BorderLayout.WEST) }, SEARCH_COLLAPSED)
        searchCards.add(searchField, SEARCH_EXPANDED)
        searchField.textEditor.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent) {
                if (searchField.text.isEmpty()) setSearchExpanded(false)
            }
        })
        searchField.textEditor.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE && searchField.text.isEmpty()) {
                    e.consume()
                    setSearchExpanded(false)
                }
            }
        })
        val filterBar = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = JBUI.Borders.empty(0, 2)
            add(glyphCombo, BorderLayout.WEST)
            add(searchCards, BorderLayout.CENTER)
            add(toolbar.component, BorderLayout.EAST)
        }

        val note = JBLabel(
            "<html><body style='width:${JBUI.scale(440)}px'>${CsleBundle.message("custom.dictionary.dialog.note")}</body></html>",
            UIUtil.ComponentStyle.SMALL,
            UIUtil.FontColor.BRIGHTER,
        )
        return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(filterBar, BorderLayout.NORTH)
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(note, BorderLayout.SOUTH)
            preferredSize = Dimension(JBUI.scale(480), JBUI.scale(360))
        }
    }

    private fun setSearchExpanded(expanded: Boolean) {
        (searchCards.layout as java.awt.CardLayout).show(searchCards, if (expanded) SEARCH_EXPANDED else SEARCH_COLLAPSED)
        if (expanded) searchField.textEditor.requestFocusInWindow() else table.requestFocusInWindow()
    }

    private fun menuAction(key: String, action: () -> Unit): AnAction =
        object : DumbAwareAction(CsleBundle.message(key)) {
            override fun actionPerformed(e: AnActionEvent) = action()
        }

    private fun selectedModelRow(): Int =
        table.selectedRow.let { if (it < 0) -1 else table.convertRowIndexToModel(it) }

    /** 选中模型中的某一行；被搜索过滤掉时先清空搜索 */
    private fun selectModelRow(modelRow: Int): Int {
        var view = table.convertRowIndexToView(modelRow)
        if (view < 0) {
            searchField.text = ""
            query = ""
            sorter.allRowsChanged()
            view = table.convertRowIndexToView(modelRow)
        }
        if (view >= 0) table.setRowSelectionInterval(view, view)
        return view
    }

    private fun switchGlyph(target: CsleGlyphs) {
        table.cellEditor?.stopCellEditing()
        stored[currentGlyph] = model.rows.map { it.copyOf() }
        currentGlyph = target
        model.setRows(stored.getValue(target))
        title = CsleBundle.message("custom.dictionary.dialog.title", target.label)
    }

    private fun addRow() {
        table.cellEditor?.stopCellEditing()
        val row = model.addRow()
        val view = selectModelRow(row)
        if (view < 0) return
        table.scrollRectToVisible(table.getCellRect(view, 1, true))
        table.editCellAt(view, 1)
        table.editorComponent?.requestFocusInWindow()
    }

    override fun getPreferredFocusedComponent(): JComponent = table

    override fun doValidate(): ValidationInfo? {
        table.cellEditor?.stopCellEditing()
        stored[currentGlyph] = model.rows.map { it.copyOf() }
        // 先检查当前字形，再检查其它字形，出错时切换到出错的字形
        for (g in listOf(currentGlyph) + CsleGlyphs.entries.filter { it != currentGlyph }) {
            val info = validateRows(g, stored.getValue(g))
            if (info != null) return info
        }
        return null
    }

    private fun validateRows(g: CsleGlyphs, rows: List<Array<String>>): ValidationInfo? {
        for (i in rows.indices) {
            val (source, target) = rows[i]
            if (source.isBlank() && target.isBlank()) continue
            val error = CsleCustomDictionary.validate(source, target) ?: continue
            if (g != currentGlyph) glyphCombo.selectedItem = g
            selectModelRow(i)
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
        table.cellEditor?.stopCellEditing()
        stored[currentGlyph] = model.rows.map { it.copyOf() }
        resultTexts = stored.entries.associate { (g, rows) -> g.name to CsleCustomDictionary.format(entriesOf(rows)) }
        resultText = resultTexts.getValue(glyph.name)
        super.doOKAction()
    }

    /** 当前表格里的有效词条；原词重复时以后面的为准，全空的行忽略 */
    private fun currentEntries(): Map<String, String> = entriesOf(model.rows)

    private fun entriesOf(rows: List<Array<String>>): Map<String, String> {
        val entries = LinkedHashMap<String, String>()
        for ((source, target) in rows) {
            if (CsleCustomDictionary.validate(source, target) != null) continue
            entries[source.trim()] = target.trim()
        }
        return entries
    }

    private fun importFile() {
        val descriptor = FileChooserDescriptorFactory.singleFile()
            .withTitle(CsleBundle.message("custom.dictionary.import"))
        val file = FileChooser.chooseFile(descriptor, null, null) ?: return
        val result = CsleCustomDictionary.parse(String(file.contentsToByteArray(), Charsets.UTF_8))
        val importTitle = CsleBundle.message("custom.dictionary.import")
        if (result.entries.isEmpty() && result.errors.isEmpty()) {
            Messages.showErrorDialog(CsleBundle.message("custom.dictionary.no.entries"), importTitle)
            return
        }
        if (result.entries.size > MAX_IMPORT_ENTRIES) {
            Messages.showErrorDialog(
                CsleBundle.message("custom.dictionary.import.too.many", result.entries.size, MAX_IMPORT_ENTRIES),
                importTitle,
            )
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
        wrapper.file.writeText(CsleCustomDictionary.templateFor(currentGlyph), Charsets.UTF_8)
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

    private companion object {
        /** 单次导入的词条上限，超出时拒绝导入，避免弹窗卡顿 */
        const val MAX_IMPORT_ENTRIES = 20000
        const val SEARCH_COLLAPSED = "collapsed"
        const val SEARCH_EXPANDED = "expanded"
    }
}
