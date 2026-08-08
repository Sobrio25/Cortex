package com.aiagents.app.data.auth

import com.aiagents.app.domain.model.ProviderType
import java.net.URI
import java.util.Locale

class UnsafeProviderEndpointException(message: String) : IllegalArgumentException(message)

/**
 * Prevents provider credentials from being paired with cleartext internet endpoints.
 *
 * The application still needs platform cleartext support for user-owned engines on the same
 * device or LAN. Only Ollama and LM Studio may use HTTP, and only with an explicitly local host.
 */
object ProviderEndpointPolicy {
    private val cleartextLocalProviders = setOf(
        ProviderType.OLLAMA,
        ProviderType.LM_STUDIO
    )

    fun validate(provider: ProviderType, input: String): String {
        val endpoint = input.trim()
        if (endpoint.isEmpty()) return endpoint

        val uri = runCatching { URI(endpoint) }.getOrNull()
            ?: throw UnsafeProviderEndpointException("La URL base no es válida")
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.trim('[', ']')?.lowercase(Locale.ROOT)

        if (scheme !in setOf("http", "https") || host.isNullOrBlank()) {
            throw UnsafeProviderEndpointException("La URL base debe usar HTTP o HTTPS e incluir un host")
        }
        if (uri.rawUserInfo != null) {
            throw UnsafeProviderEndpointException("La URL base no puede incluir usuario ni contraseña")
        }
        if (scheme == "https") return endpoint

        if (provider !in cleartextLocalProviders || !isLocalHost(host)) {
            throw UnsafeProviderEndpointException(
                "HTTP sin cifrar solo se permite para Ollama o LM Studio en este dispositivo o en la red local"
            )
        }
        return endpoint
    }

    internal fun isLocalHost(host: String): Boolean {
        val normalized = host.trim().trim('[', ']').lowercase(Locale.ROOT).trimEnd('.')
        if (normalized == "localhost" || normalized.endsWith(".localhost") || normalized.endsWith(".local")) {
            return true
        }
        if (normalized.contains(':')) {
            return normalized == "::1" ||
                normalized.startsWith("fc") ||
                normalized.startsWith("fd") ||
                normalized.startsWith("fe8") ||
                normalized.startsWith("fe9") ||
                normalized.startsWith("fea") ||
                normalized.startsWith("feb")
        }

        val octets = normalized.split('.').map { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it == null || it !in 0..255 }) return false
        val first = octets[0]!!
        val second = octets[1]!!
        return first == 10 ||
            first == 127 ||
            first == 192 && second == 168 ||
            first == 172 && second in 16..31 ||
            first == 169 && second == 254
    }
}
