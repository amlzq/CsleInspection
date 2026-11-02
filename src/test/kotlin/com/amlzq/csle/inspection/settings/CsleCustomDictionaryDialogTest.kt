package com.amlzq.csle.inspection.settings

import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.intellij.openapi.util.Disposer
import javax.swing.JPanel

class CsleCustomDictionaryDialogTest : CsleBasePlatformTest() {

    fun testOkNormalizesTextAndDropsComments() {
        val dialog = CsleCustomDictionaryDialog(JPanel(), CsleGlyphs.TAIWAN, "# 注释\n\n服务器 伺服器\n软件\t軟體 软件\n")
        try {
            dialog.performOKAction()
            assertEquals("服务器\t伺服器\n软件\t軟體\n", dialog.resultText)
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    fun testEnterOnAddRowItemAddsRow() {
        val dialog = CsleCustomDictionaryDialog(JPanel(), CsleGlyphs.TAIWAN, "服务器\t伺服器\n")
        try {
            val table = dialog.table
            // 1 条词条 + 末尾的“新增行”条目
            assertEquals(2, table.rowCount)
            table.setRowSelectionInterval(1, 1)
            val key = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0)
            val actionKey = table.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(key)
            table.actionMap.get(actionKey).actionPerformed(
                java.awt.event.ActionEvent(table, java.awt.event.ActionEvent.ACTION_PERFORMED, ""),
            )
            assertEquals(3, table.rowCount)
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    fun testCenterPanelCanBeBuilt() {
        val dialog = CsleCustomDictionaryDialog(JPanel(), CsleGlyphs.TAIWAN, "服务器\t伺服器\n")
        try {
            val method = com.intellij.openapi.ui.DialogWrapper::class.java.getDeclaredMethod("createCenterPanel")
            method.isAccessible = true
            assertNotNull(method.invoke(dialog))
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    private fun validate(dialog: CsleCustomDictionaryDialog): Any? {
        val method = com.intellij.openapi.ui.DialogWrapper::class.java.getDeclaredMethod("doValidate")
        method.isAccessible = true
        return method.invoke(dialog)
    }

    fun testValidateIgnoresAddRowItem() {
        val dialog = CsleCustomDictionaryDialog(JPanel(), CsleGlyphs.TAIWAN, "服务器\t伺服器\n")
        try {
            assertNull(validate(dialog))
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }

    fun testValidateReportsIncompleteRow() {
        val dialog = CsleCustomDictionaryDialog(JPanel(), CsleGlyphs.TAIWAN, "服务器\t伺服器\n")
        try {
            dialog.table.setValueAt("", 0, 2)
            assertNotNull(validate(dialog))
        } finally {
            Disposer.dispose(dialog.disposable)
        }
    }
}
