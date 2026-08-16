package com.procwatch

import com.procwatch.privileged.ShizukuAppController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dumpsys output format is the one thing in this app that can break on an OS update
 * without any code change, so it is the one thing pinned by tests.
 *
 * When something breaks on a new Android build: capture the real output with
 *   adb shell dumpsys meminfo > sample.txt
 * paste it in as a new case, and fix the regex against it.
 */
class ShizukuParserTest {

    private val meminfoSample = """
Applications Memory Usage (in Kilobytes):
Uptime: 1234567 Realtime: 1234567

Total PSS by process:
    311,704K: com.google.android.apps.photos (pid 12345)
    250,432K: system (pid 1234)
     98,120K: com.whatsapp (pid 4321 / activities)
     12,004K: com.procwatch (pid 9999)

Total PSS by OOM adjustment:
    562,136K: Native
""".trimIndent()

    @Test
    fun `parses pss rows with thousands separators`() {
        val processes = ShizukuAppController.parseMeminfo(meminfoSample)
        assertEquals(4, processes.size)
        assertEquals(311_704L, processes[0].pssKb)
        assertEquals(12345, processes[0].pid)
        assertEquals("com.google.android.apps.photos", processes[0].name)
    }

    @Test
    fun `stops at the next section heading`() {
        val processes = ShizukuAppController.parseMeminfo(meminfoSample)
        assertTrue(processes.none { it.name == "Native" })
    }

    @Test
    fun `maps sub-process names back to their package`() {
        val processes = ShizukuAppController.parseMeminfo(
            "Total PSS by process:\n    5,000K: com.foo:push (pid 77)\n\nTotal PSS by category:"
        )
        assertEquals("com.foo", processes.single().owningPackage)
    }

    @Test
    fun `returns nothing when the section is missing`() {
        assertTrue(ShizukuAppController.parseMeminfo("unexpected output").isEmpty())
    }

    @Test
    fun `ps fallback skips the header row`() {
        val processes = ShizukuAppController.parsePs(
            """
            PID NAME
            1 init
            4321 com.whatsapp
            """.trimIndent()
        )
        assertEquals(listOf("init", "com.whatsapp"), processes.map { it.name })
    }
}
