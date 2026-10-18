package com.amlzq.csle.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NotNull

/**
 * XML 文件的汉字检查入口（Inspections 列表中 XML 组下的独立开关）。
 *
 * HTML 是 XML 的方言，注册 language="XML" 的检查同样会在 HTML 文件上触发；
 * HTML 文件交由 [CsleHTMLInspection] 处理，此处跳过以避免重复检查。
 */
class CsleXMLInspection : CsleMarkupInspection() {

    @NotNull
    override fun checkFile(
        @NotNull file: PsiFile,
        @NotNull manager: InspectionManager,
        isOnTheFly: Boolean
    ): @NotNull Array<ProblemDescriptor>? {
        if (file.language.id.equals("HTML", ignoreCase = true)) return null
        return super.checkFile(file, manager, isOnTheFly)
    }
}
