package com.amlzq.csle.inspection.scan

import junit.framework.TestCase

class CsleAutoScanStartupActivityTest : TestCase() {

    private fun should(
        enabled: Boolean = true,
        isDefault: Boolean = false,
        disposed: Boolean = false,
        testOrHeadless: Boolean = false,
        running: Boolean = false,
    ) = CsleAutoScanStartupActivity.shouldAutoScan(enabled, isDefault, disposed, testOrHeadless, running)

    fun testRunsWhenEnabled() = assertTrue(should())

    fun testSkipsWhenDisabled() = assertFalse(should(enabled = false))

    fun testSkipsDefaultProject() = assertFalse(should(isDefault = true))

    fun testSkipsDisposedProject() = assertFalse(should(disposed = true))

    fun testSkipsTestOrHeadless() = assertFalse(should(testOrHeadless = true))

    fun testSkipsWhenScanAlreadyRunning() = assertFalse(should(running = true))
}
