package com.example.network

import android.util.Base64
import com.example.data.model.SocksProfile
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Socks5UriParser {

    fun parse(rawInput: String): SocksProfile? {
        val input = rawInput.trim()
        if (input.isEmpty()) return null

        // 1. Telegram / t.me socks link: tg://socks?server=...&port=...&user=...&pass=...
        if (input.startsWith("tg://socks", ignoreCase = true) ||
            input.contains("://t.me/socks", ignoreCase = true) ||
            input.contains("://telegram.me/socks", ignoreCase = true)
        ) {
            return parseQueryStyle(input)
        }

        // 2. Standard URI: socks5://... or socks://...
        if (input.startsWith("socks5://", ignoreCase = true) ||
            input.startsWith("socks://", ignoreCase = true)
        ) {
            return parseSocksUri(input)
        }

        // 3. Colon-delimited proxy list: host:port or host:port:user:pass
        return parseColonFormat(input)
    }

    private fun parseQueryStyle(input: String): SocksProfile? {
        val uri = runCatching { URI(input) }.getOrNull() ?: return null
        val query = uri.rawQuery ?: return null
        val params = query.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else {
                val k = decode(pair.substring(0, idx)).lowercase()
                val v = decode(pair.substring(idx + 1))
                k to v
            }
        }.toMap()

        val host = params["server"]?.trim().orEmpty()
        val port = params["port"]?.toIntOrNull() ?: return null
        if (host.isEmpty() || port !in 1..65535) return null

        val user = params["user"].orEmpty()
        val pass = params["pass"].orEmpty()
        val label = uri.fragment?.let { decode(it) }?.ifBlank { null } ?: host

        return SocksProfile(
            name = label,
            host = host,
            port = port,
            username = user,
            password = pass,
        )
    }

    private fun parseSocksUri(input: String): SocksProfile? {
        val normalized = if (input.startsWith("socks://", ignoreCase = true)) {
            "socks5://" + input.substring("socks://".length)
        } else {
            input
        }

        val uri = runCatching { URI(normalized) }.getOrNull()
        if (uri != null && !uri.host.isNullOrBlank() && uri.port in 1..65535) {
            val userInfo = uri.rawUserInfo.orEmpty()
            val (user, pass) = decodeUserInfo(userInfo)
            val label = uri.fragment?.let { decode(it) }?.ifBlank { null } ?: uri.host
            return SocksProfile(
                name = label,
                host = uri.host,
                port = uri.port,
                username = user,
                password = pass,
            )
        }

        // Fallback manual parse for base64 userinfo without '@' decoding quirks
        val withoutScheme = normalized.substringAfter("://")
        val fragment = withoutScheme.substringAfter("#", "").let { decode(it) }
        val authority = withoutScheme.substringBefore("#").substringBefore("/")
        val atIdx = authority.lastIndexOf('@')
        val credsPart = if (atIdx >= 0) authority.substring(0, atIdx) else ""
        val hostPortPart = if (atIdx >= 0) authority.substring(atIdx + 1) else authority

        val (host, port) = splitHostPort(hostPortPart) ?: return null
        val (user, pass) = decodeUserInfo(credsPart)
        return SocksProfile(
            name = fragment.ifBlank { host },
            host = host,
            port = port,
            username = user,
            password = pass,
        )
    }

    private fun parseColonFormat(input: String): SocksProfile? {
        val line = input.lineSequence().firstOrNull()?.trim().orEmpty()
        if (line.isEmpty()) return null
        val parts = line.split(':')
        return when (parts.size) {
            2 -> {
                val host = parts[0].trim()
                val port = parts[1].trim().toIntOrNull() ?: return null
                if (host.isEmpty() || port !in 1..65535) return null
                SocksProfile(name = host, host = host, port = port)
            }
            4 -> {
                val host = parts[0].trim()
                val port = parts[1].trim().toIntOrNull() ?: return null
                if (host.isEmpty() || port !in 1..65535) return null
                SocksProfile(
                    name = host,
                    host = host,
                    port = port,
                    username = parts[2].trim(),
                    password = parts[3].trim(),
                )
            }
            else -> null
        }
    }

    private fun splitHostPort(hostPort: String): Pair<String, Int>? {
        if (hostPort.startsWith("[")) {
            val closeIdx = hostPort.indexOf(']')
            if (closeIdx <= 1) return null
            val host = hostPort.substring(1, closeIdx)
            val portStr = hostPort.substring(closeIdx + 1).removePrefix(":")
            val port = portStr.toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return host to port
        }
        val colonIdx = hostPort.lastIndexOf(':')
        if (colonIdx <= 0) return null
        val host = hostPort.substring(0, colonIdx).trim()
        val port = hostPort.substring(colonIdx + 1).trim().toIntOrNull() ?: return null
        if (host.isEmpty() || port !in 1..65535) return null
        return host to port
    }

    private fun decodeUserInfo(rawUserInfo: String): Pair<String, String> {
        if (rawUserInfo.isBlank()) return "" to ""
        if (rawUserInfo.contains(':')) {
            val idx = rawUserInfo.indexOf(':')
            return decode(rawUserInfo.substring(0, idx)) to decode(rawUserInfo.substring(idx + 1))
        }
        // Check if Base64 encoded "user:pass"
        val decodedB64 = runCatching {
            val bytes = Base64.decode(rawUserInfo, Base64.URL_SAFE or Base64.NO_WRAP)
            String(bytes, StandardCharsets.UTF_8)
        }.getOrNull()
        if (decodedB64 != null && decodedB64.contains(':')) {
            val idx = decodedB64.indexOf(':')
            return decodedB64.substring(0, idx) to decodedB64.substring(idx + 1)
        }
        return decode(rawUserInfo) to ""
    }

    fun toUri(profile: SocksProfile): String {
        val creds = if (profile.hasAuth) {
            "${encode(profile.username)}:${encode(profile.password)}@"
        } else {
            ""
        }
        val formattedHost = if (profile.host.contains(':') && !profile.host.startsWith("[")) {
            "[${profile.host}]"
        } else {
            profile.host
        }
        val fragment = if (profile.name.isNotBlank()) "#${encode(profile.name)}" else ""
        return "socks5://$creds$formattedHost:${profile.port}$fragment"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
}
