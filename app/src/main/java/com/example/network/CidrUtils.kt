package com.example.network

data class CidrBlock(
    val address: String,
    val prefixLength: Int,
)

object CidrUtils {
    private val lanExcludedRanges = listOf(
        "0.0.0.0/8",
        "10.0.0.0/8",
        "100.64.0.0/10",
        "127.0.0.0/8",
        "169.254.0.0/16",
        "172.16.0.0/12",
        "192.0.0.0/24",
        "192.0.2.0/24",
        "192.168.0.0/16",
        "224.0.0.0/4",
        "240.0.0.0/4",
    )

    fun ipv4ToUInt(ip: String): UInt {
        val parts = ip.trim().split('.')
        require(parts.size == 4) { "Invalid IPv4: $ip" }
        var result = 0u
        for (part in parts) {
            val octet = part.toUInt()
            require(octet <= 255u) { "Invalid octet in IPv4: $ip" }
            result = (result shl 8) or octet
        }
        return result
    }

    fun uIntToIpv4(value: UInt): String {
        val b1 = (value shr 24) and 0xFFu
        val b2 = (value shr 16) and 0xFFu
        val b3 = (value shr 8) and 0xFFu
        val b4 = value and 0xFFu
        return "$b1.$b2.$b3.$b4"
    }

    fun parseCidr(cidr: String): Pair<UInt, UInt> {
        val (ipStr, prefixStr) = cidr.split('/')
        val prefix = prefixStr.toInt()
        require(prefix in 0..32) { "Invalid prefix: $prefix" }
        val ip = ipv4ToUInt(ipStr)
        val mask = if (prefix == 0) 0u else (0xFFFFFFFFu shl (32 - prefix))
        val start = ip and mask
        val end = start or mask.inv()
        return start to end
    }

    /**
     * Computes exact non-overlapping IPv4 CIDR blocks covering 0.0.0.0/0 minus private/LAN ranges.
     */
    fun publicRoutesExcludingLan(): List<CidrBlock> {
        val excluded = lanExcludedRanges
            .map { parseCidr(it) }
            .sortedBy { it.first }

        // Merge overlapping excluded intervals
        val merged = mutableListOf<Pair<ULong, ULong>>()
        for ((s, e) in excluded) {
            val start = s.toULong()
            val end = e.toULong()
            if (merged.isEmpty() || start > merged.last().second + 1uL) {
                merged.add(start to end)
            } else {
                val last = merged.removeAt(merged.lastIndex)
                merged.add(last.first to maxOf(last.second, end))
            }
        }

        val allowedIntervals = mutableListOf<Pair<ULong, ULong>>()
        var cursor = 0uL
        val maxIp = 0xFFFFFFFFuL
        for ((exStart, exEnd) in merged) {
            if (cursor < exStart) {
                allowedIntervals.add(cursor to (exStart - 1uL))
            }
            cursor = maxOf(cursor, exEnd + 1uL)
        }
        if (cursor <= maxIp) {
            allowedIntervals.add(cursor to maxIp)
        }

        val blocks = mutableListOf<CidrBlock>()
        for ((start, end) in allowedIntervals) {
            rangeToCidrs(start, end, blocks)
        }
        return blocks
    }

    private fun rangeToCidrs(startIp: ULong, endIp: ULong, out: MutableList<CidrBlock>) {
        var current = startIp
        while (current <= endIp) {
            var maxSizePrefix = 32
            while (maxSizePrefix > 0) {
                val nextPrefix = maxSizePrefix - 1
                val blockSize = 1uL shl (32 - nextPrefix)
                val mask = (0xFFFFFFFFuL shl (32 - nextPrefix)) and 0xFFFFFFFFuL
                if ((current and mask) != current) break
                if (current + blockSize - 1uL > endIp) break
                maxSizePrefix = nextPrefix
            }
            out.add(CidrBlock(uIntToIpv4(current.toUInt()), maxSizePrefix))
            val step = 1uL shl (32 - maxSizePrefix)
            if (current + step - 1uL >= endIp) break
            current += step
        }
    }
}
