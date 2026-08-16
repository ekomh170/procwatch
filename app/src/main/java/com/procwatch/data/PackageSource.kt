package com.procwatch.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.procwatch.core.AppMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the installed-package table. Static-ish data, so it is fetched once and reused
 * until something is installed, removed or updated.
 */
class PackageSource(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    @Suppress("DEPRECATION")
    suspend fun loadAll(): List<AppMeta> = withContext(Dispatchers.IO) {
        val packages = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        packages.mapNotNull { info ->
            val app: ApplicationInfo = info.applicationInfo ?: return@mapNotNull null
            AppMeta(
                packageName = info.packageName,
                label = runCatching { pm.getApplicationLabel(app).toString() }
                    .getOrDefault(info.packageName),
                uid = app.uid,
                isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isEnabled = app.enabled,
                versionName = info.versionName,
                firstInstallTime = info.firstInstallTime,
                lastUpdateTime = info.lastUpdateTime
            )
        }.sortedBy { it.label.lowercase() }
    }
}
