package com.amlzq.csle.inspection

import com.amlzq.csle.inspection.settings.CsleAddToDictionaryDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware

/** 编辑器右键菜单：把选中的文字添加到自定义词典，交互与快速修复一致。 */
class CsleAddToDictionaryAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible =
            e.project != null && editor != null && !editor.selectionModel.selectedText.isNullOrBlank()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val text = e.getData(CommonDataKeys.EDITOR)?.selectionModel?.selectedText ?: return
        CsleAddToDictionaryDialog.addEntry(project, text)
    }
}
