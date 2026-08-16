package com.procwatch.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.procwatch.core.AppFilter
import com.procwatch.core.AppRow
import com.procwatch.core.Bucket
import com.procwatch.core.Format
import com.procwatch.core.SortKey
import com.procwatch.privileged.Capability
import com.procwatch.ui.MainViewModel
import com.procwatch.ui.components.AppIcon
import com.procwatch.ui.components.StatusRail
import com.procwatch.ui.theme.DataStyle
import com.procwatch.ui.theme.EyebrowStyle
import com.procwatch.ui.theme.MetaStyle
import com.procwatch.ui.theme.Panel
import com.procwatch.ui.theme.bucketColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(viewModel: MainViewModel) {
    val rows by viewModel.visibleRows.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sortKey by viewModel.sortKey.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val processError by viewModel.processError.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    val canSeeProcesses = Capability.LIST_PROCESSES in capabilities

    Column(Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = DataStyle,
                placeholder = { Text("Search apps", style = DataStyle, color = Panel.TextFaint) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = Panel.TextFaint)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, "Clear search", tint = Panel.TextFaint)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions.Default,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Panel.TextPrimary,
                    unfocusedTextColor = Panel.TextPrimary,
                    focusedBorderColor = Panel.Signal,
                    unfocusedBorderColor = Panel.Outline,
                    cursorColor = Panel.Signal,
                    focusedContainerColor = Panel.Surface,
                    unfocusedContainerColor = Panel.Surface
                )
            )
            IconButton(onClick = { viewModel.refresh(reloadPackages = true) }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = Panel.TextSecondary)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppFilter.entries.forEach { option ->
                PanelChip(
                    label = option.display,
                    selected = filter == option,
                    onClick = { viewModel.setFilter(option) }
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "SORT",
                style = EyebrowStyle,
                color = Panel.TextFaint,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            Spacer(Modifier.width(4.dp))
            SortKey.entries.forEach { option ->
                PanelChip(
                    label = option.display,
                    selected = sortKey == option,
                    onClick = { viewModel.setSort(option) }
                )
            }
        }

        if (refreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Panel.Signal,
                trackColor = Panel.Background
            )
        } else {
            HorizontalDivider(color = Panel.OutlineSoft)
        }

        if (processError != null && canSeeProcesses) {
            Text(
                "Process read failed: $processError",
                style = DataStyle,
                color = Panel.Danger,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        if (rows.isEmpty()) {
            EmptyState(
                filter = filter,
                canSeeProcesses = canSeeProcesses,
                onShowAll = { viewModel.setFilter(AppFilter.ALL) }
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.packageName }) { row ->
                    AppListRow(
                        row = row,
                        showMemory = canSeeProcesses,
                        onClick = { viewModel.select(row.packageName) }
                    )
                    HorizontalDivider(color = Panel.OutlineSoft)
                }
            }
        }
    }

    if (selected != null) {
        AppDetailSheet(viewModel)
    }
}

@Composable
private fun AppListRow(row: AppRow, showMemory: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The rail encodes standby bucket: how much the system still lets this app run.
        StatusRail(
            color = if (row.isRunning) bucketColor(row.standbyBucket) else Panel.BucketUnknown,
            modifier = Modifier.fillMaxHeight()
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(row.packageName)
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.meta.label,
                        color = Panel.TextPrimary,
                        style = DataStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (row.isWhitelisted) {
                        Spacer(Modifier.width(6.dp))
                        Text("KEEP", style = EyebrowStyle, color = Panel.Good)
                    }
                    if (!row.meta.isEnabled) {
                        Spacer(Modifier.width(6.dp))
                        Text("FROZEN", style = EyebrowStyle, color = Panel.Danger)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append(Bucket.label(row.standbyBucket))
                        append("  ·  ")
                        append(Format.relativeTime(row.lastUsed))
                        if (row.foregroundMsToday != null && row.foregroundMsToday > 0) {
                            append("  ·  ")
                            append(Format.duration(row.foregroundMsToday))
                            append(" today")
                        }
                    },
                    style = MetaStyle,
                    color = Panel.TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (showMemory) Format.kb(row.pssKb) else "—",
                    style = DataStyle,
                    color = if (row.isRunning) Panel.Signal else Panel.TextFaint
                )
                Text(
                    if (row.processCount > 0) "${row.processCount} proc" else "idle",
                    style = MetaStyle,
                    color = Panel.TextFaint
                )
            }
        }
    }
}

@Composable
private fun PanelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label.uppercase(), style = EyebrowStyle) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Panel.Surface,
            labelColor = Panel.TextSecondary,
            selectedContainerColor = Panel.Signal,
            selectedLabelColor = Panel.Background
        )
    )
}

@Composable
private fun EmptyState(filter: AppFilter, canSeeProcesses: Boolean, onShowAll: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                when {
                    filter == AppFilter.RUNNING && !canSeeProcesses ->
                        "Android will not tell a normal app which processes are running. Connect Shizuku in Setup, or switch to All to browse installed apps."
                    filter == AppFilter.RUNNING ->
                        "Nothing is running that matches. That is a good sign."
                    else -> "No apps match this search."
                },
                style = DataStyle,
                color = Panel.TextSecondary
            )
            if (filter != AppFilter.ALL) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Show all apps",
                    style = DataStyle,
                    color = Panel.Signal,
                    modifier = Modifier
                        .background(Panel.Surface)
                        .clickable(onClick = onShowAll)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
