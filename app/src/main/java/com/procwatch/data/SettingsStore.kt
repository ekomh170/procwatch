package com.procwatch.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.procwatch.core.AppFilter
import com.procwatch.core.SortKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whitelist plus a handful of preferences. SharedPreferences is deliberate: this is a few
 * hundred bytes of state, and a database would be ceremony without benefit.
 *
 * The whitelist is the safety rail for the whole app. Force-stopping a package puts it into
 * the stopped state — no alarms, no notifications until the user opens it by hand — so the
 * apps you would actually miss get protected before the user can shoot themselves in the foot.
 */
class SettingsStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("procwatch", Context.MODE_PRIVATE)

    private val _whitelist = MutableStateFlow(readWhitelist())
    val whitelist: StateFlow<Set<String>> = _whitelist.asStateFlow()

    private val _sortKey = MutableStateFlow(readSort())
    val sortKey: StateFlow<SortKey> = _sortKey.asStateFlow()

    private val _filter = MutableStateFlow(readFilter())
    val filter: StateFlow<AppFilter> = _filter.asStateFlow()

    fun isWhitelisted(packageName: String): Boolean = packageName in _whitelist.value

    fun toggleWhitelist(packageName: String) {
        val next = _whitelist.value.toMutableSet()
        if (!next.remove(packageName)) next.add(packageName)
        prefs.edit().putStringSet(KEY_WHITELIST, next).apply()
        _whitelist.value = next
    }

    fun setSort(value: SortKey) {
        prefs.edit().putString(KEY_SORT, value.name).apply()
        _sortKey.value = value
    }

    fun setFilter(value: AppFilter) {
        prefs.edit().putString(KEY_FILTER, value.name).apply()
        _filter.value = value
    }

    /**
     * Runs once, on first launch. Detects the packages this device actually depends on
     * rather than shipping a hardcoded list that would be wrong on every other phone.
     */
    fun seedDefaultsIfNeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        val seeds = mutableSetOf(context.packageName)

        runCatching {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecom?.defaultDialerPackage?.let { seeds += it }
        }
        runCatching { Telephony.Sms.getDefaultSmsPackage(context)?.let { seeds += it } }
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
                ?.let { seeds += it }
        }
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
                ?.takeIf { it.isNotBlank() }
                ?.let { seeds += it }
        }
        // Clock/alarm packages are not exposed by any API, so fall back to naming.
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledPackages(0)
                .map { it.packageName }
                .filter { it.contains("deskclock") || it.endsWith(".clock") || it.contains("alarm") }
                .forEach { seeds += it }
        }

        val merged = _whitelist.value + seeds.filter { it.isNotBlank() }
        prefs.edit()
            .putStringSet(KEY_WHITELIST, merged)
            .putBoolean(KEY_SEEDED, true)
            .apply()
        _whitelist.value = merged
    }

    private fun readWhitelist(): Set<String> =
        prefs.getStringSet(KEY_WHITELIST, emptySet())?.toSet() ?: emptySet()

    private fun readSort(): SortKey =
        runCatching { SortKey.valueOf(prefs.getString(KEY_SORT, null) ?: "") }
            .getOrDefault(SortKey.MEMORY)

    private fun readFilter(): AppFilter =
        runCatching { AppFilter.valueOf(prefs.getString(KEY_FILTER, null) ?: "") }
            .getOrDefault(AppFilter.RUNNING)

    private companion object {
        const val KEY_WHITELIST = "whitelist"
        const val KEY_SORT = "sort_key"
        const val KEY_FILTER = "filter"
        const val KEY_SEEDED = "seeded_v1"
    }
}
