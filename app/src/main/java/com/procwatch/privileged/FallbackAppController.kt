package com.procwatch.privileged

import android.app.ActivityManager
import android.content.Context
import com.procwatch.core.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What we can still do with no privileged access at all.
 *
 * killBackgroundProcesses only reclaims *cached* processes. It does not put the app into
 * the stopped state, so alarms and jobs survive and the system will usually bring the app
 * straight back. The UI labels this "Soft kill" for exactly that reason — presenting it as
 * a force stop would be a lie.
 */
class FallbackAppController(private val context: Context) : AppController {

    override val name = "Soft kill"

    override fun isAvailable(): Boolean = true

    override fun capabilities(): Set<Capability> = setOf(Capability.FORCE_STOP)

    override suspend fun listProcesses(): Result<List<ProcessInfo>> =
        Result.failure(UnsupportedByController(name, "list processes"))

    override suspend fun forceStop(packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.killBackgroundProcesses(packageName)
            }
        }

    override suspend fun setEnabled(packageName: String, enabled: Boolean): Result<Unit> =
        Result.failure(UnsupportedByController(name, "freeze apps"))

    override suspend fun revokeBackground(packageName: String): Result<Unit> =
        Result.failure(UnsupportedByController(name, "revoke background execution"))

    override suspend fun processMemory(packageName: String): Result<Long> =
        Result.failure(UnsupportedByController(name, "read per-app memory"))
}
