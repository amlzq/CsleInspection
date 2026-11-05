package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CsleLocalInspectionTool.cleanPattern] 纯逻辑测试：
 * 清理字母、数字、空白与标点后，只应剩下汉字。
 */
class CsleCleanPatternTest {

    private val inspection = object : CsleLocalInspectionTool() {
        override fun inExcludedCallExpression(element: PsiElement): Boolean = false

        override fun doCheckFile(
            file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean
        ): Array<ProblemDescriptor>? = null
    }

    private fun clean(text: String): String =
        inspection.cleanPattern.matcher(text).replaceAll("")

    @Test
    fun stripsAsciiLettersDigitsSpacesAndPunctuation() {
        assertTrue(clean("Hello abc 123 .,!?").isEmpty())
    }

    @Test
    fun stripsChinesePunctuationAndFullWidthSpace() {
        assertTrue(clean("，。！？；：（）、“”——　").isEmpty())
    }

    @Test
    fun keepsHanCharacters() {
        assertFalse(clean("软件开发").isEmpty())
        assertFalse(clean("简体繁体").isEmpty())
    }

    @Test
    fun keepsOnlyHanCharactersInMixedText() {
        assertEquals("软件", clean("Hello 软件 world!"))
    }
}
