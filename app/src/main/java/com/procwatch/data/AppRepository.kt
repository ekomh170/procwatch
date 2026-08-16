package com.procwatch.data

import com.procwatch.core.ActionRecord
import com.procwatch.core.AppMeta
import com.procwatch.core.AppRow
import com.procwatch.core.ProcessInfo
import com.procwatch.core.StorageBreakdown
import com.procwatch.core.SystemStats
import com.procwatch.privileged.Capability
import com.procwatch.privileged.CapabilityRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Joins the static package table with whatever dynamic data the current privilege level
 * allows, and owns every mutation of app state.
 *
 * Nothing here knows about Shizuku. It asks the router for a capability and takes what it gets.
 */
class AppRepository(
    private val packages: PackageSource,
    private val usage: UsageSource,
    private val systemStats: SystemStatsSource,
    private val router: CapabilityRouter,
    private val settings: SettingsStore,
    private val actionLog: ActionLog
) {

    private val _rows = MutableStateFlow<List<AppRow>>(emptyList())
    val rows: StateFlow<List<AppRow>> = _rows.asStateFlow()

    private val _stats = MutableStateFlow(SystemStats())
    val stats: StateFlow<SystemStats> = _stats.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _processError = MutableStateFlow<String?>(null)
    val processError: StateFlow<String?> = _processError.asStateFlow()

    private var metaCache: List<AppMeta>? = null

    fun hasUsageAccess(): Boolean = usage.hasUsageAccess()
    fun capabilities(): Set<Capability> = router.activeCapabilities()
    fun activeControllerName(): String = router.activeName()

    /** @param reloadPackages set true after an install/uninstall; the table is cached otherwise. */
    suspend fun refresh(reloadPackages: Boolean = false) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        try {
            _stats.value = systemStats.read()

            val metas = if (reloadPackages || metaCache == null) {
                packages.loadAll().also { metaCache = it }
            } else {
                metaCache!!
            }

            val hasUsage = usage.hasUsageAccess()
            val screenTime = if (hasUsage) usage.screenTimeToday() else emptyMap()
            val lastUsed = if (hasUsage) usage.lastUsed() else emptyMap()

            val processes: List<ProcessInfo> = if (router.has(Capability.LIST_PROCESSES)) {
                router.listProcesses()
                    .onFailure { _processError.value = it.message }
                    .onSuccess { _processError.value = null }
                    .getOrDefault(emptyList())
            } else {
                _processError.value = null
                emptyList()
            }

            val byPackage = processes.groupBy { it.owningPackage }
            val whitelist = settings.whitelist.value

            val buckets: Map<String, Int> = if (hasUsage) {
                withContext(Dispatchers.IO) {
                    metas.mapNotNull { meta ->
                        usage.standbyBucket(meta.packageName)?.let { meta.packageName to it }
                    }.toMap()
                }
            } else {
                emptyMap()
            }

            _rows.value = metas.map { meta ->
                val procs = byPackage[meta.packageName].orEmpty()
                val pss = procs.sumOf { it.pssKb }
                AppRow(
                    meta = meta,
                    lastUsed = lastUsed[meta.packageName],
                    foregroundMsToday = screenTime[meta.packageName],
                    standbyBucket = buckets[meta.packageName],
                    pssKb = if (pss > 0) pss else null,
                    processCount = procs.size,
                    isWhitelisted = meta.packageName in whitelist
                )
            }
        } finally {
            _isRefreshing.value = false
        }
    }

    /** Re-tags rows after a whitelist edit without paying for a full refresh. */
    fun reapplyWhitelist() {
        val whitelist = settings.whitelist.value
        _rows.value = _rows.value.map { it.copy(isWhitelisted = it.packageName in whitelist) }
    }

    suspend fun processesFor(packageName: String): List<ProcessInfo> {
        if (!router.has(Capability.LIST_PROCESSES)) return emptyList()
        return router.listProcesses().getOrDefault(emptyList())
            .filter { it.owningPackage == packageName }
    }

    suspend fun storageFor(packageName: String): StorageBreakdown? = usage.storageFor(packageName)

    suspend fun forceStop(packageName: String): Result<Unit> {
        if (settings.isWhitelisted(packageName)) {
            return Result.failure(IllegalStateException("$packageName is whitelisted"))
        }
        val (method, result) = router.forceStop(packageName)
        actionLog.record(
            ActionRecord(
                timestamp = System.currentTimeMillis(),
                packageName = packageName,
                action = "Force stop",
                method = method,
                success = result.isSuccess,
                detail = result.exceptionOrNull()?.message
            )
        )
        return result
    }

    suspend fun setEnabled(packageName: String, enabled: Boolean): Result<Unit> {
        val (method, result) = router.setEnabled(packageName, enabled)
        actionLog.record(
            ActionRecord(
                timestamp = System.currentTimeMillis(),
                packageName = packageName,
                action = if (enabled) "Unfreeze" else "Freeze",
                method = method,
                success = result.isSuccess,
                detail = result.exceptionOrNull()?.message
            )
        )
        if (result.isSuccess) metaCache = null
        return result
    }

    suspend fun revokeBackground(packageName: String): Result<Unit> {
        val (method, result) = router.revokeBackground(packageName)
        actionLog.record(
            ActionRecord(
                timestamp = System.currentTimeMillis(),
                packageName = packageName,
                action = "Revoke background",
                method = method,
                success = result.isSuccess,
                detail = result.exceptionOrNull()?.message
            )
        )
        return result
    }

    /**
     * Stops every running, non-whitelisted user app. This is the Greenify-style sweep, done
     * explicitly rather than on a timer — an automatic version needs a foreground service,
     * which is a separate piece of work.
     */
    suspend fun hibernateAll(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): HibernateOutcome {
        val targets = _rows.value.filter { it.isRunning && !it.isWhitelisted && !it.meta.isSystem }
        var stopped = 0
        var failed = 0
        targets.forEachIndexed { index, row ->
            val result = forceStop(row.packageName)
            if (result.isSuccess) stopped++ else failed++
            onProgress(index + 1, targets.size)
        }
        refresh()
        return HibernateOutcome(stopped, failed)
    }

    data class HibernateOutcome(val stopped: Int, val failed: Int)
}
