package com.procwatch.privileged

import com.procwatch.core.ProcessInfo

/**
 * Full-strength controller. Everything runs through the shell UID.
 *
 * Process listing uses `dumpsys meminfo` rather than `ps` on purpose: it reports PSS
 * (proportional set size, the number that actually reflects an app's memory cost) in
 * kilobytes, keyed by process name and pid, in one call. `ps` reports RSS, which
 * double-counts shared pages across every process that maps them.
 */
class ShizukuAppController(private val shizuku: ShizukuManager) : AppController {

    override val name = "Shizuku"

    override fun isAvailable(): Boolean = shizuku.isReady && ShizukuShell.isUsable

    override fun capabilities(): Set<Capability> = setOf(
        Capability.LIST_PROCESSES,
        Capability.PER_APP_MEMORY,
        Capability.FORCE_STOP,
        Capability.FREEZE_APP,
        Capability.REVOKE_BACKGROUND
    )

    override suspend fun listProcesses(): Result<List<ProcessInfo>> {
        if (!isAvailable()) return Result.failure(UnsupportedByController(name, "list processes"))

        val meminfo = ShizukuShell.run("dumpsys meminfo")
        if (meminfo.ok) {
            val parsed = parseMeminfo(meminfo.output)
            if (parsed.isNotEmpty()) return Result.success(parsed)
        }

        // Fallback: at least establish which processes exist. We leave memory blank rather
        // than report RSS as if it were PSS.
        val ps = ShizukuShell.run("ps -A -o PID,NAME")
        if (!ps.ok) return Result.failure(Exception("dumpsys and ps both failed: ${meminfo.output.take(200)}"))
        return Result.success(parsePs(ps.output))
    }

    override suspend fun forceStop(packageName: String): Result<Unit> =
        runShell("am force-stop ${packageName.shellSafe()}")

    override suspend fun setEnabled(packageName: String, enabled: Boolean): Result<Unit> {
        val pkg = packageName.shellSafe()
        val cmd = if (enabled) "pm enable $pkg" else "pm disable-user --user 0 $pkg"
        return runShell(cmd)
    }

    override suspend fun revokeBackground(packageName: String): Result<Unit> =
        runShell("cmd appops set ${packageName.shellSafe()} RUN_ANY_IN_BACKGROUND ignore")

    override suspend fun processMemory(packageName: String): Result<Long> {
        val result = ShizukuShell.run("dumpsys meminfo ${packageName.shellSafe()}")
        if (!result.ok) return Result.failure(Exception(result.output.take(200)))
        val match = TOTAL_PSS.find(result.output)
            ?: return Result.failure(Exception("No TOTAL PSS line for $packageName"))
        val kb = match.groupValues[1].replace(",", "").toLongOrNull()
            ?: return Result.failure(Exception("Unparsable PSS value"))
        return Result.success(kb)
    }

    private suspend fun runShell(command: String): Result<Unit> {
        if (!isAvailable()) return Result.failure(UnsupportedByController(name, "run privileged commands"))
        val result = ShizukuShell.run(command)
        return if (result.ok) Result.success(Unit)
        else Result.failure(Exception(result.output.trim().ifEmpty { "exit code ${result.exitCode}" }))
    }

    companion object {
        /** `    311,704K: com.example.app (pid 12345)` */
        private val MEMINFO_ROW =
            Regex("""^\s*([\d,]+)K:\s+(\S+)\s+\(pid\s+(\d+)""", RegexOption.MULTILINE)

        private val TOTAL_PSS = Regex("""TOTAL\s+PSS:\s*([\d,]+)""")

        private val PS_ROW = Regex("""^\s*(\d+)\s+(\S+)\s*$""", RegexOption.MULTILINE)

        fun parseMeminfo(output: String): List<ProcessInfo> {
            val start = output.indexOf("Total PSS by process:")
            if (start < 0) return emptyList()
            val rest = output.substring(start + "Total PSS by process:".length)
            // The section ends at the next "Total PSS by ..." heading.
            val end = rest.indexOf("Total PSS by")
            val section = if (end > 0) rest.substring(0, end) else rest

            return MEMINFO_ROW.findAll(section).mapNotNull { m ->
                val kb = m.groupValues[1].replace(",", "").toLongOrNull() ?: return@mapNotNull null
                val pid = m.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                ProcessInfo(pid = pid, name = m.groupValues[2], pssKb = kb)
            }.toList()
        }

        fun parsePs(output: String): List<ProcessInfo> =
            PS_ROW.findAll(output).mapNotNull { m ->
                val pid = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val name = m.groupValues[2]
                if (name == "NAME" || name.startsWith("[")) return@mapNotNull null
                ProcessInfo(pid = pid, name = name, pssKb = 0L)
            }.toList()

    }
}

/** Package names are already constrained, but never hand raw input to `sh -c`. */
private fun String.shellSafe(): String = filter { it.isLetterOrDigit() || it in "._-:" }
