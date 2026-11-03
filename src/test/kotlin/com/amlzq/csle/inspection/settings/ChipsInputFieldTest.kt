package com.amlzq.csle.inspection.settings

import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.intellij.ui.components.JBTextField
import java.awt.Component
import java.awt.Container

/** 排除列表的标签输入框：值的归一化、提交与布局不抛异常。 */
class ChipsInputFieldTest : CsleBasePlatformTest() {

    private fun inputOf(field: ChipsInputField): JBTextField {
        fun find(c: Container): JBTextField? {
            for (child: Component in c.components) {
                if (child is JBTextField) return child
                if (child is Container) find(child)?.let { return it }
            }
            return null
        }
        return find(field)!!
    }

    fun testSetItemsTrimsAndDeduplicates() {
        val field = ChipsInputField()
        field.items = listOf(" a ", "", "b", "a")
        assertEquals(listOf("a", "b"), field.items)
    }

    fun testPendingTextCountsAndCommitSplitsOnSeparators() {
        val field = ChipsInputField()
        field.items = listOf("a")
        val input = inputOf(field)

        input.text = "b, c\nd.e@f,a"
        // 未提交的文字也算作当前值，且与已有标签去重
        assertEquals(listOf("a", "b", "c", "d.e@f"), field.items)

        field.commitPending()
        assertEquals("", input.text)
        assertEquals(listOf("a", "b", "c", "d.e@f"), field.items)
    }

    fun testManyChipsLayoutWithoutError() {
        val field = ChipsInputField()
        field.items = (1..80).map { "name$it" }
        field.setSize(400, 10)
        field.doLayout()
        field.components.forEach { (it as? Container)?.doLayout() }
        assertEquals(80, field.items.size)
        assertTrue(field.preferredSize.height > 0)
    }
}
