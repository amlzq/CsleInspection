package com.amlzq.csle.inspection.scan

import com.amlzq.csle.inspection.util.CsleBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object CsleNotifications {
    const val GROUP_ID = "CsleInspection"

    /** 手动触发扫描时已有扫描在运行：给出提示，不再并发启动。 */
    fun scanAlreadyRunning(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(CsleBundle.message("scan.already.running"), NotificationType.INFORMATION)
            .notify(project)
    }
}
