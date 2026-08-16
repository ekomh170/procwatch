package com.procwatch

import android.app.Application
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

class ProcWatchApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Android 9 added the hidden-API denylist. UsageStatsManager#getAppStandbyBucket(String)
        // sits behind it even though we hold the app-op the framework actually checks, so we
        // lift the restriction for this process. Failing here is survivable: the bucket column
        // simply reads "—".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        }

        container = AppContainer(this)
        container.settings.seedDefaultsIfNeeded()
    }
}
