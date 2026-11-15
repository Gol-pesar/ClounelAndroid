package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.data.model.AppThemeMode
import com.example.data.model.SplitTunnelMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "socksflow_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val SPLIT_MODE = stringPreferencesKey("split_mode")
        val SELECTED_PACKAGES = stringSetPreferencesKey("selected_packages")
        val LAN_BYPASS = booleanPreferencesKey("lan_bypass")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            splitTunnelMode = prefs[Keys.SPLIT_MODE]
                ?.let { runCatching { SplitTunnelMode.valueOf(it) }.getOrNull() }
                ?: SplitTunnelMode.ALL_APPS,
            selectedPackages = prefs[Keys.SELECTED_PACKAGES] ?: emptySet(),
            lanBypass = prefs[Keys.LAN_BYPASS] ?: true,
            language = prefs[Keys.LANGUAGE]
                ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.FA,
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.DARK,
        )
    }

    suspend fun getSnapshot(): AppSettings = settingsFlow.first()

    suspend fun setSplitTunnelMode(mode: SplitTunnelMode) {
        context.settingsDataStore.edit { it[Keys.SPLIT_MODE] = mode.name }
    }

    suspend fun setSelectedPackages(packages: Set<String>) {
        context.settingsDataStore.edit { it[Keys.SELECTED_PACKAGES] = packages }
    }

    suspend fun setLanBypass(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.LAN_BYPASS] = enabled }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setThemeMode(themeMode: AppThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }
}
