package com.procwatch.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.procwatch.core.SystemStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Device-level readings. None of this needs a special permission. */
class SystemStatsSource(private val context: Context) {

    suspend fun read(): SystemStats = withContext(Dispatchers.IO) {
        val memory = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        }.getOrNull()

        val battery: Intent? = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val tempTenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1

        val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()

        SystemStats(
            totalRamKb = (memory?.totalMem ?: 0) / 1024,
            availRamKb = (memory?.availMem ?: 0) / 1024,
            lowMemory = memory?.lowMemory ?: false,
            batteryLevel = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            batteryTempC = if (tempTenths > 0) tempTenths / 10f else -1f,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            storageTotalBytes = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0,
            storageFreeBytes = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0
        )
    }
}
