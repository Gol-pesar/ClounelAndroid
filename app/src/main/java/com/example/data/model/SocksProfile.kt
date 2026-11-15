package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "socks_profiles")
data class SocksProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 1080,
    val username: String = "",
    val password: String = "",
    val udpMode: String = UDP_MODE_TCP,
    val useRemoteDns: Boolean = true,
    val customDns: String = "1.1.1.1",
    val enableIpv6: Boolean = false,
    val mtu: Int = 8500,
    val lastLatencyMs: Int? = null,
    val lastExitIp: String? = null,
    val lastExitCountry: String? = null,
    val isSelected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val hasAuth: Boolean
        get() = username.isNotBlank() || password.isNotBlank()

    val displayName: String
        get() = name.ifBlank { "$host:$port" }

    companion object {
        const val UDP_MODE_TCP = "tcp"
        const val UDP_MODE_UDP = "udp"
    }
}
