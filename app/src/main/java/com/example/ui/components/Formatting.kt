package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.SocksProfile
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.JetBrainsMonoFamily
import java.util.Locale

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

fun formatSpeed(bytesPerSec: Long): String = "${formatBytes(bytesPerSec)}/s"

fun formatDuration(elapsedMs: Long): String {
    val totalSec = (elapsedMs / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
}

fun latencyColor(latencyMs: Int?): Color = when {
    latencyMs == null -> Color.Gray
    latencyMs < 150 -> ElectricEmerald
    latencyMs < 350 -> AmberPulse
    else -> CrimsonAlert
}

@Composable
fun ServerEditorDialog(
    initialProfile: SocksProfile?,
    onDismiss: () -> Unit,
    onSave: (SocksProfile) -> Unit,
) {
    val strings = LocalAppStrings.current
    var name by remember(initialProfile) { mutableStateOf(initialProfile?.name.orEmpty()) }
    var host by remember(initialProfile) { mutableStateOf(initialProfile?.host.orEmpty()) }
    var portText by remember(initialProfile) {
        mutableStateOf((initialProfile?.port ?: 1080).toString())
    }
    var username by remember(initialProfile) { mutableStateOf(initialProfile?.username.orEmpty()) }
    var password by remember(initialProfile) { mutableStateOf(initialProfile?.password.orEmpty()) }
    var passwordVisible by remember { mutableStateOf(false) }

    var showAdvanced by remember { mutableStateOf(false) }
    var udpMode by remember(initialProfile) {
        mutableStateOf(initialProfile?.udpMode ?: SocksProfile.UDP_MODE_TCP)
    }
    var useRemoteDns by remember(initialProfile) {
        mutableStateOf(initialProfile?.useRemoteDns ?: true)
    }
    var customDns by remember(initialProfile) {
        mutableStateOf(initialProfile?.customDns ?: "1.1.1.1")
    }
    var enableIpv6 by remember(initialProfile) {
        mutableStateOf(initialProfile?.enableIpv6 ?: false)
    }
    var mtuText by remember(initialProfile) {
        mutableStateOf((initialProfile?.mtu ?: 8500).toString())
    }

    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = if (initialProfile == null) strings.addServerTitle else strings.editServerTitle,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.fieldName) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_name_input"),
                )

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = {
                                host = it
                                errorText = null
                            },
                            label = { Text(strings.fieldHost) },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier
                                .weight(2f)
                                .testTag("server_host_input"),
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() } && input.length <= 5) {
                                    portText = input
                                    errorText = null
                                }
                            },
                            label = { Text(strings.fieldPort) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("server_port_input"),
                        )
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(strings.fieldUsername) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_username_input"),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(strings.fieldPassword) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = JetBrainsMonoFamily),
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = "Toggle password visibility",
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_password_input"),
                    )
                }

                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_advanced_button"),
                ) {
                    Text(strings.advancedOptions)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = strings.udpRelayMode,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = udpMode == SocksProfile.UDP_MODE_TCP,
                                onClick = { udpMode = SocksProfile.UDP_MODE_TCP },
                                label = { Text(strings.udpOverTcp) },
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = udpMode == SocksProfile.UDP_MODE_UDP,
                                onClick = { udpMode = SocksProfile.UDP_MODE_UDP },
                                label = { Text(strings.udpNative) },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(strings.remoteDnsSwitch, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    strings.remoteDnsDesc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = useRemoteDns,
                                onCheckedChange = { useRemoteDns = it },
                            )
                        }

                        if (!useRemoteDns) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                OutlinedTextField(
                                    value = customDns,
                                    onValueChange = { customDns = it },
                                    label = { Text(strings.customDnsField) },
                                    singleLine = true,
                                    textStyle = TextStyle(fontFamily = JetBrainsMonoFamily),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(strings.ipv6Switch, style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = enableIpv6,
                                onCheckedChange = { enableIpv6 = it },
                            )
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = mtuText,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() } && input.length <= 4) {
                                        mtuText = input
                                    }
                                },
                                label = { Text(strings.mtuField) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(fontFamily = JetBrainsMonoFamily),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                if (errorText != null) {
                    Text(
                        text = errorText.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanHost = host.trim()
                    val parsedPort = portText.toIntOrNull()
                    if (cleanHost.isEmpty()) {
                        errorText = strings.hostRequiredError
                        return@Button
                    }
                    if (parsedPort == null || parsedPort !in 1..65535) {
                        errorText = strings.portInvalidError
                        return@Button
                    }
                    val parsedMtu = (mtuText.toIntOrNull() ?: 8500).coerceIn(1280, 9000)
                    val profile = (initialProfile ?: SocksProfile(name = "", host = "")).copy(
                        name = name.trim(),
                        host = cleanHost,
                        port = parsedPort,
                        username = username.trim(),
                        password = password,
                        udpMode = udpMode,
                        useRemoteDns = useRemoteDns,
                        customDns = customDns.trim().ifBlank { "1.1.1.1" },
                        enableIpv6 = enableIpv6,
                        mtu = parsedMtu,
                    )
                    onSave(profile)
                },
                modifier = Modifier.testTag("save_server_button"),
            ) {
                Text(strings.saveAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
    )
}

@Composable
fun ImportUriDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Boolean,
) {
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(strings.importDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            showError = false
                        },
                        placeholder = {
                            Text(
                                text = strings.importDialogHint,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        minLines = 3,
                        maxLines = 5,
                        textStyle = TextStyle(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_uri_input"),
                    )
                }

                OutlinedButton(
                    onClick = {
                        val clip = clipboard.getText()?.text.orEmpty()
                        if (clip.isNotBlank()) {
                            text = clip
                            showError = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.pasteClipboard)
                }

                if (showError) {
                    Text(
                        text = strings.importInvalidMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onImport(text)) {
                        onDismiss()
                    } else {
                        showError = true
                    }
                },
                modifier = Modifier.testTag("confirm_import_button"),
            ) {
                Text(strings.saveAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
    )
}
