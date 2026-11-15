package com.example.vpn

import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import com.example.data.model.SpeedSample
import com.example.data.model.TunnelSnapshot
import com.example.data.model.TunnelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

object TunnelRuntimeState {
    private const val MAX_LOGS = 200
    private const val MAX_SPEED_SAMPLES = 30

    private val logSeq = AtomicLong(1L)

    private val _snapshot = MutableStateFlow(TunnelSnapshot())
    val snapshot: StateFlow<TunnelSnapshot> = _snapshot.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun setConnecting(profileId: Long, profileName: String, endpoint: String) {
        _snapshot.update {
            TunnelSnapshot(
                status = TunnelStatus.CONNECTING,
                activeProfileId = profileId,
                activeProfileName = profileName,
                activeEndpoint = endpoint,
            )
        }
        addLog(LogLevel.INFO, "Connecting to $profileName ($endpoint)...")
    }

    fun setConnected(resolvedProxyIp: String) {
        val now = System.currentTimeMillis()
        _snapshot.update {
            it.copy(
                status = TunnelStatus.CONNECTED,
                connectedSinceMs = now,
                resolvedProxyIp = resolvedProxyIp,
                errorMessage = null,
            )
        }
        addLog(LogLevel.SUCCESS, "Tunnel active via $resolvedProxyIp")
    }

    fun updateTrafficStats(
        txPackets: Long,
        txBytes: Long,
        rxPackets: Long,
        rxBytes: Long,
        txSpeedBps: Long,
        rxSpeedBps: Long,
    ) {
        _snapshot.update { current ->
            if (current.status != TunnelStatus.CONNECTED) return@update current
            val history = (current.speedHistory + SpeedSample(rxSpeedBps, txSpeedBps))
                .takeLast(MAX_SPEED_SAMPLES)
            current.copy(
                txPackets = txPackets,
                txBytes = txBytes,
                rxPackets = rxPackets,
                rxBytes = rxBytes,
                txSpeedBps = txSpeedBps,
                rxSpeedBps = rxSpeedBps,
                speedHistory = history,
            )
        }
    }

    fun setDisconnecting() {
        _snapshot.update { it.copy(status = TunnelStatus.DISCONNECTING) }
        addLog(LogLevel.INFO, "Stopping tunnel...")
    }

    fun setDisconnected() {
        _snapshot.update { TunnelSnapshot(status = TunnelStatus.DISCONNECTED) }
        addLog(LogLevel.INFO, "Tunnel disconnected")
    }

    fun setError(message: String) {
        _snapshot.update {
            it.copy(
                status = TunnelStatus.ERROR,
                errorMessage = message,
                txSpeedBps = 0L,
                rxSpeedBps = 0L,
            )
        }
        addLog(LogLevel.ERROR, message)
    }

    fun addLog(level: LogLevel, message: String) {
        val entry = LogEntry(
            id = logSeq.getAndIncrement(),
            timestampMs = System.currentTimeMillis(),
            level = level,
            message = message,
        )
        _logs.update { (it + entry).takeLast(MAX_LOGS) }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
