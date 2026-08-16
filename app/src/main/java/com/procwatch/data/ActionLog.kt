package com.procwatch.data

import android.content.Context
import com.procwatch.core.ActionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Every privileged action, kept on device, newest first, capped.
 *
 * When a force stop silently does nothing — and on aggressive OEM builds it sometimes will —
 * this is the only way to tell whether the command ran and what the shell said back.
 */
class ActionLog(context: Context) {

    private val prefs = context.getSharedPreferences("procwatch_log", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<ActionRecord>> = _entries.asStateFlow()

    fun record(record: ActionRecord) = recordAll(listOf(record))

    /**
     * One serialisation for a whole batch.
     *
     * A sweep stops dozens of apps in a row, and logging each one separately meant
     * re-encoding the entire log — up to 300 entries — once per app, on the caller's
     * thread. Callers pass records oldest-first; the log is newest-first.
     */
    fun recordAll(records: List<ActionRecord>) {
        if (records.isEmpty()) return
        val next = (records.asReversed() + _entries.value).take(MAX_ENTRIES)
        _entries.value = next
        persist(next)
    }

    fun clear() {
        _entries.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun persist(records: List<ActionRecord>) {
        val array = JSONArray()
        records.forEach { r ->
            array.put(
                JSONObject().apply {
                    put("t", r.timestamp)
                    put("p", r.packageName)
                    put("a", r.action)
                    put("m", r.method)
                    put("s", r.success)
                    put("d", r.detail ?: "")
                }
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun read(): List<ActionRecord> = runCatching {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            ActionRecord(
                timestamp = o.optLong("t"),
                packageName = o.optString("p"),
                action = o.optString("a"),
                method = o.optString("m"),
                success = o.optBoolean("s"),
                detail = o.optString("d").takeIf { it.isNotBlank() }
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 300
    }
}
