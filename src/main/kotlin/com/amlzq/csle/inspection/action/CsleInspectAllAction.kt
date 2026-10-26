package com.amlzq.csle.inspection.action

import com.amlzq.csle.inspection.CsleBundle
import com.amlzq.csle.inspection.scan.CsleProjectScanner
import com.amlzq.csle.inspection.scan.ScanResult
import com.amlzq.csle.inspection.ui.CsleResultToolWindowFactory
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware

/** Tools | Chinese Expression Inspection | Inspect All Files：后台扫描整个项目，并在 Tool Window 中展示结果。 */
class CsleInspectAllAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ProgressManager.getInstance().run(object :
            Task.Backgroundable(project, CsleBundle.message("scan.progress.title"), true) {
            private var result: ScanResult? = null

            override fun run(indicator: ProgressIndicator) {
                result = CsleProjectScanner(project).scan(indicator)
            }

            // onSuccess 在 EDT 上执行；扫描被取消或出错时不会调用
            override fun onSuccess() {
                result?.let { CsleResultToolWindowFactory.show(project, it) }
            }
        })
    }
}
