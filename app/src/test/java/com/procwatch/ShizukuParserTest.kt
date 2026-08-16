package com.procwatch

import com.procwatch.privileged.ShizukuAppController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /**
     * Section order captured from a real Poco X6 on HyperOS / Android 14. The RSS sections
     * come *first* there, which the earlier synthetic sample never exercised: a parser that
     * looked for "Total ... by process:" loosely, or that started from the top of the output,
     * would report RSS figures as if they were PSS and overstate every app.
     *
     * Package names are anonymised on purpose — this repository is public and the real dump
     * lists everything installed on the device.
     */
    private val hyperOsSample = """
Applications Memory Usage (in Kilobytes):
Uptime: 62758541 Realtime: 71813337

Total RSS by process:
    715,852K: system (pid 2837)
    548,592K: com.example.messenger (pid 12226 / activities)
    414,076K: com.android.systemui (pid 4540)

Total RSS by OOM adjustment:
    1,678,520K: Native

Total RSS by category:
    1,678,520K: Native

Total PSS by process:
    580,137K: system (pid 2837)
    393,662K: com.example.launcher (pid 4083 / activities)
    258,043K: com.example.messenger (pid 12226 / activities)

Total PSS by OOM adjustment:
    1,231,842K: Native

Total PSS by category:
    1,231,842K: Native
""".trimIndent()

    @Test
    fun `reads the pss section when rss sections come first`() {
        val processes = ShizukuAppController.parseMeminfo(hyperOsSample)

        // Three PSS rows, and none of the RSS figures.
        assertEquals(3, processes.size)
        assertEquals(listOf(580_137L, 393_662L, 258_043L), processes.map { it.pssKb })
        assertTrue(processes.none { it.pssKb == 715_852L })
        assertTrue(processes.none { it.name == "Native" })
    }

    @Test
    fun `sums every total pss block for a multi-process app`() {
        val twoProcesses = """
** MEMINFO in pid 4321 [com.whatsapp] **
                 TOTAL PSS:    98,120            TOTAL RSS:   180,004

** MEMINFO in pid 4400 [com.whatsapp:push] **
                 TOTAL PSS:    24,880            TOTAL RSS:    61,220
""".trimIndent()
        assertEquals(123_000L, ShizukuAppController.parseTotalPss(twoProcesses))
    }

    @Test
    fun `counts the summary once when a block repeats it`() {
        // Some builds print the figure in the table footer and again in App Summary.
        val repeatedWithinOneBlock = """
** MEMINFO in pid 4321 [com.whatsapp] **
                 TOTAL PSS:    98,120            TOTAL RSS:   180,004

 App Summary
                TOTAL PSS:    98,120            TOTAL RSS:   180,004
""".trimIndent()
        assertEquals(98_120L, ShizukuAppController.parseTotalPss(repeatedWithinOneBlock))
    }

    @Test
    fun `total pss is null when the app is not running`() {
        assertNull(ShizukuAppController.parseTotalPss("No process found for: com.foo"))
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
