package com.aiagents.app.data.terminal

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** URL and address checks used by web_fetch to prevent access to local/private services. */
object WebUrlPolicy {
    private val dnsExecutor = ThreadPoolExecutor(
        0,
        4,
        30L,
        TimeUnit.SECONDS,
        SynchronousQueue(),
        { runnable -> Thread(runnable, "safe-web-dns").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    fun validateUrl(rawUrl: String): Result<HttpUrl> = runCatching {
        val url = rawUrl.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("URL inválida")
        require(url.scheme == "http" || url.scheme == "https") { "Solo se permiten URLs http/https" }
        require(url.username.isEmpty() && url.password.isEmpty()) { "No se permiten credenciales en la URL" }
        require(isAllowedHostname(url.host)) { "El host local o privado no está permitido" }
        url
    }

    fun isAllowedHostname(hostname: String): Boolean {
        val host = hostname.trim().trimEnd('.').lowercase()
        if (host.isBlank()) return false
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
            host.endsWith(".internal") || host.endsWith(".home") || host.endsWith(".lan")
        ) return false

        val literal = runCatching { parseLiteralAddress(host) }.getOrNull()
        return literal?.let(::isPublicAddress) ?: true
    }

    fun resolvePublic(hostname: String): List<InetAddress> {
        require(isAllowedHostname(hostname)) { "El host local o privado no está permitido" }
        val lookup = runCatching {
            dnsExecutor.submit<List<InetAddress>> { InetAddress.getAllByName(hostname).toList() }
        }.getOrElse { throw IllegalArgumentException("Hay demasiadas consultas DNS pendientes") }
        val addresses = try {
            lookup.get(5, TimeUnit.SECONDS)
        } catch (error: Exception) {
            lookup.cancel(true)
            throw IllegalArgumentException("La resolución DNS falló o excedió 5 segundos", error)
        }
        require(addresses.isNotEmpty()) { "El host no tiene direcciones válidas" }
        require(addresses.all(::isPublicAddress)) { "El host resuelve a una red local o privada" }
        return addresses
    }

    fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false

        val bytes = address.address
        if (address is Inet4Address && bytes.size == 4) {
            val a = bytes[0].toInt() and 0xff
            val b = bytes[1].toInt() and 0xff
            if (a == 0 || a == 10 || a == 127 || a >= 224) return false
            if (a == 100 && b in 64..127) return false // carrier-grade NAT
            if (a == 169 && b == 254) return false
            if (a == 172 && b in 16..31) return false
            if (a == 192 && b == 168) return false
            if (a == 192 && b == 0) return false // IETF protocol assignments
            if (a == 192 && b == 88 && (bytes[2].toInt() and 0xff) == 99) return false
            if (a == 198 && b in 18..19) return false // benchmark networks
            if (a == 198 && b == 51 && (bytes[2].toInt() and 0xff) == 100) return false
            if (a == 203 && b == 0 && (bytes[2].toInt() and 0xff) == 113) return false
        }
        if (address is Inet6Address) {
            // Only the currently allocated global-unicast block 2000::/3 is fetchable.
            val first = bytes.firstOrNull()?.toInt()?.and(0xff) ?: return false
            if (bytes.size == 16 && bytes.take(10).all { it.toInt() == 0 } &&
                bytes[10].toInt() == -1 && bytes[11].toInt() == -1
            ) {
                return isPublicAddress(InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
            }
            if (first !in 0x20..0x3f) return false
            // Documentation, Teredo/IETF assignments and 6to4 are not direct public targets.
            val second = bytes[1].toInt() and 0xff
            val third = bytes[2].toInt() and 0xff
            val fourth = bytes[3].toInt() and 0xff
            if (first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8) return false
            if (first == 0x20 && second == 0x01 && third <= 0x01) return false
            if (first == 0x20 && second == 0x02) return false
        }
        return true
    }

    private fun parseLiteralAddress(host: String): InetAddress? {
        val looksLikeIpv4 = host.matches(Regex("^[0-9.]+$"))
        val looksLikeIpv6 = ':' in host
        return if (looksLikeIpv4 || looksLikeIpv6) InetAddress.getByName(host) else null
    }
}
