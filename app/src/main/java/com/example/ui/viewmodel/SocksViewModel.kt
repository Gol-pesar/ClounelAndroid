package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.data.model.AppThemeMode
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import com.example.data.model.ProbeResult
import com.example.data.model.SocksProfile
import com.example.data.model.SplitTunnelMode
import com.example.data.model.TunnelSnapshot
import com.example.data.model.TunnelStatus
import com.example.data.repository.SocksRepository
import com.example.network.Socks5Probe
import com.example.network.Socks5UriParser
import com.example.vpn.SocksVpnService
import com.example.vpn.TunnelRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SocksViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SocksRepository(
        dao = AppDatabase.getInstance(application).socksProfileDao(),
        settingsStore = SettingsDataStore(application),
    )

    val profiles: StateFlow<List<SocksProfile>> = repository.profiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings(),
    )

    val selectedProfile: StateFlow<SocksProfile?> = profiles
        .combine(TunnelRuntimeState.snapshot) { list, snap ->
            val byActive = snap.activeProfileId?.let { activeId -> list.firstOrNull { it.id == activeId } }
            byActive ?: list.firstOrNull { it.isSelected } ?: list.firstOrNull()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    val tunnelSnapshot: StateFlow<TunnelSnapshot> = TunnelRuntimeState.snapshot
    val logs: StateFlow<List<LogEntry>> = TunnelRuntimeState.logs

    private val _isProbingProfileIds = MutableStateFlow<Set<Long>>(emptySet())
    val isProbingProfileIds: StateFlow<Set<Long>> = _isProbingProfileIds.asStateFlow()

    private val _isTestingAll = MutableStateFlow(false)
    val isTestingAll: StateFlow<Boolean> = _isTestingAll.asStateFlow()

    private val _deepProbeResult = MutableStateFlow<ProbeResult?>(null)
    val deepProbeResult: StateFlow<ProbeResult?> = _deepProbeResult.asStateFlow()

    private val _isRunningDeepProbe = MutableStateFlow(false)
    val isRunningDeepProbe: StateFlow<Boolean> = _isRunningDeepProbe.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    fun selectProfile(profile: SocksProfile) {
        viewModelScope.launch {
            repository.selectProfile(profile.id)
            if (tunnelSnapshot.value.status == TunnelStatus.CONNECTED) {
                SocksVpnService.startConnect(getApplication(), profile.id)
            }
        }
    }

    fun saveProfile(profile: SocksProfile) {
        viewModelScope.launch {
            val savedId = repository.saveProfile(profile)
            TunnelRuntimeState.addLog(
                LogLevel.INFO,
                "Saved profile '${profile.displayName}' (${profile.host}:${profile.port})",
            )
            if (tunnelSnapshot.value.status == TunnelStatus.CONNECTED &&
                tunnelSnapshot.value.activeProfileId == savedId
            ) {
                SocksVpnService.startConnect(getApplication(), savedId)
            }
        }
    }

    fun deleteProfile(profile: SocksProfile) {
        viewModelScope.launch {
            if (tunnelSnapshot.value.status == TunnelStatus.CONNECTED &&
                tunnelSnapshot.value.activeProfileId == profile.id
            ) {
                SocksVpnService.stopDisconnect(getApplication())
            }
            repository.deleteProfile(profile.id)
        }
    }

    fun importFromUri(rawText: String): Boolean {
        val parsed = Socks5UriParser.parse(rawText) ?: return false
        viewModelScope.launch {
            repository.saveProfile(parsed.copy(isSelected = true))
            TunnelRuntimeState.addLog(
                LogLevel.SUCCESS,
                "Imported SOCKS5 server ${parsed.host}:${parsed.port}",
            )
        }
        return true
    }

    fun startVpnForSelected() {
        viewModelScope.launch {
            val target = selectedProfile.value ?: repository.getSelectedOrFirstProfile() ?: return@launch
            SocksVpnService.startConnect(getApplication(), target.id)
        }
    }

    fun disconnectVpn() {
        SocksVpnService.stopDisconnect(getApplication())
    }

    fun probeProfile(profile: SocksProfile) {
        if (_isProbingProfileIds.value.contains(profile.id)) return
        viewModelScope.launch {
            _isProbingProfileIds.value += profile.id
            try {
                val result = Socks5Probe.runFullProbe(profile)
                repository.updateProbeStats(
                    id = profile.id,
                    latencyMs = result.totalLatencyMs ?: result.tcpLatencyMs,
                    exitIp = result.exitIp,
                    exitCountry = result.exitCountry,
                )
                TunnelRuntimeState.addLog(
                    if (result.success) LogLevel.SUCCESS else LogLevel.WARN,
                    if (result.success) {
                        "Probe ${profile.displayName}: ${result.totalLatencyMs}ms (${result.exitIp ?: "OK"})"
                    } else {
                        "Probe ${profile.displayName} failed: ${result.errorDetail}"
                    },
                )
            } finally {
                _isProbingProfileIds.value -= profile.id
            }
        }
    }

    fun testAllProfiles() {
        val list = profiles.value
        if (list.isEmpty() || _isTestingAll.value) return
        viewModelScope.launch {
            _isTestingAll.value = true
            try {
                list.map { profile ->
                    async {
                        _isProbingProfileIds.value += profile.id
                        try {
                            val latency = Socks5Probe.quickPing(profile)
                            repository.updateProbeStats(
                                id = profile.id,
                                latencyMs = latency,
                                exitIp = profile.lastExitIp,
                                exitCountry = profile.lastExitCountry,
                            )
                        } finally {
                            _isProbingProfileIds.value -= profile.id
                        }
                    }
                }.awaitAll()
            } finally {
                _isTestingAll.value = false
            }
        }
    }

    fun runDeepDiagnostics() {
        val target = selectedProfile.value ?: return
        if (_isRunningDeepProbe.value) return
        viewModelScope.launch {
            _isRunningDeepProbe.value = true
            try {
                val result = Socks5Probe.runFullProbe(target)
                _deepProbeResult.value = result
                repository.updateProbeStats(
                    id = target.id,
                    latencyMs = result.totalLatencyMs ?: result.tcpLatencyMs,
                    exitIp = result.exitIp,
                    exitCountry = result.exitCountry,
                )
            } finally {
                _isRunningDeepProbe.value = false
            }
        }
    }

    fun loadInstalledAppsIfNeeded() {
        if (_installedApps.value.isNotEmpty() || _isLoadingApps.value) return
        viewModelScope.launch {
            _isLoadingApps.value = true
            try {
                val apps = withContext(Dispatchers.IO) {
                    val pm = getApplication<Application>().packageManager
                    val selfPkg = getApplication<Application>().packageName
                    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                    val uniquePackages = linkedMapOf<String, InstalledAppInfo>()
                    for (resolveInfo in resolved) {
                        val appInfo = resolveInfo.activityInfo?.applicationInfo ?: continue
                        val pkg = appInfo.packageName
                        if (pkg == selfPkg || uniquePackages.containsKey(pkg)) continue
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                            .getOrDefault(pkg)
                        val icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()
                        uniquePackages[pkg] = InstalledAppInfo(
                            packageName = pkg,
                            label = label,
                            icon = icon,
                            isSystemApp = isSystem,
                        )
                    }
                    uniquePackages.values.sortedBy { it.label.lowercase() }
                }
                _installedApps.value = apps
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    fun setSplitTunnelMode(mode: SplitTunnelMode) {
        viewModelScope.launch { repository.setSplitTunnelMode(mode) }
    }

    fun toggleAppPackage(packageName: String) {
        viewModelScope.launch { repository.toggleAppPackage(packageName) }
    }

    fun clearSelectedPackages() {
        viewModelScope.launch { repository.clearSelectedPackages() }
    }

    fun setLanBypass(enabled: Boolean) {
        viewModelScope.launch { repository.setLanBypass(enabled) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun clearLogs() {
        TunnelRuntimeState.clearLogs()
    }

    fun logWarning(message: String) {
        TunnelRuntimeState.addLog(LogLevel.WARN, message)
    }
}
