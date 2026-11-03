package com.amlzq.csle.inspection.settings

import com.amlzq.csle.inspection.util.CsleBundle
import com.intellij.icons.AllIcons
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

/**
 * 类似 InputChip 的多值输入框：已有值显示为带 × 的圆角标签，末尾是输入框。
 *
 * - 回车 / 逗号 / 粘贴多行文本 → 添加；失焦时把未提交的文字也提交
 * - 输入框为空时按 Backspace → 删除最后一个；选中标签后按 Delete / Backspace → 删除该标签
 * - 标签超过 [MAX_COLLAPSED_ROWS] 行时默认折叠，底部出现「展开 / 收起」链接
 *
 * 分隔符只用逗号和换行：排除项里常见 `div@class`、`a.b.c`、`--my-var` 这类带符号的名称，不能拆开。
 */
class ChipsInputField : JPanel(BorderLayout()) {

    private val chips = mutableListOf<ChipComponent>()
    private val input = JBTextField()
    private val flow = object : JPanel() {
        // 自绘圆角底和边框，与设置页里其他圆角控件保持一致
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = JBUI.scale(8)
                g2.color = UIUtil.getTextFieldBackground()
                g2.fillRoundRect(0, 0, width, height, arc, arc)
                val focused = input.hasFocus()
                val stroke = if (focused) JBUI.scale(2) else 1
                g2.color = if (focused) JBUI.CurrentTheme.Focus.focusColor() else JBColor.border()
                g2.stroke = BasicStroke(stroke.toFloat())
                val inset = stroke / 2f
                g2.draw(
                    RoundRectangle2D.Float(
                        inset, inset, width - stroke.toFloat(), height - stroke.toFloat(),
                        arc.toFloat(), arc.toFloat(),
                    ),
                )
            } finally {
                g2.dispose()
            }
        }
    }
    private val layout = ChipFlowLayout(input)
    private val toggleLink = ActionLink()
    private val toggleBar = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
    private var linkState: Pair<Boolean, Int>? = null

    /** 当前所有值；包含输入框里尚未提交的文字（去重后），所以「正在输入」也算作修改 */
    var items: List<String>
        get() = (chips.map { it.text } + split(input.text)).distinct()
        set(value) {
            input.text = ""
            chips.forEach { flow.remove(it) }
            chips.clear()
            value.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { addChip(it) }
            layout.expanded = false
            refresh()
        }

    init {
        flow.layout = layout
        flow.isOpaque = false
        flow.border = JBUI.Borders.empty(5, 7)

        input.apply {
            border = JBUI.Borders.empty()
            isOpaque = false
            emptyText.text = CsleBundle.message("excluded.chips.placeholder")
            // 默认聚焦时会隐藏占位符；这里改成只要为空就显示，聚焦后也能看出该输入什么
            putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, Predicate<JBTextField> { it.text.isEmpty() })
            // 默认的 JTextField 会把粘贴内容里的换行替换成空格，这里要保留换行才能按行拆分
            document.putProperty("filterNewlines", false)
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_ENTER -> {
                            // 不让回车落到设置对话框的默认按钮上
                            e.consume()
                            commitPending()
                        }

                        KeyEvent.VK_BACK_SPACE -> if (text.isEmpty() && chips.isNotEmpty()) {
                            e.consume()
                            removeChip(chips.last(), refocus = false)
                        }
                    }
                }

                override fun keyTyped(e: KeyEvent) {
                    if (e.keyChar == ',' || e.keyChar == '，') {
                        e.consume()
                        commitPending()
                    }
                }
            })
            document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    // 粘贴带分隔符的文本：文档监听器里不能改文档，延后提交
                    if (text.any { it in SEPARATORS }) SwingUtilities.invokeLater { commitPending() }
                }
            })
            addFocusListener(object : FocusAdapter() {
                override fun focusGained(e: FocusEvent) = flow.repaint()
                override fun focusLost(e: FocusEvent) {
                    flow.repaint()
                    commitPending()
                }
            })
        }
        flow.add(input)
        flow.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                input.requestFocusInWindow()
            }
        })
        // 宽度变化会改变换行后的行数，preferredSize 需要重新计算
        flow.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (flow.preferredSize.height != flow.height) flow.revalidate()
            }
        })

        layout.onInfo = { overflow, hidden -> updateToggle(overflow, hidden) }
        toggleLink.addActionListener {
            layout.expanded = !layout.expanded
            linkState = null
            flow.revalidate()
            flow.repaint()
        }
        toggleBar.isOpaque = false
        // 与框内第一个标签左对齐（边框 1px + 内边距 7px），并与框保持一点距离
        toggleBar.border = JBUI.Borders.empty(5, 8, 2, 0)
        toggleBar.isVisible = false
        toggleBar.add(toggleLink)

        add(flow, BorderLayout.CENTER)
        add(toggleBar, BorderLayout.SOUTH)
        refresh()
    }

    /** 把输入框里的文字提交为标签；apply 之前也要调用，避免丢失正在输入的内容 */
    fun commitPending() {
        val parts = split(input.text)
        input.text = ""
        var added = false
        for (part in parts) {
            if (chips.none { it.text == part }) {
                addChip(part)
                added = true
            }
        }
        if (added) {
            // 新加的标签如果落在折叠区，用户会以为没加上：直接展开
            layout.expanded = true
            refresh()
        }
    }

    private fun addChip(text: String) {
        val chip = ChipComponent(text) { removeChip(it, refocus = true) }
        chips.add(chip)
        flow.add(chip, chips.size - 1)
    }

    private fun removeChip(chip: ChipComponent, refocus: Boolean) {
        if (!chips.remove(chip)) return
        flow.remove(chip)
        refresh()
        if (refocus) input.requestFocusInWindow()
    }

    private fun refresh() {
                flow.revalidate()
        flow.repaint()
    }

    private fun updateToggle(overflow: Boolean, hidden: Int) {
        val state = overflow to hidden
        if (state == linkState) return
        linkState = state
        // 在布局过程中改可见性 / 文案会再次触发布局，延后到布局结束
        SwingUtilities.invokeLater {
            toggleBar.isVisible = overflow
            toggleLink.text = if (layout.expanded) {
                CsleBundle.message("excluded.chips.collapse")
            } else {
                CsleBundle.message("excluded.chips.expand", hidden)
            }
            revalidate()
        }
    }

    private fun split(text: String): List<String> =
        text.split(*SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        private const val MAX_COLLAPSED_ROWS = 3
        private const val MIN_ROWS = 1
        private val SEPARATORS = charArrayOf(',', '，', '\n', '\r')
    }

    /** 单个标签：圆角底 + 文字 + × */
    private class ChipComponent(val text: String, private val onRemove: (ChipComponent) -> Unit) : JComponent() {
        private val closeIcon = AllIcons.Actions.Close
        private val closeHoverIcon = AllIcons.Actions.CloseHovered
        private var closeHovered = false

        init {
            isOpaque = false
            isFocusable = true
            font = JBUI.Fonts.label()
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (closeBounds().contains(e.point)) onRemove(this@ChipComponent) else requestFocusInWindow()
                }

                override fun mouseExited(e: MouseEvent) = setHover(false)
            })
            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) = setHover(closeBounds().contains(e.point))
            })
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_DELETE || e.keyCode == KeyEvent.VK_BACK_SPACE) {
                        e.consume()
                        onRemove(this@ChipComponent)
                    }
                }
            })
            addFocusListener(object : FocusAdapter() {
                override fun focusGained(e: FocusEvent) = repaint()
                override fun focusLost(e: FocusEvent) = repaint()
            })
        }

        private fun setHover(value: Boolean) {
            if (closeHovered == value) return
            closeHovered = value
            cursor = Cursor.getPredefinedCursor(if (value) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
            repaint()
        }

        private fun closeBounds(): Rectangle {
            val w = closeIcon.iconWidth
            val h = closeIcon.iconHeight
            return Rectangle(width - w - JBUI.scale(4), (height - h) / 2, w, h)
        }

        override fun getPreferredSize(): Dimension {
            val fm = getFontMetrics(font)
            val w = JBUI.scale(8) + fm.stringWidth(text) + JBUI.scale(4) + closeIcon.iconWidth + JBUI.scale(4)
            return Dimension(w, fm.height + JBUI.scale(6))
        }

        override fun getMinimumSize(): Dimension = preferredSize

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = JBUI.scale(10)
                g2.color = CHIP_BACKGROUND
                g2.fillRoundRect(0, 0, width, height, arc, arc)
                if (isFocusOwner) {
                    g2.color = JBUI.CurrentTheme.Focus.focusColor()
                    g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
                }
                val close = closeBounds()
                g2.clipRect(0, 0, close.x, height)
                g2.color = foreground
                g2.font = font
                val fm = g2.fontMetrics
                g2.drawString(text, JBUI.scale(8), (height - fm.height) / 2 + fm.ascent)
                g2.clip = null
                (if (closeHovered) closeHoverIcon else closeIcon).paintIcon(this, g2, close.x, close.y)
            } finally {
                g2.dispose()
            }
        }

        companion object {
            private val CHIP_BACKGROUND = JBColor(0xDFE1E5, 0x4E5157)
        }
    }

    /**
     * 流式换行布局：标签依次排列，放不下就换行；输入框始终是最后一项并占满所在行剩余宽度。
     * 折叠时只显示能放进 [MAX_COLLAPSED_ROWS] 行的标签（输入框始终保留），其余隐藏。
     */
    private class ChipFlowLayout(private val input: Component) : LayoutManager {
        var expanded = false
        var onInfo: ((overflow: Boolean, hidden: Int) -> Unit)? = null

        private val hgap get() = JBUI.scale(4)
        private val vgap get() = JBUI.scale(4)

        private class Plan(val rects: List<Rectangle>, val rows: Int, val totalRows: Int, val visibleChips: Int)

        override fun addLayoutComponent(name: String?, comp: Component?) = Unit
        override fun removeLayoutComponent(comp: Component?) = Unit

        private fun rowHeight(chips: List<Component>): Int =
            maxOf(input.preferredSize.height, chips.maxOfOrNull { it.preferredSize.height } ?: 0)

        /** 把宽度为 [avail] 的一行行排好；Rectangle 里 y 暂存行号，x / width 为最终值 */
        private fun arrange(chips: List<Component>, avail: Int): Pair<List<Rectangle>, Int> {
            val rects = ArrayList<Rectangle>(chips.size + 1)
            var x = 0
            var row = 0
            for (chip in chips) {
                val w = minOf(chip.preferredSize.width, avail)
                if (x > 0 && x + w > avail) {
                    row++
                    x = 0
                }
                rects.add(Rectangle(x, row, w, 0))
                x += w + hgap
            }
            val minInput = minOf(JBUI.scale(180), avail)
            if (x > 0 && avail - x < minInput) {
                row++
                x = 0
            }
            rects.add(Rectangle(x, row, maxOf(avail - x, minInput), 0))
            return rects to row + 1
        }

        private fun plan(parent: Container, avail: Int): Plan {
            val chips = parent.components.filter { it !== input }
            val (fullRects, totalRows) = arrange(chips, avail)
            if (expanded || totalRows <= MAX_COLLAPSED_ROWS) {
                return Plan(fullRects, totalRows, totalRows, chips.size)
            }
            var visible = chips.size
            var arranged = fullRects to totalRows
            while (visible > 0) {
                visible--
                arranged = arrange(chips.take(visible), avail)
                if (arranged.second <= MAX_COLLAPSED_ROWS) break
            }
            return Plan(arranged.first, arranged.second, totalRows, visible)
        }

        private fun availableWidth(parent: Container): Int {
            val insets = parent.insets
            val width = if (parent.width > 0) parent.width else JBUI.scale(400)
            return maxOf(width - insets.left - insets.right, JBUI.scale(40))
        }

        override fun preferredLayoutSize(parent: Container): Dimension {
            synchronized(parent.treeLock) {
                val plan = plan(parent, availableWidth(parent))
                val rowH = rowHeight(parent.components.filter { it !== input })
                val rows = maxOf(plan.rows, MIN_ROWS)
                val insets = parent.insets
                val height = rows * rowH + (rows - 1) * vgap + insets.top + insets.bottom
                return Dimension(JBUI.scale(100) + insets.left + insets.right, height)
            }
        }

        override fun minimumLayoutSize(parent: Container): Dimension = preferredLayoutSize(parent)

        override fun layoutContainer(parent: Container) {
            synchronized(parent.treeLock) {
                val insets = parent.insets
                val chips = parent.components.filter { it !== input }
                val plan = plan(parent, availableWidth(parent))
                val rowH = rowHeight(chips)
                fun place(c: Component, r: Rectangle) {
                    val h = minOf(c.preferredSize.height, rowH)
                    c.setBounds(
                        insets.left + r.x,
                        insets.top + r.y * (rowH + vgap) + (rowH - h) / 2,
                        r.width,
                        h,
                    )
                }
                chips.forEachIndexed { i, chip ->
                    val show = i < plan.visibleChips
                    if (chip.isVisible != show) chip.isVisible = show
                    if (show) place(chip, plan.rects[i])
                }
                place(input, plan.rects.last())
                val overflow = plan.totalRows > MAX_COLLAPSED_ROWS
                onInfo?.invoke(overflow, chips.size - plan.visibleChips)
            }
        }
    }
}
