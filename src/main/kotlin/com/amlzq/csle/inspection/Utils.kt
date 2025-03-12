package com.amlzq.csle.inspection

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.ide.actions.DartPubActionBase

object Utils {

    fun getRealVirtualFile(psiFile: PsiFile?): VirtualFile? {
        return psiFile?.originalFile?.virtualFile
    }

    fun isPubActionInProgress(): Boolean {
        return DartPubActionBase.isInProgress()
    }
}

private const val debug = false

fun debugPrintln(message: Any?) {
    if (debug) println("CsleInspection $message")
}