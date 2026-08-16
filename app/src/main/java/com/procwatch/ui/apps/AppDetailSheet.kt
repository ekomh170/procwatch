package com.procwatch.ui.apps

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.procwatch.core.Bucket
import com.procwatch.core.Format
import com.procwatch.privileged.Capability
import com.procwatch.ui.MainViewModel
import com.procwatch.ui.components.AppIcon
import com.procwatch.ui.components.ReadoutRow
import com.procwatch.ui.components.StatusChip
import com.procwatch.ui.theme.DataStyle
import com.procwatch.ui.theme.EyebrowStyle
import com.procwatch.ui.theme.MetaStyle
import com.procwatch.ui.theme.Panel
import com.procwatch.ui.theme.bucketColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailSheet(viewModel: MainViewModel) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var confirmStop by remember { mutableStateOf(false) }
    val current = detail ?: return
    val row = current.row

    ModalBottomSheet(
        onDismissRequest = { viewModel.select(null) },
        sheetState = sheetState,
        containerColor = Panel.Surface,
        contentColor = Panel.TextPrimary
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(row.packageName, size = 46.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.meta.label, style = DataStyle, color = Panel.TextPrimary, maxLines = 2)
                    Text(
                        row.packageName,
                        style = MetaStyle,
                        color = Panel.TextFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusChip(Bucket.label(row.standbyBucket), bucketColor(row.standbyBucket))
            }

            Spacer(Modifier.height(18.dp))

            Text("STATE", style = EyebrowStyle, color = Panel.TextFaint)
            Spacer(Modifier.height(6.dp))
            ReadoutRow(
                "Processes",
                if (Capability.LIST_PROCESSES in capabilities) "${row.processCount}" else "needs Shizuku"
            )
            ReadoutRow(
                "Memory (PSS)",
                Format.kb(row.pssKb),
                valueColor = if (row.isRunning) Panel.Signal else Panel.TextFaint
            )
            ReadoutRow("Last used", Format.relativeTime(row.lastUsed))
            ReadoutRow("Screen time today", Format.duration(row.foregroundMsToday))
            ReadoutRow("Keep running", if (row.isWhitelisted) "Protected" else "Not protected")
            ReadoutRow("Enabled", if (row.meta.isEnabled) "Yes" else "Frozen")

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Panel.Outline)
            Spacer(Modifier.height(16.dp))

            Text("PACKAGE", style = EyebrowStyle, color = Panel.TextFaint)
            Spacer(Modifier.height(6.dp))
            ReadoutRow("Version", row.meta.versionName ?: "—")
            ReadoutRow("UID", row.meta.uid.toString())
            ReadoutRow("Type", if (row.meta.isSystem) "System" else "User")
            ReadoutRow("Installed", Format.dateTime(row.meta.firstInstallTime))
            ReadoutRow("Updated", Format.dateTime(row.meta.lastUpdateTime))

            current.storage?.let { storage ->
                Spacer(Modifier.height(6.dp))
                ReadoutRow("App size", Format.bytes(storage.appBytes))
                ReadoutRow("Data", Format.bytes(storage.dataBytes))
                ReadoutRow("Cache", Format.bytes(storage.cacheBytes))
            }

            if (current.processes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Panel.Outline)
                Spacer(Modifier.height(16.dp))
                Text("LIVE PROCESSES", style = EyebrowStyle, color = Panel.TextFaint)
                Spacer(Modifier.height(6.dp))
                current.processes.forEach { process ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            process.pid.toString().padStart(6),
                            style = MetaStyle,
                            color = Panel.TextFaint
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            process.name,
                            style = MetaStyle,
                            color = Panel.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            Format.kb(process.pssKb),
                            style = MetaStyle,
                            color = Panel.Signal
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = { confirmStop = true },
                enabled = !row.isWhitelisted && Capability.FORCE_STOP in capabilities,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Panel.Danger,
                    contentColor = Panel.Background,
                    disabledContainerColor = Panel.SurfaceRaised,
                    disabledContentColor = Panel.TextFaint
                )
            ) {
                Text(
                    if (row.isWhitelisted) "Protected — stopping disabled" else "Force stop",
                    style = DataStyle
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.toggleWhitelist(row.packageName) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (row.isWhitelisted) "Stop protecting" else "Keep running",
                        style = DataStyle
                    )
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", row.packageName, null)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("App info", style = DataStyle)
                }
            }

            if (Capability.FREEZE_APP in capabilities || Capability.REVOKE_BACKGROUND in capabilities) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (Capability.FREEZE_APP in capabilities) {
                        OutlinedButton(
                            onClick = { viewModel.setEnabled(row.packageName, !row.meta.isEnabled) },
                            enabled = !row.isWhitelisted,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (row.meta.isEnabled) "Freeze" else "Unfreeze", style = DataStyle)
                        }
                    }
                    if (Capability.REVOKE_BACKGROUND in capabilities) {
                        OutlinedButton(
                            onClick = { viewModel.revokeBackground(row.packageName) },
                            enabled = !row.isWhitelisted,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Block background", style = DataStyle)
                        }
                    }
                }
            }

            if (!current.loaded) {
                Spacer(Modifier.height(10.dp))
                Text("Reading storage and live memory…", style = MetaStyle, color = Panel.TextFaint)
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = Panel.Surface,
            title = { Text("Force stop ${row.meta.label}?", color = Panel.TextPrimary) },
            text = {
                Text(
                    "The app goes into the stopped state. Its alarms and scheduled jobs are " +
                        "cancelled, and it sends no notifications until you open it yourself.",
                    style = DataStyle,
                    color = Panel.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    viewModel.forceStop(row.packageName)
                }) { Text("Force stop", color = Panel.Danger) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmStop = false
                    viewModel.toggleWhitelist(row.packageName)
                }) { Text("Keep running instead", color = Panel.TextSecondary) }
            }
        )
    }
}
