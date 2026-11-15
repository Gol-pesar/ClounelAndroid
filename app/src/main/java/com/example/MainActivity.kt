package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.AppLanguage
import com.example.data.model.SocksProfile
import com.example.data.model.TunnelStatus
import com.example.ui.components.ImportUriDialog
import com.example.ui.components.ServerEditorDialog
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsFor
import com.example.ui.screens.ConnectScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.RoutingScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.theme.SocksFlowTheme
import com.example.ui.viewmodel.SocksViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object ConnectRoute

@Serializable
object ServersRoute

@Serializable
object RoutingRoute

@Serializable
object DiagnosticsRoute

private data class NavTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SocksFlowApp()
        }
    }
}

@Composable
fun SocksFlowApp(viewModel: SocksViewModel = viewModel()) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val snapshot by viewModel.tunnelSnapshot.collectAsStateWithLifecycle()
    val probingIds by viewModel.isProbingProfileIds.collectAsStateWithLifecycle()
    val isTestingAll by viewModel.isTestingAll.collectAsStateWithLifecycle()
    val deepProbeResult by viewModel.deepProbeResult.collectAsStateWithLifecycle()
    val isRunningDeepProbe by viewModel.isRunningDeepProbe.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()

    val strings = remember(settings.language) { stringsFor(settings.language) }
    val layoutDirection = if (settings.language == AppLanguage.FA) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<SocksProfile?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpnForSelected()
        } else {
            viewModel.logWarning(strings.vpnPermissionDenied)
            scope.launch { snackbarHostState.showSnackbar(strings.vpnPermissionDenied) }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* Tunnel works even if user declines notifications */ }

    fun triggerToggleConnection() {
        val isActive = snapshot.status == TunnelStatus.CONNECTED ||
            snapshot.status == TunnelStatus.CONNECTING
        if (isActive) {
            viewModel.disconnectVpn()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.startVpnForSelected()
        }
    }

    SocksFlowTheme(themeMode = settings.themeMode) {
        CompositionLocalProvider(
            LocalAppStrings provides strings,
            LocalLayoutDirection provides layoutDirection,
        ) {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            val tabs = remember(strings) {
                listOf(
                    NavTab(
                        route = ConnectRoute,
                        label = strings.navConnect,
                        selectedIcon = Icons.Filled.PowerSettingsNew,
                        unselectedIcon = Icons.Outlined.PowerSettingsNew,
                        testTag = "nav_connect",
                    ),
                    NavTab(
                        route = ServersRoute,
                        label = strings.navServers,
                        selectedIcon = Icons.Filled.Dns,
                        unselectedIcon = Icons.Outlined.Dns,
                        testTag = "nav_servers",
                    ),
                    NavTab(
                        route = RoutingRoute,
                        label = strings.navRouting,
                        selectedIcon = Icons.Filled.AltRoute,
                        unselectedIcon = Icons.Outlined.AltRoute,
                        testTag = "nav_routing",
                    ),
                    NavTab(
                        route = DiagnosticsRoute,
                        label = strings.navDiagnostics,
                        selectedIcon = Icons.Filled.Tune,
                        unselectedIcon = Icons.Outlined.Tune,
                        testTag = "nav_diagnostics",
                    ),
                )
            }

            fun navigateToTab(route: Any) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ) {
                                tabs.forEach { tab ->
                                    val selected = currentDestination?.hasRoute(tab.route::class) == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateToTab(tab.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label,
                                            )
                                        },
                                        label = { Text(tab.label, maxLines = 1) },
                                        modifier = Modifier.testTag(tab.testTag),
                                    )
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        if (useRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ) {
                                tabs.forEach { tab ->
                                    val selected = currentDestination?.hasRoute(tab.route::class) == true
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateToTab(tab.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label,
                                            )
                                        },
                                        label = { Text(tab.label, maxLines = 1) },
                                        modifier = Modifier.testTag(tab.testTag),
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = ConnectRoute,
                            ) {
                                composable<ConnectRoute> {
                                    ConnectScreen(
                                        snapshot = snapshot,
                                        selectedProfile = selectedProfile,
                                        isProbing = selectedProfile?.let { probingIds.contains(it.id) } == true,
                                        onToggleConnection = ::triggerToggleConnection,
                                        onProbeSelected = {
                                            selectedProfile?.let { viewModel.probeProfile(it) }
                                        },
                                        onOpenServers = { navigateToTab(ServersRoute) },
                                        onAddNewServer = {
                                            editingProfile = null
                                            showEditorDialog = true
                                        },
                                    )
                                }

                                composable<ServersRoute> {
                                    ServersScreen(
                                        profiles = profiles,
                                        probingIds = probingIds,
                                        isTestingAll = isTestingAll,
                                        onSelectProfile = { viewModel.selectProfile(it) },
                                        onProbeProfile = { viewModel.probeProfile(it) },
                                        onTestAll = { viewModel.testAllProfiles() },
                                        onAddClick = {
                                            editingProfile = null
                                            showEditorDialog = true
                                        },
                                        onImportClick = { showImportDialog = true },
                                        onEditClick = { profile ->
                                            editingProfile = profile
                                            showEditorDialog = true
                                        },
                                        onDeleteClick = { viewModel.deleteProfile(it) },
                                    )
                                }

                                composable<RoutingRoute> {
                                    RoutingScreen(
                                        settings = settings,
                                        installedApps = installedApps,
                                        isLoadingApps = isLoadingApps,
                                        onLoadApps = { viewModel.loadInstalledAppsIfNeeded() },
                                        onSetSplitMode = { viewModel.setSplitTunnelMode(it) },
                                        onTogglePackage = { viewModel.toggleAppPackage(it) },
                                        onClearSelection = { viewModel.clearSelectedPackages() },
                                        onSetLanBypass = { viewModel.setLanBypass(it) },
                                    )
                                }

                                composable<DiagnosticsRoute> {
                                    DiagnosticsScreen(
                                        selectedProfile = selectedProfile,
                                        deepProbeResult = deepProbeResult,
                                        isRunningDeepProbe = isRunningDeepProbe,
                                        logs = logs,
                                        settings = settings,
                                        onRunDeepProbe = { viewModel.runDeepDiagnostics() },
                                        onClearLogs = { viewModel.clearLogs() },
                                        onSetLanguage = { viewModel.setLanguage(it) },
                                        onSetThemeMode = { viewModel.setThemeMode(it) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (showEditorDialog) {
                    ServerEditorDialog(
                        initialProfile = editingProfile,
                        onDismiss = { showEditorDialog = false },
                        onSave = { profile ->
                            viewModel.saveProfile(profile)
                            showEditorDialog = false
                        },
                    )
                }

                if (showImportDialog) {
                    ImportUriDialog(
                        onDismiss = { showImportDialog = false },
                        onImport = { raw -> viewModel.importFromUri(raw) },
                    )
                }
            }
        }
    }
}
