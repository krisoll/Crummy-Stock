package com.example.stockapplication.data.transfer

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Finds the phone's own address on the local Wi-Fi network, so it can be typed into the desktop app.
 *
 * ## Why not `WifiManager`
 *
 * `WifiManager.getConnectionInfo().ipAddress` is deprecated, returns an int in host byte order that
 * is easy to misread, and reports nothing at all on a device connected by Ethernet, a hotspot, or
 * with Wi-Fi disabled but a USB tether up. Enumerating the interfaces answers "what address would
 * another machine on this network use to reach me?" regardless of how the phone is connected.
 *
 * ## Why IPv4 only
 *
 * Link-local IPv6 addresses are long, awkward to type and frequently blocked; a shop network that
 * matters here is IPv4. A device with only IPv6 will show "not found" rather than an address nobody
 * can read out correctly.
 */
object LocalAddress {

    /**
     * The best guess at the device's LAN address, or null when it has none.
     *
     * Prefers a private address (192.168.x, 10.x, 172.16–31.x) because those are what a home or shop
     * router hands out; a public address means the phone is on mobile data, where nothing on the
     * local network could reach it anyway.
     */
    fun wifiAddress(): String? {
        val candidates = runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .filter { it.isUp && !it.isLoopback }
                // Wi-Fi and Ethernet first, then anything else. A VPN or mobile-data interface
                // can be just as "private" as Wi-Fi (10.x is used by both), but its address is
                // not reachable from the computer, so showing it would send the user chasing a
                // correct-looking address that cannot work.
                .sortedBy { rank(it.name) }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress }
                .mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())

        return candidates.firstOrNull { it.isPrivate() } ?: candidates.firstOrNull()
    }

    /**
     * Ordering for [wifiAddress]: 0 = a real Wi-Fi/Ethernet interface, 2 = a tunnel or mobile
     * interface, 1 = anything else. The address is still accepted from a lower-ranked interface
     * when no better one exists, because a phone on USB tethering is better than "not found".
     */
    private fun rank(name: String): Int {
        val n = name.lowercase()
        return when {
            n.startsWith("wlan") ||
                n.startsWith("eth") ||
                n.startsWith("ap") ||
                n.startsWith("swlan") -> 0
            n.startsWith("tun") ||
                n.startsWith("tap") ||
                n.startsWith("ppp") ||
                n.startsWith("rmnet") ||
                n.startsWith("dummy") ||
                n.startsWith("v4-") ||
                n.startsWith("ipsec") -> 2
            else -> 1
        }
    }

    /**
     * True for the RFC 1918 ranges a router hands out.
     *
     * Deliberately simple string matching: these addresses always come from
     * [Inet4Address.hostAddress], so the dotted form and leading octet are reliable.
     */
    private fun String.isPrivate(): Boolean =
        startsWith("192.168.") ||
            startsWith("10.") ||
            (startsWith("172.") && substringAfter("172.").substringBefore('.').toIntOrNull()
                ?.let { it in 16..31 } == true)
}
