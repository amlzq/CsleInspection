package com.amlzq.csle.inspection.lang

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NotNull

/**
 * HTML 文件的汉字检查入口（Inspections 列表中 HTML 组下的独立开关）。
 *
 * 与 [CsleXMLInspection] 共享 [CsleMarkupInspection] 的全部逻辑；
 * HTML 是 XML 的方言，需显式限定只处理 HTML 文件。
 */
class CsleHTMLInspection : CsleMarkupInspection() {

    @NotNull
    override fun checkFile(
        @NotNull file: PsiFile,
        @NotNull manager: InspectionManager,
        isOnTheFly: Boolean
    ): @NotNull Array<ProblemDescriptor>? {
        if (!file.language.id.equals("HTML", ignoreCase = true)) return null
        return super.checkFile(file, manager, isOnTheFly)
    }
}
