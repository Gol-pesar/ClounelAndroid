package com.example.data.model

enum class SplitTunnelMode {
    ALL_APPS,
    BYPASS_SELECTED,
    ONLY_SELECTED,
}

enum class AppLanguage {
    FA,
    EN,
}

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM,
}

data class AppSettings(
    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.ALL_APPS,
    val selectedPackages: Set<String> = emptySet(),
    val lanBypass: Boolean = true,
    val language: AppLanguage = AppLanguage.FA,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
)
