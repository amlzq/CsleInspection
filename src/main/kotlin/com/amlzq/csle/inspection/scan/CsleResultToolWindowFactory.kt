package com.amlzq.csle.inspection.scan

import com.amlzq.csle.inspection.util.CsleBundle
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
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
        // 静默扫描时窗口尚未创建内容，结果先暂存，窗口第一次打开时补上
        project.getUserData(PENDING)?.let { (result, notice) ->
            project.putUserData(PENDING, null)
            panel.showResult(result, notice)
        }
    }

    companion object {
        const val ID = "CsleInspection"

        private val PENDING = Key.create<Pair<ScanResult, String?>>("csle.pending.scan.result")

        /**
         * 展示 [result]，[notice] 显示在汇总前面。须在 EDT 调用。
         * [activate] 为 false 时只更新内容（窗口还没创建内容则暂存），不展开、不抢焦点。
         */
        fun show(project: Project, result: ScanResult, notice: String? = null, activate: Boolean = true) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return
            // 窗口已有内容时直接更新：已激活的窗口再调用 activate(callback) 不一定会执行回调，
            // 之前"全部接受"后重扫的结果因此没有刷新
            fun update() {
                val panel = toolWindow.contentManager.getContent(0)?.component as? CsleResultPanel
                panel?.showResult(result, notice)
            }
            when {
                toolWindow.contentManager.contentCount > 0 -> {
                    update()
                    if (activate) toolWindow.activate(null)
                }
                // 首次打开：内容在窗口显示时才创建
                activate -> toolWindow.activate { update() }
                else -> project.putUserData(PENDING, result to notice)
            }
        }

        /** 打开 Tool Window，不改变其中内容。 */
        fun open(project: Project) {
            ToolWindowManager.getInstance(project).getToolWindow(ID)?.activate(null)
        }
    }
}
