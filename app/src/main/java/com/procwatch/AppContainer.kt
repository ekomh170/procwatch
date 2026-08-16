package com.procwatch

import android.content.Context
import com.procwatch.data.ActionLog
import com.procwatch.data.AppRepository
import com.procwatch.data.PackageSource
import com.procwatch.data.SettingsStore
import com.procwatch.data.SystemStatsSource
import com.procwatch.data.UsageSource
import com.procwatch.privileged.CapabilityRouter
import com.procwatch.privileged.FallbackAppController
import com.procwatch.privileged.ShizukuAppController
import com.procwatch.privileged.ShizukuManager

/**
 * Hand-rolled dependency container.
 *
 * A DI framework would add an annotation processor and a build-time cost for what is one
 * object graph with no variants. If the app ever needs scoping beyond "application", that is
 * the moment to bring in Hilt — not before.
 *
 * Controller order is priority order: strongest first.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val shizuku = ShizukuManager(appContext)
    val settings = SettingsStore(appContext)
    val actionLog = ActionLog(appContext)

    private val router = CapabilityRouter(
        listOf(
            ShizukuAppController(shizuku),
            FallbackAppController(appContext)
        )
    )

    val repository = AppRepository(
        packages = PackageSource(appContext),
        usage = UsageSource(appContext),
        systemStats = SystemStatsSource(appContext),
        router = router,
        settings = settings,
        actionLog = actionLog
    )
}
