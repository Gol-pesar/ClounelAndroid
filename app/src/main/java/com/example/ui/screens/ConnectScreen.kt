package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.SocksProfile
import com.example.data.model.SpeedSample
import com.example.data.model.TunnelSnapshot
import com.example.data.model.TunnelStatus
import com.example.ui.components.formatBytes
import com.example.ui.components.formatDuration
import com.example.ui.components.formatSpeed
import com.example.ui.components.latencyColor
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SpaceGroteskFamily
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    snapshot: TunnelSnapshot,
    selectedProfile: SocksProfile?,
    isProbing: Boolean,
    onToggleConnection: () -> Unit,
    onProbeSelected: () -> Unit,
    onOpenServers: () -> Unit,
    onAddNewServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(snapshot.status, snapshot.connectedSinceMs) {
        if (snapshot.status == TunnelStatus.CONNECTED && snapshot.connectedSinceMs != null) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val accentColor by animateColorAsState(
        targetValue = when (snapshot.status) {
            TunnelStatus.CONNECTED -> ElectricEmerald
            TunnelStatus.CONNECTING, TunnelStatus.DISCONNECTING -> AmberPulse
            TunnelStatus.ERROR -> CrimsonAlert
            TunnelStatus.DISCONNECTED -> SignalCyan
        },
        label = "accentColor",
    )

    val statusText = when (snapshot.status) {
        TunnelStatus.CONNECTED -> strings.statusConnected
        TunnelStatus.CONNECTING -> strings.statusConnecting
        TunnelStatus.DISCONNECTING -> strings.statusDisconnecting
        TunnelStatus.ERROR -> strings.statusError
        TunnelStatus.DISCONNECTED -> strings.statusDisconnected
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height * 0.25f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Brand + Status Pill Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = strings.appTitle,
                            tint = accentColor,
                            modifier = Modifier.padding(8.dp).size(22.dp),
                        )
                    }
                    Text(
                        text = strings.appTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = SpaceGroteskFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                        )
                    }
                }
            }

            // Hero Power Core
            TunnelPowerCore(
                status = snapshot.status,
                accentColor = accentColor,
                enabled = selectedProfile != null,
                onClick = onToggleConnection,
            )

            // Subtitle or Uptime
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (snapshot.status == TunnelStatus.CONNECTED && snapshot.connectedSinceMs != null) {
                    val elapsed = (nowMs - snapshot.connectedSinceMs).coerceAtLeast(0L)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = formatDuration(elapsed),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = ElectricEmerald,
                        )
                    }
                    Text(
                        text = strings.tapToDisconnect,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (snapshot.status == TunnelStatus.ERROR && !snapshot.errorMessage.isNullOrBlank()) {
                    Text(
                        text = snapshot.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CrimsonAlert,
                    )
                } else if (selectedProfile != null) {
                    Text(
                        text = strings.tapToConnect,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Active Server Card or Empty Onboarding Card
            if (selectedProfile == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Radar,
                            contentDescription = null,
                            tint = SignalCyan,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = strings.noServerSelectedTitle,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = strings.noServerSelectedDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onAddNewServer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_add_server_button"),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.addServerAction)
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenServers() }
                        .testTag("active_server_card"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (snapshot.status == TunnelStatus.CONNECTED) {
                            ElectricEmerald.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.activeServerLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = selectedProfile.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = "${selectedProfile.host}:${selectedProfile.port}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                        ),
                                        color = SignalCyan,
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onProbeSelected,
                                enabled = !isProbing,
                                modifier = Modifier.testTag("quick_probe_button"),
                            ) {
                                if (isProbing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.testing)
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.testConnection)
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AssistChip(
                                onClick = {},
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (selectedProfile.hasAuth) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        if (selectedProfile.hasAuth) strings.authBadge else strings.noAuthBadge,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                            AssistChip(
                                onClick = {},
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Dns,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        if (selectedProfile.useRemoteDns) strings.remoteDnsBadge else strings.localDnsBadge,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        if (selectedProfile.udpMode == SocksProfile.UDP_MODE_UDP) {
                                            strings.udpNativeBadge
                                        } else {
                                            strings.udpTcpBadge
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                        }

                        if (selectedProfile.lastLatencyMs != null || !selectedProfile.lastExitIp.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                selectedProfile.lastLatencyMs?.let { ms ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = latencyColor(ms).copy(alpha = 0.14f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = strings.latencyLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Text(
                                                    text = "$ms ms",
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                    color = latencyColor(ms),
                                                )
                                            }
                                        }
                                    }
                                }
                                selectedProfile.lastExitIp?.let { ip ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Public,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = SignalCyan,
                                            )
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                val text = listOfNotNull(ip, selectedProfile.lastExitCountry)
                                                    .joinToString(" • ")
                                                Text(
                                                    text = text,
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontFamily = JetBrainsMonoFamily,
                                                    ),
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

            // Live Telemetry Cards + Throughput Sparkline
            AnimatedVisibility(visible = snapshot.status == TunnelStatus.CONNECTED) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TrafficMetricCard(
                            label = strings.downloadLabel,
                            speedText = formatSpeed(snapshot.rxSpeedBps),
                            totalText = "${strings.totalLabel}: ${formatBytes(snapshot.rxBytes)}",
                            accent = ElectricEmerald,
                            icon = Icons.Filled.ArrowDownward,
                            modifier = Modifier.weight(1f),
                        )
                        TrafficMetricCard(
                            label = strings.uploadLabel,
                            speedText = formatSpeed(snapshot.txSpeedBps),
                            totalText = "${strings.totalLabel}: ${formatBytes(snapshot.txBytes)}",
                            accent = SignalCyan,
                            icon = Icons.Filled.ArrowUpward,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    ThroughputSparklineCard(
                        title = strings.trafficGraphTitle,
                        samples = snapshot.speedHistory,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TunnelPowerCore(
    status: TunnelStatus,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "powerCore")
    val spinAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinAngle",
    )
    val pulseScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(
        modifier = Modifier.size(216.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension * 0.45f

            // Outer ambient ring
            drawCircle(
                color = accentColor.copy(alpha = if (status == TunnelStatus.CONNECTED) 0.22f else 0.10f),
                radius = outerRadius * (if (status == TunnelStatus.CONNECTED) pulseScale else 1f),
                center = center,
                style = Stroke(width = 3.dp.toPx()),
            )

            // Secondary precision track
            drawCircle(
                color = accentColor.copy(alpha = 0.20f),
                radius = outerRadius * 0.84f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )

            if (status == TunnelStatus.CONNECTING || status == TunnelStatus.DISCONNECTING) {
                val arcRadius = outerRadius * 0.92f
                drawArc(
                    color = accentColor,
                    startAngle = spinAngle,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                )
            } else if (status == TunnelStatus.CONNECTED) {
                val arcRadius = outerRadius * 0.92f
                drawArc(
                    color = ElectricEmerald,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        Button(
            onClick = onClick,
            enabled = enabled && status != TunnelStatus.DISCONNECTING,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (status == TunnelStatus.CONNECTED) {
                    ElectricEmerald
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (status == TunnelStatus.CONNECTED) {
                    Color(0xFF00291F)
                } else {
                    accentColor
                },
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .size(134.dp)
                .minimumInteractiveComponentSize()
                .testTag("power_connect_button"),
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = if (status == TunnelStatus.CONNECTED) "Disconnect" else "Connect",
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

@Composable
private fun TrafficMetricCard(
    label: String,
    speedText: String,
    totalText: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = speedText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = accent,
                )
            }
            Text(
                text = totalText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThroughputSparklineCard(
    title: String,
    samples: List<SpeedSample>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp),
            ) {
                val maxVal = samples
                    .maxOfOrNull { maxOf(it.rxBytesPerSec, it.txBytesPerSec) }
                    ?.coerceAtLeast(1024L)
                    ?: 1024L

                // Horizontal subtle grid lines
                for (i in 1..2) {
                    val y = size.height * i / 3f
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.16f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                if (samples.size >= 2) {
                    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)

                    fun buildPath(selector: (SpeedSample) -> Long): Path {
                        return Path().apply {
                            samples.forEachIndexed { idx, s ->
                                val x = idx * stepX
                                val ratio = (selector(s).toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                                val y = size.height - (ratio * (size.height - 8.dp.toPx())) - 4.dp.toPx()
                                if (idx == 0) moveTo(x, y) else lineTo(x, y)
                            }
                        }
                    }

                    drawPath(
                        path = buildPath { it.txBytesPerSec },
                        color = SignalCyan,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawPath(
                        path = buildPath { it.rxBytesPerSec },
                        color = ElectricEmerald,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}
