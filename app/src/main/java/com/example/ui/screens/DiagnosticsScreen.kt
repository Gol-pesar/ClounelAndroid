package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.data.model.AppThemeMode
import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import com.example.data.model.ProbeResult
import com.example.data.model.ProbeStepStatus
import com.example.data.model.SocksProfile
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SignalCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    selectedProfile: SocksProfile?,
    deepProbeResult: ProbeResult?,
    isRunningDeepProbe: Boolean,
    logs: List<LogEntry>,
    settings: AppSettings,
    onRunDeepProbe: () -> Unit,
    onClearLogs: () -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = strings.diagnosticsTitle,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            // Deep SOCKS5 Protocol Probe Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onRunDeepProbe,
                            enabled = selectedProfile != null && !isRunningDeepProbe,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_probe_button"),
                        ) {
                            if (isRunningDeepProbe) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(strings.testing)
                            } else {
                                Icon(Icons.Filled.BugReport, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(strings.runDeepTest)
                            }
                        }

                        if (deepProbeResult != null) {
                            val stepLabels = mapOf(
                                "dns" to strings.stepDns,
                                "tcp" to strings.stepTcp,
                                "auth" to strings.stepAuth,
                                "http" to strings.stepHttp,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                deepProbeResult.steps.forEach { step ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                imageVector = when (step.status) {
                                                    ProbeStepStatus.PASSED -> Icons.Filled.CheckCircle
                                                    ProbeStepStatus.FAILED -> Icons.Filled.Cancel
                                                    else -> Icons.Filled.RadioButtonUnchecked
                                                },
                                                contentDescription = null,
                                                tint = when (step.status) {
                                                    ProbeStepStatus.PASSED -> ElectricEmerald
                                                    ProbeStepStatus.FAILED -> CrimsonAlert
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = stepLabels[step.key] ?: step.key,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        if (step.detail.isNotBlank()) {
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Text(
                                                    text = step.detail,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = JetBrainsMonoFamily,
                                                    ),
                                                    color = when (step.status) {
                                                        ProbeStepStatus.PASSED -> SignalCyan
                                                        ProbeStepStatus.FAILED -> CrimsonAlert
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // App Settings Card (Language + Theme)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = strings.settingsTitle,
                            style = MaterialTheme.typography.titleMedium,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.Language, contentDescription = null, tint = SignalCyan)
                            Text(strings.languageLabel, style = MaterialTheme.typography.bodyLarge)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.language == AppLanguage.FA,
                                onClick = { onSetLanguage(AppLanguage.FA) },
                                label = { Text("فارسی") },
                                modifier = Modifier.weight(1f).testTag("lang_fa_chip"),
                            )
                            FilterChip(
                                selected = settings.language == AppLanguage.EN,
                                onClick = { onSetLanguage(AppLanguage.EN) },
                                label = { Text("English") },
                                modifier = Modifier.weight(1f).testTag("lang_en_chip"),
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.Palette, contentDescription = null, tint = ElectricEmerald)
                            Text(strings.themeLabel, style = MaterialTheme.typography.bodyLarge)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.themeMode == AppThemeMode.DARK,
                                onClick = { onSetThemeMode(AppThemeMode.DARK) },
                                label = { Text(strings.themeDark) },
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = settings.themeMode == AppThemeMode.LIGHT,
                                onClick = { onSetThemeMode(AppThemeMode.LIGHT) },
                                label = { Text(strings.themeLight) },
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = settings.themeMode == AppThemeMode.SYSTEM,
                                onClick = { onSetThemeMode(AppThemeMode.SYSTEM) },
                                label = { Text(strings.themeSystem) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Live Tunnel Logs Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = strings.liveLogsTitle,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        val dump = logs.joinToString("\n") { entry ->
                                            "[${timeFormatter.format(Date(entry.timestampMs))}] ${entry.level.name}: ${entry.message}"
                                        }
                                        clipboard.setText(AnnotatedString(dump))
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = strings.copyLogs)
                                }
                                IconButton(
                                    onClick = onClearLogs,
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(Icons.Filled.DeleteSweep, contentDescription = strings.clearLogs)
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (logs.isEmpty()) {
                                        Text(
                                            text = strings.noLogsYet,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        logs.asReversed().take(50).forEach { entry ->
                                            val levelColor = when (entry.level) {
                                                LogLevel.SUCCESS -> ElectricEmerald
                                                LogLevel.WARN -> AmberPulse
                                                LogLevel.ERROR -> CrimsonAlert
                                                LogLevel.INFO -> SignalCyan
                                            }
                                            Text(
                                                text = "[${timeFormatter.format(Date(entry.timestampMs))}] ${entry.message}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontWeight = FontWeight.Normal,
                                                ),
                                                color = levelColor,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
