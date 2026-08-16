package com.procwatch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.procwatch.AppContainer
import com.procwatch.core.ActionRecord
import com.procwatch.core.AppFilter
import com.procwatch.core.AppRow
import com.procwatch.core.ProcessInfo
import com.procwatch.core.SortKey
import com.procwatch.core.StorageBreakdown
import com.procwatch.core.SystemStats
import com.procwatch.privileged.Capability
import com.procwatch.privileged.ShizukuStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One view model backs all three tabs. They read the same repository and the same privilege
 * state, so splitting them would mean three copies of the same wiring. If the app grows past
 * this, the seam to cut along is the tab boundary.
 */
class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val settings = container.settings

    val stats: StateFlow<SystemStats> = repo.stats
    val isRefreshing: StateFlow<Boolean> = repo.isRefreshing
    val processError: StateFlow<String?> = repo.processError
    val shizukuStatus: StateFlow<ShizukuStatus> = container.shizuku.status
    val whitelist: StateFlow<Set<String>> = settings.whitelist
    val logEntries: StateFlow<List<ActionRecord>> = container.actionLog.entries
    val sortKey: StateFlow<SortKey> = settings.sortKey
    val filter: StateFlow<AppFilter> = settings.filter

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _hasUsageAccess = MutableStateFlow(false)
    val hasUsageAccess: StateFlow<Boolean> = _hasUsageAccess.asStateFlow()

    private val _capabilities = MutableStateFlow<Set<Capability>>(emptySet())
    val capabilities: StateFlow<Set<Capability>> = _capabilities.asStateFlow()

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _detail = MutableStateFlow<AppDetail?>(null)
    val detail: StateFlow<AppDetail?> = _detail.asStateFlow()

    private val _busyMessage = MutableStateFlow<String?>(null)
    val busyMessage: StateFlow<String?> = _busyMessage.asStateFlow()

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val allRows: StateFlow<List<AppRow>> = repo.rows

    /** Search, filter and sort are applied here so the list screen stays presentational. */
    val visibleRows: StateFlow<List<AppRow>> =
        combine(repo.rows, settings.sortKey, settings.filter, _query) { rows, sort, filter, query ->
            rows
                .filter { row ->
                    when (filter) {
                        AppFilter.RUNNING -> row.isRunning
                        AppFilter.USER -> !row.meta.isSystem
                        AppFilter.SYSTEM -> row.meta.isSystem
                        AppFilter.ALL -> true
                    }
                }
                .filter { row ->
                    query.isBlank() ||
                        row.meta.label.contains(query, ignoreCase = true) ||
                        row.packageName.contains(query, ignoreCase = true)
                }
                .sortedWith(comparatorFor(sort))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun comparatorFor(sort: SortKey): Comparator<AppRow> = when (sort) {
        SortKey.MEMORY -> compareByDescending<AppRow> { it.pssKb ?: 0 }
            .thenByDescending { it.processCount }
            .thenBy { it.meta.label.lowercase() }
        SortKey.LAST_USED -> compareByDescending<AppRow> { it.lastUsed ?: 0 }
            .thenBy { it.meta.label.lowercase() }
        SortKey.SCREEN_TIME -> compareByDescending<AppRow> { it.foregroundMsToday ?: 0 }
            .thenBy { it.meta.label.lowercase() }
        SortKey.NAME -> compareBy { it.meta.label.lowercase() }
    }

    fun refresh(reloadPackages: Boolean = false) {
        viewModelScope.launch {
            container.shizuku.refresh()
            _hasUsageAccess.value = repo.hasUsageAccess()
            repo.refresh(reloadPackages)
            _capabilities.value = repo.capabilities()
        }
    }

    fun setQuery(value: String) { _query.value = value }
    fun setSort(value: SortKey) = settings.setSort(value)
    fun setFilter(value: AppFilter) = settings.setFilter(value)

    fun toggleWhitelist(packageName: String) {
        settings.toggleWhitelist(packageName)
        repo.reapplyWhitelist()
        val open = _detail.value
        if (open != null && open.row.packageName == packageName) {
            _detail.value = open.copy(
                row = open.row.copy(isWhitelisted = settings.isWhitelisted(packageName))
            )
        }
    }

    fun requestShizukuPermission() = container.shizuku.requestPermission()

    fun select(packageName: String?) {
        _selected.value = packageName
        _detail.value = null
        if (packageName == null) return
        val row = repo.rows.value.firstOrNull { it.packageName == packageName }
        if (row == null) {
            _selected.value = null
            return
        }
        // Processes come straight from the last refresh, so the sheet opens populated.
        // Only storage and the fresh memory reading have to be waited on.
        val processes = repo.processesFor(packageName)
        _detail.value = AppDetail(row = row, processes = processes)
        viewModelScope.launch {
            val storage = repo.storageFor(packageName)
            val livePss = repo.memoryFor(packageName)
            _detail.value = AppDetail(
                row = if (livePss != null) row.copy(pssKb = livePss) else row,
                processes = processes,
                storage = storage,
                loaded = true
            )
        }
    }

    fun forceStop(packageName: String) {
        viewModelScope.launch {
            val result = repo.forceStop(packageName)
            messages.tryEmit(
                if (result.isSuccess) "Stopped $packageName"
                else "Could not stop $packageName — ${result.exceptionOrNull()?.message}"
            )
            repo.refresh()
            if (_selected.value == packageName) select(packageName)
        }
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            val result = repo.setEnabled(packageName, enabled)
            messages.tryEmit(
                when {
                    result.isSuccess && enabled -> "Unfroze $packageName"
                    result.isSuccess -> "Froze $packageName"
                    else -> "Failed — ${result.exceptionOrNull()?.message}"
                }
            )
            repo.refresh(reloadPackages = true)
        }
    }

    fun revokeBackground(packageName: String) {
        viewModelScope.launch {
            val result = repo.revokeBackground(packageName)
            messages.tryEmit(
                if (result.isSuccess) "Background execution revoked for $packageName"
                else "Failed — ${result.exceptionOrNull()?.message}"
            )
        }
    }

    fun hibernateAll() {
        viewModelScope.launch {
            _busyMessage.value = "Preparing sweep"
            val outcome = repo.hibernateAll { done, total ->
                _busyMessage.value = "Stopping $done of $total"
            }
            _busyMessage.value = null
            messages.tryEmit(
                when {
                    outcome.stopped == 0 && outcome.failed == 0 -> "Nothing to stop"
                    outcome.failed == 0 -> "Stopped ${outcome.stopped} apps"
                    else -> "Stopped ${outcome.stopped}, ${outcome.failed} failed"
                }
            )
        }
    }

    fun clearLog() = container.actionLog.clear()

    data class AppDetail(
        val row: AppRow,
        val processes: List<ProcessInfo> = emptyList(),
        val storage: StorageBreakdown? = null,
        val loaded: Boolean = false
    )

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
                "Unknown ViewModel ${modelClass.name}"
            }
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(container) as T
        }
    }
}
