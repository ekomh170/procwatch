package com.procwatch.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.procwatch.core.Bucket
import com.procwatch.core.Format
import com.procwatch.privileged.Capability
import com.procwatch.privileged.ShizukuStatus
import com.procwatch.ui.MainViewModel
import com.procwatch.ui.components.PanelCard
import com.procwatch.ui.components.ReadoutRow
import com.procwatch.ui.components.SegmentMeter
import com.procwatch.ui.components.StatusChip
import com.procwatch.ui.theme.DataStyle
import com.procwatch.ui.theme.DataStyleLarge
import com.procwatch.ui.theme.EyebrowStyle
import com.procwatch.ui.theme.Panel
import com.procwatch.ui.theme.bucketColor

@Composable
fun DashboardScreen(viewModel: MainViewModel, onOpenApps: () -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val rows by viewModel.allRows.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val hasUsage by viewModel.hasUsageAccess.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val busy by viewModel.busyMessage.collectAsStateWithLifecycle()

    var confirmSweep by remember { mutableStateOf(false) }

    val running = rows.filter { it.isRunning }
    val canSeeProcesses = Capability.LIST_PROCESSES in capabilities
    val sweepTargets = running.filter { !it.isWhitelisted && !it.meta.isSystem }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("PROCWATCH", style = EyebrowStyle, color = Panel.TextFaint)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Background activity",
                    style = DataStyleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Panel.TextPrimary
                )
            }
        }

        item {
            PanelCard(
                title = "Memory",
                trailing = {
                    if (stats.lowMemory) StatusChip("LOW MEMORY", Panel.Danger)
                }
            ) {
                Column {
                    Text(
                        Format.kb(stats.usedRamKb),
                        style = DataStyleLarge,
                        color = Panel.Signal
                    )
                    Text(
                        "of ${Format.kb(stats.totalRamKb)} in use",
                        style = DataStyle,
                        color = Panel.TextFaint
                    )
                    Spacer(Modifier.height(12.dp))
                    SegmentMeter(
                        fraction = stats.ramFraction,
                        activeColor = if (stats.lowMemory) Panel.Danger else Panel.Signal
                    )
                    Spacer(Modifier.height(12.dp))
                    ReadoutRow("Available", Format.kb(stats.availRamKb))
                    ReadoutRow(
                        "Storage free",
                        "${Format.bytes(stats.storageFreeBytes)} / ${Format.bytes(stats.storageTotalBytes)}"
                    )
                    ReadoutRow(
                        "Battery",
                        buildString {
                            append(if (stats.batteryLevel >= 0) "${stats.batteryLevel}%" else "—")
                            if (stats.isCharging) append("  charging")
                            if (stats.batteryTempC > 0) append("  ${stats.batteryTempC}°C")
                        }
                    )
                }
            }
        }

        item {
            PanelCard(title = "Access level") {
                Column {
                    ReadoutRow(
                        "Shizuku",
                        shizuku.description,
                        valueColor = if (shizuku == ShizukuStatus.READY) Panel.Good else Panel.TextFaint
                    )
                    ReadoutRow(
                        "Usage access",
                        if (hasUsage) "Granted" else "Not granted",
                        valueColor = if (hasUsage) Panel.Good else Panel.TextFaint
                    )
                    ReadoutRow(
                        "Live process list",
                        if (canSeeProcesses) "Available" else "Unavailable",
                        valueColor = if (canSeeProcesses) Panel.Good else Panel.TextFaint
                    )

                    if (!canSeeProcesses || !hasUsage) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (!hasUsage)
                                "Without Usage Access this list is just installed apps. Turn it on in Setup to see what actually runs."
                            else
                                "Android hides other apps' processes from normal apps. Connect Shizuku in Setup to read the real list.",
                            style = DataStyle,
                            color = Panel.TextSecondary
                        )
                    }
                }
            }
        }

        item {
            PanelCard(
                title = "Right now",
                trailing = { StatusChip("${running.size} RUNNING", Panel.Signal) }
            ) {
                Column {
                    if (!canSeeProcesses) {
                        Text(
                            "Nothing to show yet. Process data needs Shizuku.",
                            style = DataStyle,
                            color = Panel.TextFaint
                        )
                    } else if (running.isEmpty()) {
                        Text(
                            "No third-party apps are holding a process.",
                            style = DataStyle,
                            color = Panel.TextFaint
                        )
                    } else {
                        running.sortedByDescending { it.pssKb ?: 0 }.take(5).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    row.meta.label,
                                    style = DataStyle,
                                    color = Panel.TextPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                StatusChip(
                                    Bucket.label(row.standbyBucket),
                                    bucketColor(row.standbyBucket)
                                )
                                Text(
                                    "  ${Format.kb(row.pssKb)}",
                                    style = DataStyle,
                                    color = Panel.Signal
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenApps, modifier = Modifier.fillMaxWidth()) {
                            Text("See all apps", style = DataStyle)
                        }
                    }
                }
            }
        }

        item {
            PanelCard(title = "Sweep") {
                Column {
                    Text(
                        "Force stops every running app that is not on your keep-running list. " +
                            "Stopped apps send no notifications until you open them again.",
                        style = DataStyle,
                        color = Panel.TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    ReadoutRow("Would stop", "${sweepTargets.size} apps")
                    ReadoutRow("Protected", "${rows.count { it.isWhitelisted }} apps")
                    Spacer(Modifier.height(12.dp))

                    if (busy != null) {
                        Text(busy!!, style = DataStyle, color = Panel.Signal)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Panel.Signal,
                            trackColor = Panel.OutlineSoft
                        )
                    } else {
                        Button(
                            onClick = { confirmSweep = true },
                            enabled = sweepTargets.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Panel.Signal,
                                contentColor = Panel.Background,
                                disabledContainerColor = Panel.SurfaceRaised,
                                disabledContentColor = Panel.TextFaint
                            )
                        ) {
                            Text("Stop ${sweepTargets.size} background apps", style = DataStyle)
                        }
                    }
                }
            }
        }
    }

    if (confirmSweep) {
        AlertDialog(
            onDismissRequest = { confirmSweep = false },
            containerColor = Panel.Surface,
            title = { Text("Stop ${sweepTargets.size} apps?", color = Panel.TextPrimary) },
            text = {
                Text(
                    "Each one goes into the stopped state: its alarms and background jobs are " +
                        "cancelled and it sends no notifications until you open it. " +
                        "Apps on your keep-running list are skipped.",
                    style = DataStyle,
                    color = Panel.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSweep = false
                    viewModel.hibernateAll()
                }) { Text("Stop them", color = Panel.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSweep = false }) {
                    Text("Cancel", color = Panel.TextSecondary)
                }
            }
        )
    }
}
