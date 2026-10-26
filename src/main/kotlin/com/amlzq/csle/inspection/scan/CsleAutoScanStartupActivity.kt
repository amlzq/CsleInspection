package com.amlzq.csle.inspection.scan

import com.amlzq.csle.inspection.settings.CsleSettings
import com.amlzq.csle.inspection.util.CsleBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** 项目打开并完成索引后，按设置自动扫描一次；有结果时只弹通知，不展开 Tool Window。 */
class CsleAutoScanStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val app = ApplicationManager.getApplication()
        if (!shouldAutoScan(
                enabled = CsleSettings.instance.state.autoScanOnOpen,
                isDefaultProject = project.isDefault,
                isDisposed = project.isDisposed,
                isTestOrHeadless = app.isUnitTestMode || app.isHeadlessEnvironment,
                isScanRunning = CsleScanRunner.isRunning(project),
            )
        ) return

        // 扫描依赖索引；回调在 EDT 上执行
        DumbService.getInstance(project).runWhenSmart {
            // 等待索引期间开关可能被关闭、项目可能被关闭、用户可能已手动开始扫描
            if (!shouldAutoScan(
                    enabled = CsleSettings.instance.state.autoScanOnOpen,
                    isDefaultProject = project.isDefault,
                    isDisposed = project.isDisposed,
                    isTestOrHeadless = false,
                    isScanRunning = CsleScanRunner.isRunning(project),
                )
            ) return@runWhenSmart
            CsleScanRunner.start(project, onResult = { result, notice ->
                if (result.items.isNotEmpty()) {
                    CsleResultToolWindowFactory.show(project, result, notice, activate = false)
                    notifyFound(project, result)
                }
            })
        }
    }

    private fun notifyFound(project: Project, result: ScanResult) {
        val fileCount = result.items.map { it.file }.distinct().size
        NotificationGroupManager.getInstance()
            .getNotificationGroup(CsleNotifications.GROUP_ID)
            .createNotification(
                CsleBundle.message("scan.auto.notification", result.items.size, fileCount),
                NotificationType.INFORMATION,
            )
            .addAction(NotificationAction.createSimpleExpiring(CsleBundle.message("scan.auto.notification.view")) {
                CsleResultToolWindowFactory.open(project)
            })
            .notify(project)
    }

    companion object {
        /** 是否应该自动扫描；抽成纯函数便于单测。 */
        fun shouldAutoScan(
            enabled: Boolean,
            isDefaultProject: Boolean,
            isDisposed: Boolean,
            isTestOrHeadless: Boolean,
            isScanRunning: Boolean,
        ): Boolean = enabled && !isDefaultProject && !isDisposed && !isTestOrHeadless && !isScanRunning
    }
}
