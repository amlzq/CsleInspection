package com.amlzq.csle.inspection.util

import com.intellij.openapi.project.Project

/**
 * 当前检查/修复所属的项目，用来决定读取项目级还是应用级设置。
 *
 * 检查在 checkFile 内同步完成，快速修复在创建时记录项目，所以用线程内的上下文即可，
 * 不必把 Project 传进每一处读取设置的地方。上下文之外（例如单元测试直接调用）读取应用级设置。
 */
object CsleContext {
    private val current = ThreadLocal<Project?>()

    fun project(): Project? = current.get()

    fun <T> with(project: Project?, block: () -> T): T {
        val previous = current.get()
        current.set(project)
        try {
            return block()
        } finally {
            current.set(previous)
        }
    }
}
