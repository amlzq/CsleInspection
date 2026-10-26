package com.amlzq.csle.inspection

import com.amlzq.csle.inspection.scan.CsleBatchFixer
import com.amlzq.csle.inspection.scan.CsleProjectScanner
import com.amlzq.csle.inspection.scan.CsleScanRunner
import com.amlzq.csle.inspection.scan.ScanResult
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/** 模拟"全部接受"之后的重扫：后台扫描必须能完成并把结果交回 UI。 */
class CsleRescanFlowTest : CsleBasePlatformTest() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        LightJavaCodeInsightFixtureTestCase.JAVA_21

    private fun scanInBackground(notice: String?): Pair<ScanResult, String?>? {
        var got: Pair<ScanResult, String?>? = null
        CsleScanRunner.start(project, notice) { result, text -> got = result to text }
        val end = System.currentTimeMillis() + 20_000
        while (got == null && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            Thread.sleep(50)
        }
        return got
    }

    fun testRescanAfterAcceptDeliversResult() {
        myFixture.addFileToProject("A.java", "public class A { String a = \"软件\"; }")

        val first = scanInBackground(null)
        assertNotNull("first scan delivered", first)
        assertEquals(1, first!!.first.items.size)

        CsleBatchFixer.apply(project, CsleProjectScanner(project).scan().items)

        val second = scanInBackground("NOTICE")
        assertNotNull("rescan after accept delivered", second)
        assertEquals("NOTICE", second!!.second)
        assertTrue(second.first.items.isEmpty())
    }
}
