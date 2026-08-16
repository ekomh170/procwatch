package com.procwatch.privileged

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class ShellResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * Runs shell commands inside the Shizuku process, which holds the shell UID (2000) —
 * the same privileges you get from `adb shell`.
 *
 * Shizuku#newProcess is marked @RestrictTo in the client library, so we reach it by
 * reflection. That is the standard approach; the alternative is binding a UserService,
 * which is more robust but needs its own AIDL. If a future Shizuku release renames the
 * method, only this file has to change.
 *
 * stderr is folded into stdout with `2>&1` so a chatty command can never deadlock us by
 * filling a pipe we are not reading.
 */
object ShizukuShell {

    private val newProcess by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        }.getOrNull()
    }

    val isUsable: Boolean get() = newProcess != null

    suspend fun run(command: String): ShellResult = withContext(Dispatchers.IO) {
        val method = newProcess
            ?: return@withContext ShellResult(-1, "Shizuku shell entry point not found")
        try {
            val args = arrayOf<Any?>(arrayOf("sh", "-c", "$command 2>&1"), null, null)
            val process = method.invoke(null, *args) as Process
            val text = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            ShellResult(code, text)
        } catch (t: Throwable) {
            ShellResult(-1, t.message ?: t.javaClass.simpleName)
        }
    }
}
