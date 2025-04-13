package com.amlzq.csle.inspection

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

object Utils {
    fun getRealVirtualFile(psiFile: PsiFile?): VirtualFile? {
        return psiFile?.originalFile?.virtualFile
    }
}

private const val debug = true

fun debugPrintln(message: Any?) {
    if (debug) println("CsleInspection $message")
}