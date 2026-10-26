package com.amlzq.csle.inspection.scan

import com.amlzq.csle.inspection.util.CsleBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

/** 在后台扫描整个项目，完成后在 Tool Window 展示结果。菜单动作、Rescan 和批量接受后的重扫都走这里。 */
object CsleScanRunner {

    private val LOG = Logger.getInstance(CsleScanRunner::class.java)

    private val RUNNING = Key.create<Boolean>("csle.scan.running")

    fun isRunning(project: Project): Boolean = project.getUserData(RUNNING) == true

    /**
     * [notice] 会显示在结果汇总前面，例如"已转换 N 处"。
     * 同一项目已有扫描在运行时不再启动，返回 false。
     */
    fun start(
        project: Project,
        notice: String? = null,
        onResult: (ScanResult, String?) -> Unit = { result, text -> CsleResultToolWindowFactory.show(project, result, text) },
    ): Boolean {
        synchronized(project) {
            if (isRunning(project)) return false
            project.putUserData(RUNNING, true)
        }
        ProgressManager.getInstance().run(object :
            Task.Backgroundable(project, CsleBundle.message("scan.progress.title"), true) {
            private var result: ScanResult? = null

            override fun run(indicator: ProgressIndicator) {
                LOG.info("scan started")
                result = CsleProjectScanner(project).scan(indicator)
                LOG.info("scan finished: ${result?.items?.size} item(s)")
            }

            override fun onFinished() {
                project.putUserData(RUNNING, null)
            }

            override fun onCancel() {
                LOG.info("scan cancelled")
            }

            override fun onThrowable(error: Throwable) {
                LOG.warn("scan failed", error)
            }

            // onSuccess 在 EDT 上执行；扫描被取消或出错时不会调用
            override fun onSuccess() {
                LOG.info("scan result delivered")
                result?.let { onResult(it, notice) }
            }
        })
        return true
    }
}
