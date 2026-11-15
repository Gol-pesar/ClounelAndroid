package com.example

import com.example.data.model.SocksProfile
import com.example.network.CidrUtils
import com.example.network.HevConfigGenerator
import com.example.network.Socks5UriParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun parseSocks5Uri_withCredentialsAndFragment() {
        val parsed = Socks5UriParser.parse("socks5://alice:s3cr3t@185.10.20.30:1080#Germany%20Node")
        assertNotNull(parsed)
        assertEquals("185.10.20.30", parsed?.host)
        assertEquals(1080, parsed?.port)
        assertEquals("alice", parsed?.username)
        assertEquals("s3cr3t", parsed?.password)
        assertEquals("Germany Node", parsed?.name)
    }

    @Test
    fun parseTelegramSocksLink() {
        val parsed = Socks5UriParser.parse("tg://socks?server=proxy.example.org&port=9050&user=u1&pass=p2")
        assertNotNull(parsed)
        assertEquals("proxy.example.org", parsed?.host)
        assertEquals(9050, parsed?.port)
        assertEquals("u1", parsed?.username)
        assertEquals("p2", parsed?.password)
    }

    @Test
    fun parseColonFormat_withAndWithoutAuth() {
        val noAuth = Socks5UriParser.parse("1.2.3.4:1080")
        assertEquals("1.2.3.4", noAuth?.host)
        assertEquals(1080, noAuth?.port)
        assertEquals("", noAuth?.username)

        val withAuth = Socks5UriParser.parse("1.2.3.4:8080:myuser:mypass")
        assertEquals("1.2.3.4", withAuth?.host)
        assertEquals(8080, withAuth?.port)
        assertEquals("myuser", withAuth?.username)
        assertEquals("mypass", withAuth?.password)

        assertNull(Socks5UriParser.parse("invalid-host:99999"))
    }

    @Test
    fun cidrLanBypass_excludesPrivateAndKeepsPublicAndMapDns() {
        val blocks = CidrUtils.publicRoutesExcludingLan()
        assertTrue(blocks.isNotEmpty())

        fun isRouted(ip: String): Boolean {
            val target = CidrUtils.ipv4ToUInt(ip)
            return blocks.any { block ->
                val (start, end) = CidrUtils.parseCidr("${block.address}/${block.prefixLength}")
                target in start..end
            }
        }

        // Private / LAN / Loopback must NOT be routed
        assertFalse(isRouted("192.168.1.1"))
        assertFalse(isRouted("10.0.0.1"))
        assertFalse(isRouted("172.16.5.4"))
        assertFalse(isRouted("127.0.0.1"))
        assertFalse(isRouted("169.254.1.1"))

        // Public IPs and MapDNS range MUST be routed
        assertTrue(isRouted("1.1.1.1"))
        assertTrue(isRouted("8.8.8.8"))
        assertTrue(isRouted(HevConfigGenerator.MAPDNS_ADDRESS))
        assertTrue(isRouted("198.19.12.34"))
    }

    @Test
    fun hevConfigGenerator_containsSocks5AndMapDns() {
        val profile = SocksProfile(
            name = "Test",
            host = "1.2.3.4",
            port = 1080,
            username = "bob",
            password = "pw'1",
            udpMode = SocksProfile.UDP_MODE_TCP,
            useRemoteDns = true,
        )
        val yaml = HevConfigGenerator.generateYaml(profile, "1.2.3.4")
        assertTrue(yaml.contains("address: '1.2.3.4'"))
        assertTrue(yaml.contains("port: 1080"))
        assertTrue(yaml.contains("udp: 'tcp'"))
        assertTrue(yaml.contains("password: 'pw''1'"))
        assertTrue(yaml.contains("mapdns:"))
        assertTrue(yaml.contains("address: 198.18.0.2"))
    }
}
