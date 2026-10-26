package com.amlzq.csle.inspection.action

import com.amlzq.csle.inspection.scan.CsleScanRunner
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/** Tools | Chinese Expression Inspection | Inspect All Files：后台扫描整个项目，并在 Tool Window 中展示结果。 */
class CsleInspectAllAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        CsleScanRunner.start(project)
    }
}
