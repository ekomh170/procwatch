package com.procwatch.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import com.procwatch.core.StorageBreakdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Everything unlocked by Usage Access — the one special permission that is worth asking for
 * even if the user never sets up Shizuku.
 */
class UsageSource(private val context: Context) {

    private val usageStats by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }
    private val appOps by lazy {
        context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    }

    fun hasUsageAccess(): Boolean = runCatching {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Foreground time since local midnight, keyed by package. */
    suspend fun screenTimeToday(): Map<String, Long> = withContext(Dispatchers.IO) {
        aggregate(startOfToday(), System.currentTimeMillis())
            .mapValues { it.value.totalTimeInForeground }
    }

    /** Last-used timestamps over a wide window so rarely opened apps still show up. */
    suspend fun lastUsed(days: Int = 60): Map<String, Long> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        aggregate(now - days * 24L * 60 * 60 * 1000, now)
            .mapValues { it.value.lastTimeUsed }
            .filterValues { it > 0 }
    }

    private fun aggregate(begin: Long, end: Long): Map<String, UsageStats> =
        runCatching { usageStats.queryAndAggregateUsageStats(begin, end) }
            .getOrDefault(emptyMap())

    /**
     * getAppStandbyBucket(String) is @SystemApi, not public SDK. The PACKAGE_USAGE_STATS
     * app-op is what the framework actually checks, and we hold it, so reflection through
     * HiddenApiBypass gets us there.
     *
     * The lookup is cached because this is called once per installed package on every
     * refresh — a few hundred times — and only the invoke has to be per-package.
     */
    private val standbyBucketMethod by lazy {
        runCatching {
            UsageStatsManager::class.java.getMethod("getAppStandbyBucket", String::class.java)
        }.getOrNull()
    }

    /** Null rather than throwing if a future Android closes this off. */
    fun standbyBucket(packageName: String): Int? {
        val method = standbyBucketMethod ?: return null
        return runCatching { method.invoke(usageStats, packageName) as? Int }.getOrNull()
    }

    suspend fun storageFor(packageName: String): StorageBreakdown? = withContext(Dispatchers.IO) {
        runCatching {
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val stats = ssm.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                packageName,
                Process.myUserHandle()
            )
            StorageBreakdown(stats.appBytes, stats.dataBytes, stats.cacheBytes)
        }.getOrNull()
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
