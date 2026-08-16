package com.procwatch.privileged

import com.procwatch.core.ProcessInfo

enum class Capability {
    LIST_PROCESSES,
    PER_APP_MEMORY,
    FORCE_STOP,
    FREEZE_APP,
    REVOKE_BACKGROUND
}

/**
 * The seam between "what the app wants to do" and "how much privilege we happen to have".
 *
 * Every feature talks to this interface, never to Shizuku directly. When Shizuku is not
 * running the app degrades to a weaker controller instead of breaking, and adding a root
 * backend later means writing one more implementation and nothing else.
 */
interface AppController {
    val name: String
    fun isAvailable(): Boolean
    fun capabilities(): Set<Capability>

    suspend fun listProcesses(): Result<List<ProcessInfo>>
    suspend fun forceStop(packageName: String): Result<Unit>
    suspend fun setEnabled(packageName: String, enabled: Boolean): Result<Unit>
    suspend fun revokeBackground(packageName: String): Result<Unit>
    suspend fun processMemory(packageName: String): Result<Long>
}

/** Shared "we can't do that at this privilege level" failure. */
class UnsupportedByController(controller: String, what: String) :
    Exception("$controller cannot $what")
