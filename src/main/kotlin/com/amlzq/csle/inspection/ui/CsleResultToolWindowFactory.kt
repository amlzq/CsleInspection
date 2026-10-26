package com.amlzq.csle.inspection.ui

import com.amlzq.csle.inspection.CsleBundle
import com.amlzq.csle.inspection.scan.ScanResult
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

/** 底部 Tool Window，承载全项目扫描的结果。 */
class CsleResultToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.stripeTitle = CsleBundle.message("toolwindow.title")
        val panel = CsleResultPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        const val ID = "CsleInspection"

        /** 打开 Tool Window 并展示 [result]，[notice] 显示在汇总前面。须在 EDT 调用。 */
        fun show(project: Project, result: ScanResult, notice: String? = null) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return
            // 窗口已有内容时直接更新：已激活的窗口再调用 activate(callback) 不一定会执行回调，
            // 之前"全部接受"后重扫的结果因此没有刷新
            fun update() {
                val panel = toolWindow.contentManager.getContent(0)?.component as? CsleResultPanel
                panel?.showResult(result, notice)
            }
            if (toolWindow.contentManager.contentCount > 0) {
                update()
                toolWindow.activate(null)
            } else {
                // 首次打开：内容在窗口显示时才创建
                toolWindow.activate { update() }
            }
        }
    }
}
