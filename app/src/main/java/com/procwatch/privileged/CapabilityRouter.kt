package com.procwatch.privileged

import com.procwatch.core.ProcessInfo

/**
 * Picks the strongest controller that is actually alive right now, per capability.
 *
 * Ordering is priority order: Shizuku first, then whatever still works without it.
 * Nothing else in the app needs to know which one answered.
 */
class CapabilityRouter(private val controllers: List<AppController>) {

    fun controllerFor(capability: Capability): AppController? =
        controllers.firstOrNull { it.isAvailable() && capability in it.capabilities() }

    fun has(capability: Capability): Boolean = controllerFor(capability) != null

    fun activeName(): String = controllers.firstOrNull { it.isAvailable() }?.name ?: "None"

    /** All capabilities available at this instant — used to drive the UI's tier badge. */
    fun activeCapabilities(): Set<Capability> =
        controllers.filter { it.isAvailable() }.flatMap { it.capabilities() }.toSet()

    suspend fun listProcesses(): Result<List<ProcessInfo>> =
        controllerFor(Capability.LIST_PROCESSES)?.listProcesses()
            ?: Result.failure(UnsupportedByController("No controller", "list processes"))

    /** Returns the controller name alongside the outcome so the action log stays honest. */
    suspend fun forceStop(packageName: String): Pair<String, Result<Unit>> {
        val controller = controllerFor(Capability.FORCE_STOP)
            ?: return "None" to Result.failure(UnsupportedByController("No controller", "force stop"))
        return controller.name to controller.forceStop(packageName)
    }

    suspend fun setEnabled(packageName: String, enabled: Boolean): Pair<String, Result<Unit>> {
        val controller = controllerFor(Capability.FREEZE_APP)
            ?: return "None" to Result.failure(UnsupportedByController("No controller", "freeze apps"))
        return controller.name to controller.setEnabled(packageName, enabled)
    }

    suspend fun revokeBackground(packageName: String): Pair<String, Result<Unit>> {
        val controller = controllerFor(Capability.REVOKE_BACKGROUND)
            ?: return "None" to Result.failure(UnsupportedByController("No controller", "revoke background"))
        return controller.name to controller.revokeBackground(packageName)
    }
}
