package com.procwatch.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.procwatch.core.Format
import com.procwatch.privileged.ShizukuManager
import com.procwatch.privileged.ShizukuStatus
import com.procwatch.ui.MainViewModel
import com.procwatch.ui.components.PanelCard
import com.procwatch.ui.components.ReadoutRow
import com.procwatch.ui.components.StatusChip
import com.procwatch.ui.theme.DataStyle
import com.procwatch.ui.theme.EyebrowStyle
import com.procwatch.ui.theme.MetaStyle
import com.procwatch.ui.theme.Panel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val hasUsage by viewModel.hasUsageAccess.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val whitelist by viewModel.whitelist.collectAsStateWithLifecycle()
    val rows by viewModel.allRows.collectAsStateWithLifecycle()
    val log by viewModel.logEntries.collectAsStateWithLifecycle()
    val controller = viewModel.capabilities.collectAsStateWithLifecycle().value

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PanelCard(
                title = "Usage access",
                trailing = {
                    StatusChip(
                        if (hasUsage) "GRANTED" else "REQUIRED",
                        if (hasUsage) Panel.Good else Panel.Danger
                    )
                }
            ) {
                Column {
                    Text(
                        "Unlocks screen time, last-used timestamps, standby buckets and per-app " +
                            "storage. Android only grants this from its own Settings screen, not " +
                            "from a permission dialog.",
                        style = DataStyle,
                        color = Panel.TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (hasUsage) "Review in Settings" else "Open Settings", style = DataStyle)
                    }
                }
            }
        }

        item {
            PanelCard(
                title = "Shizuku",
                trailing = {
                    StatusChip(
                        shizuku.description.uppercase(),
                        if (shizuku == ShizukuStatus.READY) Panel.Good else Panel.TextFaint
                    )
                }
            ) {
                Column {
                    Text(
                        when (shizuku) {
                            ShizukuStatus.READY ->
                                "Connected at shell level. Live process list, real force stop, " +
                                    "freeze and background blocking are all available."
                            ShizukuStatus.NEEDS_PERMISSION ->
                                "Shizuku is running. Authorise ProcWatch to use it."
                            ShizukuStatus.NOT_RUNNING ->
                                "Shizuku is installed but its service is stopped — this is normal " +
                                    "after a reboot. Open Shizuku and start it via Wireless debugging."
                            ShizukuStatus.NOT_INSTALLED ->
                                "Not installed. Without it, ProcWatch can only show installed apps " +
                                    "and usage history: Android hides other apps' processes from " +
                                    "normal apps, and there is no API that gets around that."
                            ShizukuStatus.OUTDATED ->
                                "This Shizuku build is too old for the current permission flow. " +
                                    "Update it and reconnect."
                        },
                        style = DataStyle,
                        color = Panel.TextSecondary
                    )

                    Spacer(Modifier.height(12.dp))

                    when (shizuku) {
                        ShizukuStatus.NEEDS_PERMISSION -> Button(
                            onClick = viewModel::requestShizukuPermission,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Panel.Signal,
                                contentColor = Panel.Background
                            )
                        ) { Text("Authorise ProcWatch", style = DataStyle) }

                        ShizukuStatus.NOT_RUNNING, ShizukuStatus.OUTDATED -> OutlinedButton(
                            onClick = {
                                val intent = context.packageManager
                                    .getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)
                                runCatching {
                                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open Shizuku", style = DataStyle) }

                        ShizukuStatus.NOT_INSTALLED -> Text(
                            "Install Shizuku, then come back here.",
                            style = MetaStyle,
                            color = Panel.TextFaint
                        )

                        ShizukuStatus.READY -> ReadoutRow(
                            "Capabilities",
                            "${controller.size} available",
                            valueColor = Panel.Good
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("ON XIAOMI / HYPEROS", style = EyebrowStyle, color = Panel.TextFaint)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Turn on \"USB debugging (Security settings)\" as well as ordinary USB " +
                            "debugging, and if pairing fails, turn off \"Secure app spawning\" in " +
                            "Settings > Security. Shizuku stops on every reboot unless the phone " +
                            "is rooted.",
                        style = DataStyle,
                        color = Panel.TextSecondary
                    )
                }
            }
        }

        item {
            PanelCard(
                title = "Keep-running list",
                trailing = { StatusChip("${whitelist.size} PROTECTED", Panel.Good) }
            ) {
                Column {
                    Text(
                        "These are never force stopped, including during a sweep. Your dialer, " +
                            "messaging app, keyboard, launcher and clock were added automatically " +
                            "on first launch.",
                        style = DataStyle,
                        color = Panel.TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))

                    val protectedRows = rows.filter { it.packageName in whitelist }
                    if (protectedRows.isEmpty()) {
                        Text("Nothing protected yet.", style = DataStyle, color = Panel.TextFaint)
                    } else {
                        protectedRows.forEach { row ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleWhitelist(row.packageName) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        row.meta.label,
                                        style = DataStyle,
                                        color = Panel.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        row.packageName,
                                        style = MetaStyle,
                                        color = Panel.TextFaint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text("REMOVE", style = EyebrowStyle, color = Panel.Danger)
                            }
                        }
                    }
                }
            }
        }

        item {
            PanelCard(
                title = "Activity log",
                trailing = {
                    if (log.isNotEmpty()) {
                        Text(
                            "CLEAR",
                            style = EyebrowStyle,
                            color = Panel.Danger,
                            modifier = Modifier.clickable { viewModel.clearLog() }
                        )
                    }
                }
            ) {
                Column {
                    if (log.isEmpty()) {
                        Text(
                            "Every force stop, freeze and block is recorded here with the exact " +
                                "shell error when one fails.",
                            style = DataStyle,
                            color = Panel.TextFaint
                        )
                    } else {
                        log.take(25).forEach { entry ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Row {
                                    Text(
                                        Format.timeOnly(entry.timestamp),
                                        style = MetaStyle,
                                        color = Panel.TextFaint
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        entry.action,
                                        style = MetaStyle,
                                        color = if (entry.success) Panel.Good else Panel.Danger
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        entry.method,
                                        style = MetaStyle,
                                        color = Panel.TextFaint
                                    )
                                }
                                Text(
                                    entry.packageName,
                                    style = MetaStyle,
                                    color = Panel.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                entry.detail?.let {
                                    Text(
                                        it,
                                        style = MetaStyle,
                                        color = Panel.Danger,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            PanelCard(title = "About") {
                Column {
                    ReadoutRow("Version", "0.1.0")
                    ReadoutRow("Network access", "None")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "ProcWatch declares no INTERNET permission. Nothing it reads leaves the " +
                            "phone. Not yet built: automatic sweeps on screen off, usage history " +
                            "charts, and scheduling rules.",
                        style = DataStyle,
                        color = Panel.TextFaint
                    )
                }
            }
        }
    }
}
