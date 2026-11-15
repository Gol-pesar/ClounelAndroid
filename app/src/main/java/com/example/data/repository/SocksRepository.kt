package com.example.data.repository

import com.example.data.local.SettingsDataStore
import com.example.data.local.SocksProfileDao
import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.data.model.AppThemeMode
import com.example.data.model.SocksProfile
import com.example.data.model.SplitTunnelMode
import kotlinx.coroutines.flow.Flow

class SocksRepository(
    private val dao: SocksProfileDao,
    private val settingsStore: SettingsDataStore,
) {
    val profiles: Flow<List<SocksProfile>> = dao.observeAllProfiles()
    val settings: Flow<AppSettings> = settingsStore.settingsFlow

    suspend fun getAllProfiles(): List<SocksProfile> = dao.getAllProfiles()

    suspend fun getProfileById(id: Long): SocksProfile? = dao.getProfileById(id)

    suspend fun getSelectedOrFirstProfile(): SocksProfile? {
        return dao.getSelectedProfile() ?: dao.getAllProfiles().firstOrNull()
    }

    suspend fun saveProfile(profile: SocksProfile): Long {
        val existing = dao.getAllProfiles()
        val shouldSelect = profile.isSelected || existing.isEmpty()
        val id = if (profile.id == 0L) {
            dao.insertProfile(profile.copy(isSelected = shouldSelect))
        } else {
            dao.updateProfile(profile)
            profile.id
        }
        if (shouldSelect) {
            dao.selectProfile(id)
        }
        return id
    }

    suspend fun selectProfile(id: Long) {
        dao.selectProfile(id)
    }

    suspend fun deleteProfile(id: Long) {
        val target = dao.getProfileById(id)
        dao.deleteProfileById(id)
        if (target?.isSelected == true) {
            dao.getAllProfiles().firstOrNull()?.let { next ->
                dao.selectProfile(next.id)
            }
        }
    }

    suspend fun updateProbeStats(
        id: Long,
        latencyMs: Int?,
        exitIp: String?,
        exitCountry: String?,
    ) {
        dao.updateProbeStats(id, latencyMs, exitIp, exitCountry)
    }

    suspend fun getSettingsSnapshot(): AppSettings = settingsStore.getSnapshot()

    suspend fun setSplitTunnelMode(mode: SplitTunnelMode) = settingsStore.setSplitTunnelMode(mode)

    suspend fun toggleAppPackage(packageName: String) {
        val current = settingsStore.getSnapshot().selectedPackages.toMutableSet()
        if (!current.add(packageName)) {
            current.remove(packageName)
        }
        settingsStore.setSelectedPackages(current)
    }

    suspend fun clearSelectedPackages() = settingsStore.setSelectedPackages(emptySet())

    suspend fun setLanBypass(enabled: Boolean) = settingsStore.setLanBypass(enabled)

    suspend fun setLanguage(language: AppLanguage) = settingsStore.setLanguage(language)

    suspend fun setThemeMode(themeMode: AppThemeMode) = settingsStore.setThemeMode(themeMode)
}
