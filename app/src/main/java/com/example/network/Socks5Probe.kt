package com.example.network

import com.example.data.model.ProbeResult
import com.example.data.model.ProbeStep
import com.example.data.model.ProbeStepStatus
import com.example.data.model.SocksProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Pure Kotlin RFC 1928 / RFC 1929 SOCKS5 protocol tester & diagnostic probe.
 */
object Socks5Probe {

    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 7000

    suspend fun runFullProbe(profile: SocksProfile): ProbeResult = withContext(Dispatchers.IO) {
        val steps = mutableListOf(
            ProbeStep("dns", ProbeStepStatus.PENDING),
            ProbeStep("tcp", ProbeStepStatus.PENDING),
            ProbeStep("auth", ProbeStepStatus.PENDING),
            ProbeStep("http", ProbeStepStatus.PENDING),
        )

        var resolvedIp: String
        try {
            val address = InetAddress.getByName(profile.host)
            resolvedIp = address.hostAddress ?: profile.host
            steps[0] = ProbeStep("dns", ProbeStepStatus.PASSED, resolvedIp)
        } catch (e: Exception) {
            steps[0] = ProbeStep("dns", ProbeStepStatus.FAILED, e.message ?: "DNS error")
            return@withContext ProbeResult(
                profileId = profile.id,
                success = false,
                errorDetail = "DNS resolution failed: ${e.message}",
                steps = steps,
            )
        }

        val socket = Socket()
        var tcpMs = 0
        try {
            val totalStartNs = System.nanoTime()
            val tcpStartNs = System.nanoTime()
            socket.soTimeout = READ_TIMEOUT_MS
            socket.connect(InetSocketAddress(resolvedIp, profile.port), CONNECT_TIMEOUT_MS)
            tcpMs = ((System.nanoTime() - tcpStartNs) / 1_000_000L).toInt().coerceAtLeast(1)
            steps[1] = ProbeStep("tcp", ProbeStepStatus.PASSED, "$tcpMs ms")

            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            // RFC 1928 Greeting + RFC 1929 Username/Password Auth
            negotiateSocks5(profile, input, output)
            val authLabel = if (profile.hasAuth) "RFC 1929 User/Pass OK" else "No-Auth OK"
            steps[2] = ProbeStep("auth", ProbeStepStatus.PASSED, authLabel)

            // Connect through SOCKS5 to ip-api.com:80 for Exit IP & Country check
            val targetHost = "ip-api.com"
            sendConnectDomain(input, output, targetHost, 80)

            val httpReq = buildString {
                append("GET /json/?fields=status,country,query HTTP/1.1\r\n")
                append("Host: $targetHost\r\n")
                append("User-Agent: SocksFlow/1.0\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(httpReq.toByteArray(StandardCharsets.US_ASCII))
            output.flush()

            val responseText = readHttpResponse(input)
            val totalMs = ((System.nanoTime() - totalStartNs) / 1_000_000L).toInt().coerceAtLeast(1)
            val body = responseText.substringAfter("\r\n\r\n", "")

            var exitIp: String? = null
            var exitCountry: String? = null
            if (body.trimStart().startsWith("{")) {
                val json = JSONObject(body)
                if (json.optString("status") == "success") {
                    exitIp = json.optString("query").ifBlank { null }
                    exitCountry = json.optString("country").ifBlank { null }
                }
            }

            if (responseText.startsWith("HTTP/1.")) {
                val detail = listOfNotNull(exitIp, exitCountry).joinToString(" • ").ifBlank { "${totalMs} ms" }
                steps[3] = ProbeStep("http", ProbeStepStatus.PASSED, detail)
                return@withContext ProbeResult(
                    profileId = profile.id,
                    success = true,
                    tcpLatencyMs = tcpMs,
                    totalLatencyMs = totalMs,
                    exitIp = exitIp,
                    exitCountry = exitCountry,
                    steps = steps,
                )
            } else {
                steps[3] = ProbeStep("http", ProbeStepStatus.FAILED, "Empty HTTP response")
                return@withContext ProbeResult(
                    profileId = profile.id,
                    success = false,
                    tcpLatencyMs = tcpMs,
                    errorDetail = "Tunnel connected, but HTTP probe returned empty response",
                    steps = steps,
                )
            }
        } catch (e: SocksAuthException) {
            steps[2] = ProbeStep("auth", ProbeStepStatus.FAILED, e.message ?: "Auth rejected")
            return@withContext ProbeResult(
                profileId = profile.id,
                success = false,
                tcpLatencyMs = if (tcpMs > 0) tcpMs else null,
                errorDetail = e.message,
                steps = steps,
            )
        } catch (e: SocksConnectException) {
            steps[3] = ProbeStep("http", ProbeStepStatus.FAILED, e.message ?: "Connect failed")
            return@withContext ProbeResult(
                profileId = profile.id,
                success = false,
                tcpLatencyMs = if (tcpMs > 0) tcpMs else null,
                errorDetail = e.message,
                steps = steps,
            )
        } catch (e: Exception) {
            val failedIdx = if (steps[1].status != ProbeStepStatus.PASSED) 1 else 3
            steps[failedIdx] = ProbeStep(steps[failedIdx].key, ProbeStepStatus.FAILED, e.message ?: "Error")
            return@withContext ProbeResult(
                profileId = profile.id,
                success = false,
                tcpLatencyMs = if (tcpMs > 0) tcpMs else null,
                errorDetail = e.message ?: "Connection failed",
                steps = steps,
            )
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * Quick TCP + SOCKS5 handshake + HTTP 204 latency check (used for "Test All" servers).
     */
    suspend fun quickPing(profile: SocksProfile): Int? = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            val startNs = System.nanoTime()
            val address = InetAddress.getByName(profile.host)
            socket.soTimeout = 5000
            socket.connect(InetSocketAddress(address, profile.port), 4500)

            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            negotiateSocks5(profile, input, output)
            sendConnectDomain(input, output, "cp.cloudflare.com", 80)

            val req = "HEAD /generate_204 HTTP/1.1\r\nHost: cp.cloudflare.com\r\nConnection: close\r\n\r\n"
            output.write(req.toByteArray(StandardCharsets.US_ASCII))
            output.flush()

            val firstLine = readLine(input)
            if (!firstLine.startsWith("HTTP/1.")) return@withContext null
            ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { socket.close() }
        }
    }

    internal fun negotiateSocks5(profile: SocksProfile, input: InputStream, output: OutputStream) {
        val methods = if (profile.hasAuth) {
            byteArrayOf(0x05, 0x02, 0x00, 0x02) // VER=5, NMETHODS=2, NO_AUTH(0), USER_PASS(2)
        } else {
            byteArrayOf(0x05, 0x01, 0x00) // VER=5, NMETHODS=1, NO_AUTH(0)
        }
        output.write(methods)
        output.flush()

        val resp = readExact(input, 2)
        if (resp[0].toInt() != 0x05) {
            throw SocksAuthException("Invalid SOCKS version: ${resp[0].toInt() and 0xFF}")
        }
        when (resp[1].toInt() and 0xFF) {
            0x00 -> Unit // No auth required
            0x02 -> {
                if (!profile.hasAuth) {
                    throw SocksAuthException("Server requires username/password")
                }
                val uBytes = profile.username.toByteArray(StandardCharsets.UTF_8)
                val pBytes = profile.password.toByteArray(StandardCharsets.UTF_8)
                if (uBytes.size > 255 || pBytes.size > 255) {
                    throw SocksAuthException("Credentials exceed 255 bytes")
                }
                val authPacket = ByteArray(3 + uBytes.size + pBytes.size)
                authPacket[0] = 0x01 // Subnegotiation version
                authPacket[1] = uBytes.size.toByte()
                System.arraycopy(uBytes, 0, authPacket, 2, uBytes.size)
                authPacket[2 + uBytes.size] = pBytes.size.toByte()
                System.arraycopy(pBytes, 0, authPacket, 3 + uBytes.size, pBytes.size)
                output.write(authPacket)
                output.flush()

                val authResp = readExact(input, 2)
                if (authResp[1].toInt() != 0x00) {
                    throw SocksAuthException("Authentication failed (status=${authResp[1].toInt() and 0xFF})")
                }
            }
            0xFF -> throw SocksAuthException("No acceptable authentication method")
            else -> throw SocksAuthException("Unsupported auth method: ${resp[1].toInt() and 0xFF}")
        }
    }

    internal fun sendConnectDomain(
        input: InputStream,
        output: OutputStream,
        domain: String,
        port: Int,
    ) {
        val hostBytes = domain.toByteArray(StandardCharsets.US_ASCII)
        val req = ByteArray(7 + hostBytes.size)
        req[0] = 0x05 // VER
        req[1] = 0x01 // CMD = CONNECT
        req[2] = 0x00 // RSV
        req[3] = 0x03 // ATYP = DOMAINNAME
        req[4] = hostBytes.size.toByte()
        System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
        req[5 + hostBytes.size] = ((port ushr 8) and 0xFF).toByte()
        req[6 + hostBytes.size] = (port and 0xFF).toByte()
        output.write(req)
        output.flush()

        val header = readExact(input, 4)
        if (header[0].toInt() != 0x05) {
            throw SocksConnectException("Invalid SOCKS5 reply version")
        }
        val rep = header[1].toInt() and 0xFF
        if (rep != 0x00) {
            throw SocksConnectException(replyCodeMessage(rep))
        }
        // Consume BND.ADDR + BND.PORT
        when (header[3].toInt() and 0xFF) {
            0x01 -> readExact(input, 4 + 2) // IPv4
            0x03 -> {
                val len = readExact(input, 1)[0].toInt() and 0xFF
                readExact(input, len + 2)
            }
            0x04 -> readExact(input, 16 + 2) // IPv6
            else -> throw SocksConnectException("Unknown address type in reply")
        }
    }

    private fun replyCodeMessage(code: Int): String = when (code) {
        0x01 -> "General SOCKS server failure"
        0x02 -> "Connection not allowed by ruleset"
        0x03 -> "Network unreachable"
        0x04 -> "Host unreachable"
        0x05 -> "Connection refused"
        0x06 -> "TTL expired"
        0x07 -> "Command not supported"
        0x08 -> "Address type not supported"
        else -> "SOCKS5 reply error ($code)"
    }

    private fun readExact(input: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buf, offset, count - offset)
            if (read == -1) throw IOException("Unexpected EOF from SOCKS5 server")
            offset += read
        }
        return buf
    }

    private fun readLine(input: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1 || b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
            if (sb.length > 4096) break
        }
        return sb.toString()
    }

    private fun readHttpResponse(input: InputStream): String {
        val bytes = ByteArray(8192)
        val sb = StringBuilder()
        while (sb.length < 16384) {
            val read = input.read(bytes)
            if (read <= 0) break
            sb.append(String(bytes, 0, read, StandardCharsets.UTF_8))
        }
        return sb.toString()
    }

    class SocksAuthException(message: String) : IOException(message)
    class SocksConnectException(message: String) : IOException(message)
}
