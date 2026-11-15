package com.example.network

import com.example.data.model.SocksProfile

object HevConfigGenerator {

    const val TUN_IPV4 = "198.18.0.1"
    const val TUN_IPV6 = "fc00::1"
    const val MAPDNS_ADDRESS = "198.18.0.2"
    const val MAPDNS_NETWORK = "198.19.0.0"
    const val MAPDNS_NETMASK = "255.255.0.0"

    fun generateYaml(
        profile: SocksProfile,
        resolvedProxyAddress: String,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("tunnel:")
        sb.appendLine("  name: tun0")
        sb.appendLine("  mtu: ${profile.mtu.coerceIn(1280, 9000)}")
        sb.appendLine("  multi-queue: false")
        sb.appendLine("  ipv4: $TUN_IPV4")
        if (profile.enableIpv6) {
            sb.appendLine("  ipv6: '$TUN_IPV6'")
        }
        sb.appendLine()
        sb.appendLine("socks5:")
        sb.appendLine("  port: ${profile.port}")
        sb.appendLine("  address: ${yamlQuote(resolvedProxyAddress)}")
        sb.appendLine("  udp: '${if (profile.udpMode == SocksProfile.UDP_MODE_UDP) "udp" else "tcp"}'")
        if (profile.username.isNotBlank()) {
            sb.appendLine("  username: ${yamlQuote(profile.username)}")
        }
        if (profile.password.isNotBlank()) {
            sb.appendLine("  password: ${yamlQuote(profile.password)}")
        }
        if (profile.useRemoteDns) {
            sb.appendLine()
            sb.appendLine("mapdns:")
            sb.appendLine("  address: $MAPDNS_ADDRESS")
            sb.appendLine("  port: 53")
            sb.appendLine("  network: $MAPDNS_NETWORK")
            sb.appendLine("  netmask: $MAPDNS_NETMASK")
            sb.appendLine("  cache-size: 10000")
        }
        sb.appendLine()
        sb.appendLine("misc:")
        sb.appendLine("  connect-timeout: 15000")
        sb.appendLine("  tcp-read-write-timeout: 300000")
        sb.appendLine("  udp-read-write-timeout: 60000")
        sb.appendLine("  log-file: stderr")
        sb.appendLine("  log-level: warn")
        return sb.toString()
    }

    private fun yamlQuote(value: String): String {
        val escaped = value.replace("'", "''")
        return "'$escaped'"
    }
}
