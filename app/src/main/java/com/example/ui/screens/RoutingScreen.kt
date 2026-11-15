package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.AppSettings
import com.example.data.model.InstalledAppInfo
import com.example.data.model.SplitTunnelMode
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SignalCyan

@Composable
fun RoutingScreen(
    settings: AppSettings,
    installedApps: List<InstalledAppInfo>,
    isLoadingApps: Boolean,
    onLoadApps: () -> Unit,
    onSetSplitMode: (SplitTunnelMode) -> Unit,
    onTogglePackage: (String) -> Unit,
    onClearSelection: () -> Unit,
    onSetLanBypass: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onLoadApps()
    }

    val filteredApps = remember(installedApps, searchQuery, showSystemApps, settings.selectedPackages) {
        installedApps
            .filter { app -> showSystemApps || !app.isSystemApp || settings.selectedPackages.contains(app.packageName) }
            .filter { app ->
                searchQuery.isBlank() ||
                    app.label.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { settings.selectedPackages.contains(it.packageName) }
                    .thenBy { it.label.lowercase() },
            )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = strings.routingTitle,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            // LAN Bypass Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lan,
                                contentDescription = null,
                                tint = SignalCyan,
                            )
                            Column {
                                Text(
                                    text = strings.lanBypassTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = strings.lanBypassDesc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = settings.lanBypass,
                            onCheckedChange = onSetLanBypass,
                            modifier = Modifier.testTag("lan_bypass_switch"),
                        )
                    }
                }
            }

            // Split Tunnel Mode Selector
            item {
                Text(
                    text = strings.splitModeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SplitModeOptionCard(
                        title = strings.modeAllApps,
                        description = strings.modeAllAppsDesc,
                        selected = settings.splitTunnelMode == SplitTunnelMode.ALL_APPS,
                        onClick = { onSetSplitMode(SplitTunnelMode.ALL_APPS) },
                        testTag = "split_mode_all",
                    )
                    SplitModeOptionCard(
                        title = strings.modeBypassSelected,
                        description = strings.modeBypassSelectedDesc,
                        selected = settings.splitTunnelMode == SplitTunnelMode.BYPASS_SELECTED,
                        onClick = { onSetSplitMode(SplitTunnelMode.BYPASS_SELECTED) },
                        testTag = "split_mode_bypass",
                    )
                    SplitModeOptionCard(
                        title = strings.modeOnlySelected,
                        description = strings.modeOnlySelectedDesc,
                        selected = settings.splitTunnelMode == SplitTunnelMode.ONLY_SELECTED,
                        onClick = { onSetSplitMode(SplitTunnelMode.ONLY_SELECTED) },
                        testTag = "split_mode_only",
                    )
                }
            }

            if (settings.splitTunnelMode != SplitTunnelMode.ALL_APPS) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            placeholder = { Text(strings.searchAppsHint) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_apps_input"),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = showSystemApps,
                                    onCheckedChange = { showSystemApps = it },
                                )
                                Text(
                                    text = strings.showSystemApps,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = strings.selectedAppsCount(settings.selectedPackages.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricEmerald,
                                )
                                if (settings.selectedPackages.isNotEmpty()) {
                                    TextButton(onClick = onClearSelection) {
                                        Text(strings.clearSelection)
                                    }
                                }
                            }
                        }
                    }
                }

                if (isLoadingApps) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val checked = settings.selectedPackages.contains(app.packageName)
                        AppRowCard(
                            app = app,
                            checked = checked,
                            onToggle = { onTogglePackage(app.packageName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) ElectricEmerald.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppRowCard(
    app: InstalledAppInfo,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (checked) ElectricEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconImage(drawable = app.icon, contentDescription = app.label)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun AppIconImage(
    drawable: Drawable?,
    contentDescription: String,
) {
    val bitmap = remember(drawable) {
        drawable?.let {
            runCatching { it.toBitmap(width = 72, height = 72).asImageBitmap() }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier.size(36.dp),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = contentDescription,
            modifier = Modifier.size(36.dp),
            tint = SignalCyan,
        )
    }
}
