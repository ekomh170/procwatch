package com.procwatch.privileged

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    /** The Shizuku app is not on the device. */
    NOT_INSTALLED,

    /** Installed, but the service is not running — usually means the phone was rebooted. */
    NOT_RUNNING,

    /** Running, but this app has not been authorised yet. */
    NEEDS_PERMISSION,

    /** Shizuku is too old to use the modern permission flow. */
    OUTDATED,

    /** Everything is wired up. */
    READY;

    val description: String
        get() = when (this) {
            NOT_INSTALLED -> "Not installed"
            NOT_RUNNING -> "Installed, service stopped"
            NEEDS_PERMISSION -> "Waiting for authorisation"
            OUTDATED -> "Version too old"
            READY -> "Connected"
        }
}

/**
 * Tracks the live state of the Shizuku binder.
 *
 * Shizuku dies on every reboot unless the device is rooted, so this has to be a
 * flow the whole UI observes — not a one-shot check at startup.
 */
class ShizukuManager(private val context: Context) {

    private val _status = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    val isReady: Boolean get() = _status.value == ShizukuStatus.READY

    private val onBinderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val onBinderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val onPermissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun attach() {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
            Shizuku.addBinderDeadListener(onBinderDead)
            Shizuku.addRequestPermissionResultListener(onPermissionResult)
        }
        refresh()
    }

    fun detach() {
        runCatching {
            Shizuku.removeBinderReceivedListener(onBinderReceived)
            Shizuku.removeBinderDeadListener(onBinderDead)
            Shizuku.removeRequestPermissionResultListener(onPermissionResult)
        }
    }

    fun refresh() {
        _status.value = compute()
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
    }

    private fun compute(): ShizukuStatus {
        val installed = runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)

        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) return if (installed) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED

        if (runCatching { Shizuku.isPreV11() }.getOrDefault(false)) return ShizukuStatus.OUTDATED

        val granted = runCatching { Shizuku.checkSelfPermission() }
            .getOrDefault(PackageManager.PERMISSION_DENIED) == PackageManager.PERMISSION_GRANTED

        return if (granted) ShizukuStatus.READY else ShizukuStatus.NEEDS_PERMISSION
    }

    companion object {
        /** Forks such as thedjchi/Shizuku keep this same application id, so they work unchanged. */
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val PERMISSION_REQUEST_CODE = 8721
    }
}
