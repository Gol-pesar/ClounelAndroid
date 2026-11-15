package com.example.data.model

import android.graphics.drawable.Drawable

enum class TunnelStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}

data class SpeedSample(
    val rxBytesPerSec: Long,
    val txBytesPerSec: Long,
    val timestampMs: Long = System.currentTimeMillis(),
)

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR,
}

data class LogEntry(
    val id: Long,
    val timestampMs: Long,
    val level: LogLevel,
    val message: String,
)

data class TunnelSnapshot(
    val status: TunnelStatus = TunnelStatus.DISCONNECTED,
    val activeProfileId: Long? = null,
    val activeProfileName: String = "",
    val activeEndpoint: String = "",
    val connectedSinceMs: Long? = null,
    val txBytes: Long = 0L,
    val rxBytes: Long = 0L,
    val txPackets: Long = 0L,
    val rxPackets: Long = 0L,
    val txSpeedBps: Long = 0L,
    val rxSpeedBps: Long = 0L,
    val speedHistory: List<SpeedSample> = emptyList(),
    val errorMessage: String? = null,
    val resolvedProxyIp: String? = null,
)

enum class ProbeStepStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
}

data class ProbeStep(
    val key: String,
    val status: ProbeStepStatus,
    val detail: String = "",
)

data class ProbeResult(
    val profileId: Long,
    val success: Boolean,
    val tcpLatencyMs: Int? = null,
    val totalLatencyMs: Int? = null,
    val exitIp: String? = null,
    val exitCountry: String? = null,
    val errorDetail: String? = null,
    val steps: List<ProbeStep> = emptyList(),
)

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
)
