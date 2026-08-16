package com.procwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.procwatch.ui.MainViewModel
import com.procwatch.ui.apps.AppsScreen
import com.procwatch.ui.dashboard.DashboardScreen
import com.procwatch.ui.settings.SettingsScreen
import com.procwatch.ui.theme.EyebrowStyle
import com.procwatch.ui.theme.Panel
import com.procwatch.ui.theme.ProcWatchTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as ProcWatchApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The no-argument enableEdgeToEdge() picks system bar icon colour from the phone's
        // dark mode setting rather than from this app's theme. ProcWatch is always dark, so
        // on a phone in light mode the status bar drew dark icons over a near-black
        // background and the clock, battery and signal all but vanished. Worse, uiMode is in
        // this activity's configChanges, so nothing recreates it — the icons stayed wrong
        // until the app was closed and reopened.
        //
        // dark() states the fact instead of inferring it. A transparent scrim is safe here
        // because the bottom NavigationBar paints Panel.Surface behind the system bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        // Shizuku's binder can arrive or die at any moment; the listeners keep the whole UI
        // honest about which tier is actually live.
        (application as ProcWatchApp).container.shizuku.attach()

        setContent {
            ProcWatchTheme {
                ProcWatchRoot(viewModel)
            }
        }
    }

    override fun onDestroy() {
        (application as ProcWatchApp).container.shizuku.detach()
        super.onDestroy()
    }
}

private enum class Tab(val label: String) {
    DASHBOARD("Overview"),
    APPS("Apps"),
    SETTINGS("Setup")
}

@Composable
private fun ProcWatchRoot(viewModel: MainViewModel) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh when the screen comes back into view rather than polling. Usage Access and
    // Shizuku are both granted in other apps, so returning here is exactly when state changed.
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = Panel.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Panel.Surface, tonalElevation = 0.dp) {
                Tab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    Tab.DASHBOARD -> Icons.Default.Home
                                    Tab.APPS -> Icons.AutoMirrored.Filled.List
                                    Tab.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label.uppercase(), style = EyebrowStyle) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Panel.Signal,
                            selectedTextColor = Panel.Signal,
                            unselectedIconColor = Panel.TextFaint,
                            unselectedTextColor = Panel.TextFaint,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (Tab.entries[tabIndex]) {
                Tab.DASHBOARD -> DashboardScreen(viewModel, onOpenApps = { tabIndex = 1 })
                Tab.APPS -> AppsScreen(viewModel)
                Tab.SETTINGS -> SettingsScreen(viewModel)
            }
        }
    }
}
