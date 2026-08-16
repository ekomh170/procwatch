package com.procwatch.core

/** Standby buckets as reported by UsageStatsManager. */
object Bucket {
    const val ACTIVE = 10
    const val WORKING_SET = 20
    const val FREQUENT = 30
    const val RARE = 40
    const val RESTRICTED = 45

    fun label(value: Int?): String = when (value) {
        ACTIVE -> "ACTIVE"
        WORKING_SET -> "WORKING"
        FREQUENT -> "FREQUENT"
        RARE -> "RARE"
        RESTRICTED -> "RESTRICTED"
        else -> "—"
    }
}

/** Everything static we know about an installed package. Cached aggressively. */
data class AppMeta(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val versionName: String?,
    val firstInstallTime: Long,
    val lastUpdateTime: Long
)

/** A single OS process, as read through Shizuku. */
data class ProcessInfo(
    val pid: Int,
    val name: String,
    val pssKb: Long
) {
    /** "com.foo:remote" belongs to package "com.foo". */
    val owningPackage: String get() = name.substringBefore(':')
}

/** One row in the app list: static meta joined with whatever dynamic data we could get. */
data class AppRow(
    val meta: AppMeta,
    val lastUsed: Long? = null,
    val foregroundMsToday: Long? = null,
    val standbyBucket: Int? = null,
    val pssKb: Long? = null,
    val processCount: Int = 0,
    val isWhitelisted: Boolean = false
) {
    val packageName: String get() = meta.packageName
    val isRunning: Boolean get() = processCount > 0
}

/** Device-level readings for the dashboard. */
data class SystemStats(
    val totalRamKb: Long = 0,
    val availRamKb: Long = 0,
    val lowMemory: Boolean = false,
    val batteryLevel: Int = -1,
    val batteryTempC: Float = -1f,
    val isCharging: Boolean = false,
    val storageTotalBytes: Long = 0,
    val storageFreeBytes: Long = 0
) {
    val usedRamKb: Long get() = (totalRamKb - availRamKb).coerceAtLeast(0)
    val ramFraction: Float
        get() = if (totalRamKb <= 0) 0f else (usedRamKb.toFloat() / totalRamKb.toFloat()).coerceIn(0f, 1f)
}

/** Storage breakdown for one package. Only available with Usage Access. */
data class StorageBreakdown(val appBytes: Long, val dataBytes: Long, val cacheBytes: Long)

/** Every privileged action we take gets written down. */
data class ActionRecord(
    val timestamp: Long,
    val packageName: String,
    val action: String,
    val method: String,
    val success: Boolean,
    val detail: String? = null
)

enum class SortKey(val display: String) {
    MEMORY("Memory"),
    LAST_USED("Last used"),
    SCREEN_TIME("Screen time"),
    NAME("Name")
}

enum class AppFilter(val display: String) {
    RUNNING("Running"),
    USER("User apps"),
    SYSTEM("System"),
    ALL("All")
}
