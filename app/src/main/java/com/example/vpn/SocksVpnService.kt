package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.model.AppSettings
import com.example.data.model.LogLevel
import com.example.data.model.SocksProfile
import com.example.data.model.SplitTunnelMode
import com.example.data.model.TunnelStatus
import com.example.network.CidrUtils
import com.example.network.HevConfigGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.amnezia.awg.hevtunnel.TProxyService
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress

class SocksVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private var startJob: Job? = null

    @Volatile
    private var nativeEngineRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopTunnel(isError = false)
                return START_NOT_STICKY
            }
            ACTION_CONNECT, null -> {
                val profileId = intent?.getLongExtra(EXTRA_PROFILE_ID, -1L) ?: -1L
                ensureNotificationChannel()
                startForegroundCompat(buildNotification("SocksFlow", "Connecting..."))
                startJob?.cancel()
                startJob = serviceScope.launch {
                    startTunnel(profileId)
                }
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private suspend fun startTunnel(requestedProfileId: Long) {
        try {
            if (nativeEngineRunning) {
                stopNativeEngineOnly()
            }

            val dao = AppDatabase.getInstance(applicationContext).socksProfileDao()
            val settingsStore = SettingsDataStore(applicationContext)

            val profile = if (requestedProfileId > 0L) {
                dao.getProfileById(requestedProfileId)
            } else {
                dao.getSelectedProfile() ?: dao.getAllProfiles().firstOrNull()
            }

            if (profile == null) {
                TunnelRuntimeState.setError("No SOCKS5 server profile selected")
                stopSelfCleanly()
                return
            }

            val settings = settingsStore.getSnapshot()
            TunnelRuntimeState.setConnecting(
                profileId = profile.id,
                profileName = profile.displayName,
                endpoint = "${profile.host}:${profile.port}",
            )

            // Resolve proxy host while our app is not routed through VPN yet
            val resolvedIp = resolveProxyHost(profile.host)
            TunnelRuntimeState.addLog(LogLevel.INFO, "Resolved ${profile.host} -> $resolvedIp")

            val builder = buildVpnInterface(profile, settings)
            val pfd = builder.establish()
            if (pfd == null) {
                TunnelRuntimeState.setError("VPN permission revoked or interface busy")
                stopSelfCleanly()
                return
            }
            vpnInterface = pfd

            val configFile = File(filesDir, "hev_socks5_config.yml")
            val yaml = HevConfigGenerator.generateYaml(profile, resolvedIp)
            configFile.writeText(yaml)

            TunnelRuntimeState.addLog(
                LogLevel.INFO,
                "Starting tunnel: MTU=${profile.mtu}, UDP=${profile.udpMode}, RemoteDNS=${profile.useRemoteDns}",
            )

            synchronized(ENGINE_LOCK) {
                TProxyService.TProxyStartService(configFile.absolutePath, pfd.fd)
                nativeEngineRunning = true
            }

            TunnelRuntimeState.setConnected(resolvedIp)
            updateNotification(
                title = "SocksFlow • ${profile.displayName}",
                text = "${profile.host}:${profile.port}",
            )
            startStatsPolling(profile.displayName)
        } catch (e: Exception) {
            TunnelRuntimeState.setError("Failed to start tunnel: ${e.message ?: "Unknown error"}")
            stopTunnel(isError = true)
        }
    }

    private fun resolveProxyHost(host: String): String {
        val addresses = InetAddress.getAllByName(host)
        val ipv4 = addresses.firstOrNull { it is Inet4Address }
        return (ipv4 ?: addresses.first()).hostAddress ?: host
    }

    private fun buildVpnInterface(profile: SocksProfile, settings: AppSettings): Builder {
        return Builder().apply {
            setSession("SocksFlow - ${profile.displayName}")
            setMtu(profile.mtu.coerceIn(1280, 9000))
            addAddress(HevConfigGenerator.TUN_IPV4, 32)
            if (profile.enableIpv6) {
                addAddress(HevConfigGenerator.TUN_IPV6, 128)
            }

            // Routes
            if (settings.lanBypass) {
                CidrUtils.publicRoutesExcludingLan().forEach { block ->
                    addRoute(block.address, block.prefixLength)
                }
                TunnelRuntimeState.addLog(LogLevel.INFO, "LAN Bypass active (private subnets excluded)")
            } else {
                addRoute("0.0.0.0", 0)
            }

            if (profile.enableIpv6) {
                addRoute("::", 0)
            }

            // DNS
            if (profile.useRemoteDns) {
                addDnsServer(HevConfigGenerator.MAPDNS_ADDRESS)
            } else {
                val dns = profile.customDns.trim().ifBlank { "1.1.1.1" }
                addDnsServer(dns)
            }

            // Split Tunneling + Loopback Prevention
            when (settings.splitTunnelMode) {
                SplitTunnelMode.ALL_APPS -> {
                    runCatching { addDisallowedApplication(packageName) }
                }
                SplitTunnelMode.BYPASS_SELECTED -> {
                    runCatching { addDisallowedApplication(packageName) }
                    settings.selectedPackages
                        .filter { it != packageName }
                        .forEach { pkg -> runCatching { addDisallowedApplication(pkg) } }
                    TunnelRuntimeState.addLog(
                        LogLevel.INFO,
                        "Split tunnel: bypassing ${settings.selectedPackages.size} apps",
                    )
                }
                SplitTunnelMode.ONLY_SELECTED -> {
                    val allowed = settings.selectedPackages.filter { it != packageName }
                    if (allowed.isEmpty()) {
                        runCatching { addDisallowedApplication(packageName) }
                        TunnelRuntimeState.addLog(
                            LogLevel.WARN,
                            "No apps selected for Only-Selected mode; routing all other apps",
                        )
                    } else {
                        allowed.forEach { pkg -> runCatching { addAllowedApplication(pkg) } }
                        TunnelRuntimeState.addLog(
                            LogLevel.INFO,
                            "Split tunnel: proxying only ${allowed.size} apps",
                        )
                    }
                }
            }
        }
    }

    private fun startStatsPolling(profileName: String) {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            var prevTx = 0L
            var prevRx = 0L
            var firstTick = true
            while (isActive && nativeEngineRunning) {
                delay(1000L)
                val stats = runCatching {
                    synchronized(ENGINE_LOCK) {
                        if (nativeEngineRunning) TProxyService.TProxyGetStats() else null
                    }
                }.getOrNull()

                if (stats != null && stats.size >= 4) {
                    val txPackets = stats[0].coerceAtLeast(0L)
                    val txBytes = stats[1].coerceAtLeast(0L)
                    val rxPackets = stats[2].coerceAtLeast(0L)
                    val rxBytes = stats[3].coerceAtLeast(0L)

                    val txSpeed = if (firstTick) 0L else (txBytes - prevTx).coerceAtLeast(0L)
                    val rxSpeed = if (firstTick) 0L else (rxBytes - prevRx).coerceAtLeast(0L)
                    prevTx = txBytes
                    prevRx = rxBytes
                    firstTick = false

                    TunnelRuntimeState.updateTrafficStats(
                        txPackets = txPackets,
                        txBytes = txBytes,
                        rxPackets = rxPackets,
                        rxBytes = rxBytes,
                        txSpeedBps = txSpeed,
                        rxSpeedBps = rxSpeed,
                    )

                    updateNotification(
                        title = "SocksFlow • $profileName",
                        text = "↓ ${formatRate(rxSpeed)}  ↑ ${formatRate(txSpeed)}",
                    )
                }
            }
        }
    }

    private fun stopNativeEngineOnly() {
        statsJob?.cancel()
        statsJob = null
        synchronized(ENGINE_LOCK) {
            if (nativeEngineRunning) {
                runCatching { TProxyService.TProxyStopService() }
                nativeEngineRunning = false
            }
        }
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    private fun stopTunnel(isError: Boolean) {
        startJob?.cancel()
        startJob = null
        if (!isError && TunnelRuntimeState.snapshot.value.status != TunnelStatus.DISCONNECTED) {
            TunnelRuntimeState.setDisconnecting()
        }
        stopNativeEngineOnly()
        if (!isError) {
            TunnelRuntimeState.setDisconnected()
        }
        stopSelfCleanly()
    }

    private fun stopSelfCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        TunnelRuntimeState.addLog(LogLevel.WARN, "VPN revoked by system or another VPN app")
        stopTunnel(isError = false)
        super.onRevoke()
    }

    override fun onDestroy() {
        stopNativeEngineOnly()
        if (TunnelRuntimeState.snapshot.value.status == TunnelStatus.CONNECTED ||
            TunnelRuntimeState.snapshot.value.status == TunnelStatus.CONNECTING ||
            TunnelRuntimeState.snapshot.value.status == TunnelStatus.DISCONNECTING
        ) {
            TunnelRuntimeState.setDisconnected()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "SOCKS5 VPN Tunnel",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Active SOCKS5 VPN connection status and live throughput"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, SocksVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(
                R.drawable.ic_vpn_stop,
                "Disconnect",
                disconnectIntent,
            )
            .build()
    }

    private fun formatRate(bytesPerSec: Long): String {
        return when {
            bytesPerSec < 1024 -> "$bytesPerSec B/s"
            bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024} KB/s"
            else -> String.format("%.1f MB/s", bytesPerSec / (1024f * 1024f))
        }
    }

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        private const val CHANNEL_ID = "socks_vpn_channel"
        private const val NOTIFICATION_ID = 1055
        private val ENGINE_LOCK = Any()

        fun startConnect(context: Context, profileId: Long) {
            val intent = Intent(context, SocksVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_PROFILE_ID, profileId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopDisconnect(context: Context) {
            val intent = Intent(context, SocksVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }
}
