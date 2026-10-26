package com.amlzq.csle.inspection

import com.amlzq.csle.inspection.scan.CsleBatchFixer
import com.amlzq.csle.inspection.scan.CsleProjectScanner
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class CsleBatchFixerTest : CsleBasePlatformTest() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        LightJavaCodeInsightFixtureTestCase.JAVA_21

    private val source = "public class A {\n" +
        "    String a = \"软件\";\n" +
        "    String b = \"软件\" + \"硬盘\";\n" +
        "}"

    fun testAcceptAllConvertsEveryOccurrence() {
        val psi = myFixture.addFileToProject("A.java", source)
        val items = CsleProjectScanner(project).scan().items
        assertEquals(3, items.size)

        val result = CsleBatchFixer.apply(project, items)

        assertEquals(3, result.fixed)
        assertTrue(result.failed.isEmpty())
        val text = psi.viewProvider.document!!.text
        assertFalse(text, text.contains("软件"))
        assertTrue(text, text.contains("軟體") || text.contains("軟件"))
        assertTrue(CsleProjectScanner(project).scan().items.isEmpty())
    }

    fun testOnlyGivenItemsAreConverted() {
        val psi = myFixture.addFileToProject("A.java", source)
        val items = CsleProjectScanner(project).scan().items
        val result = CsleBatchFixer.apply(project, items.take(1))

        assertEquals(1, result.fixed)
        assertEquals(2, CsleProjectScanner(project).scan().items.size)
        assertTrue(psi.viewProvider.document!!.text.contains("软件"))
    }

    fun testStaleItemIsReportedAsFailed() {
        val psi = myFixture.addFileToProject("A.java", source)
        val items = CsleProjectScanner(project).scan().items
        // 扫描之后文件被改动：第一条的文本已经变了
        val doc = psi.viewProvider.document!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            val offset = doc.text.indexOf("\"软件\"")
            doc.replaceString(offset, offset + 4, "\"别的\"")
            com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(doc)
        }
        val result = CsleBatchFixer.apply(project, items)
        assertEquals(2, result.fixed)
        assertEquals(1, result.failed.size)
        assertTrue(doc.text.contains("别的"))
    }

    fun testSingleUndoRevertsWholeBatch() {
        val psi = myFixture.addFileToProject("A.java", source)
        myFixture.openFileInEditor(psi.virtualFile)
        val items = CsleProjectScanner(project).scan().items
        CsleBatchFixer.apply(project, items)
        val doc = psi.viewProvider.document!!
        assertFalse(doc.text.contains("软件"))

        val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
            .getSelectedEditor(psi.virtualFile)
        val undo = UndoManager.getInstance(project)
        assertTrue(undo.isUndoAvailable(editor))
        undo.undo(editor)
        assertEquals(source, doc.text)
    }
}
