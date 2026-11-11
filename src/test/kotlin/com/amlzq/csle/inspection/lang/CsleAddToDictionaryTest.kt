package com.amlzq.csle.inspection.lang

import com.amlzq.csle.inspection.CsleAddToDictionaryQuickFix
import com.amlzq.csle.inspection.CsleBasePlatformTest
import com.amlzq.csle.inspection.CsleLocalQuickFix
import com.intellij.codeInspection.InspectionManager

/**
 * “添加到自定义词典”快速修复：随转换修复一起附带，但不参与批量修复。
 */
class CsleAddToDictionaryTest : CsleBasePlatformTest() {

    fun testProblemCarriesAddToDictionaryFixAfterConvertFix() {
        val file = myFixture.configureByText("a.kt", "const val SOFTWARE = \"软件\"")
        val problems = CsleKotlinInspection().checkFile(file, InspectionManager.getInstance(file.project), true)
            ?: emptyArray()
        assertEquals(1, problems.size)
        val fixes = problems[0].fixes ?: error("no fixes")
        assertEquals(2, fixes.size)
        assertTrue(fixes[0] is CsleLocalQuickFix)
        val add = fixes[1] as CsleAddToDictionaryQuickFix
        assertEquals("Add to custom dictionary", add.name)
        assertFalse(add.startInWriteAction())
        assertEquals(com.intellij.codeInsight.intention.PriorityAction.Priority.LOW, add.priority)
    }

    fun testSourceOfStripsQuotes() {
        assertEquals("软件", CsleAddToDictionaryQuickFix.sourceOf("\"软件\""))
        assertEquals("软件", CsleAddToDictionaryQuickFix.sourceOf("'软件'"))
        assertEquals("软件", CsleAddToDictionaryQuickFix.sourceOf("\"\"\"软件\"\"\""))
        assertEquals("软件", CsleAddToDictionaryQuickFix.sourceOf("软件"))
        assertEquals("\"", CsleAddToDictionaryQuickFix.sourceOf("\""))
    }
}
