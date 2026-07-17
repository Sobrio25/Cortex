package com.aiagents.app.data.diagnostics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import retrofit2.HttpException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class UserErrorCategory {
    NETWORK,
    TIMEOUT,
    AUTHENTICATION,
    RATE_LIMIT,
    SERVICE_UNAVAILABLE,
    PERMISSION,
    NOT_FOUND,
    REQUEST_TOO_LARGE,
    STORAGE,
    CONFIGURATION,
    EMPTY_RESPONSE,
    CANCELLED,
    UNKNOWN
}

data class UserFacingError(
    val category: UserErrorCategory,
    val code: String,
    val message: String,
    val retryable: Boolean
) {
    val displayMessage: String = "$message\nCódigo: $code"
}

/**
 * Converts implementation details into a short, actionable message. Raw provider messages are
 * deliberately never returned to the UI because they may contain URLs, request fragments or keys.
 */
object UserFacingErrorMapper {
    private val httpCodePattern = Regex("""(?<!\d)(401|403|404|408|413|429|500|502|503|504)(?!\d)""")

    fun map(error: Throwable, operation: String): UserFacingError {
        val chain = generateSequence(error) { it.cause }.take(8).toList()
        val normalizedMessage = chain.joinToString(" ") { it.message.orEmpty() }.lowercase()
        val httpCode = chain.filterIsInstance<HttpException>().firstOrNull()?.code()
            ?: httpCodePattern.find(normalizedMessage)?.value?.toIntOrNull()

        val category = when {
            chain.any { it is CancellationException && it !is TimeoutCancellationException } ->
                UserErrorCategory.CANCELLED
            chain.any { it is SocketTimeoutException || it is TimeoutCancellationException } ||
                httpCode == 408 || "timed out" in normalizedMessage || "timeout" in normalizedMessage ->
                UserErrorCategory.TIMEOUT
            chain.any { it is UnknownHostException } -> UserErrorCategory.NETWORK
            chain.any { it is ConnectException || it is SocketException } ||
                "connection refused" in normalizedMessage || "failed to connect" in normalizedMessage ||
                "unable to resolve host" in normalizedMessage -> UserErrorCategory.NETWORK
            httpCode == 401 || httpCode == 403 ||
                "api key" in normalizedMessage || "unauthorized" in normalizedMessage ||
                "not authorized" in normalizedMessage || "no está autorizado" in normalizedMessage ||
                "authentication" in normalizedMessage -> UserErrorCategory.AUTHENTICATION
            httpCode == 429 || "rate limit" in normalizedMessage || "too many requests" in normalizedMessage ->
                UserErrorCategory.RATE_LIMIT
            httpCode in setOf(500, 502, 503, 504) -> UserErrorCategory.SERVICE_UNAVAILABLE
            httpCode == 413 || "context length" in normalizedMessage ||
                "maximum context" in normalizedMessage || "request too large" in normalizedMessage ||
                "too many tokens" in normalizedMessage -> UserErrorCategory.REQUEST_TOO_LARGE
            chain.any { it is SecurityException } || "permission denied" in normalizedMessage ->
                UserErrorCategory.PERMISSION
            chain.any { it is FileNotFoundException } || httpCode == 404 ||
                "not found" in normalizedMessage -> UserErrorCategory.NOT_FOUND
            "no space left" in normalizedMessage || "insufficient storage" in normalizedMessage ||
                "disk full" in normalizedMessage || "database or disk is full" in normalizedMessage ->
                UserErrorCategory.STORAGE
            "api key" in normalizedMessage || "not configured" in normalizedMessage ||
                "no configurada" in normalizedMessage -> UserErrorCategory.CONFIGURATION
            "sin devolver una respuesta" in normalizedMessage || "empty response" in normalizedMessage ->
                UserErrorCategory.EMPTY_RESPONSE
            chain.any { it is IOException } -> UserErrorCategory.NETWORK
            else -> UserErrorCategory.UNKNOWN
        }

        return fromCategory(category, operation)
    }

    private fun fromCategory(category: UserErrorCategory, operation: String): UserFacingError {
        val operationCode = operation.uppercase()
            .replace(Regex("[^A-Z0-9]+"), "-")
            .trim('-')
            .take(20)
            .ifBlank { "APP" }
        val suffix = when (category) {
            UserErrorCategory.NETWORK -> "RED"
            UserErrorCategory.TIMEOUT -> "TIEMPO"
            UserErrorCategory.AUTHENTICATION -> "ACCESO"
            UserErrorCategory.RATE_LIMIT -> "LIMITE"
            UserErrorCategory.SERVICE_UNAVAILABLE -> "SERVICIO"
            UserErrorCategory.PERMISSION -> "PERMISO"
            UserErrorCategory.NOT_FOUND -> "NO-ENCONTRADO"
            UserErrorCategory.REQUEST_TOO_LARGE -> "MUY-GRANDE"
            UserErrorCategory.STORAGE -> "ESPACIO"
            UserErrorCategory.CONFIGURATION -> "CONFIG"
            UserErrorCategory.EMPTY_RESPONSE -> "SIN-RESPUESTA"
            UserErrorCategory.CANCELLED -> "CANCELADO"
            UserErrorCategory.UNKNOWN -> "INESPERADO"
        }
        val message = when (category) {
            UserErrorCategory.NETWORK ->
                "No pudimos conectarnos. Revisa tu internet y, si usas un servidor local, confirma que esté encendido."
            UserErrorCategory.TIMEOUT ->
                "La operación tardó demasiado. Inténtalo de nuevo en unos segundos."
            UserErrorCategory.AUTHENTICATION ->
                "La clave o sesión del proveedor no es válida. Revísala en Proveedores y vuelve a intentarlo."
            UserErrorCategory.RATE_LIMIT ->
                "El proveedor recibió demasiadas solicitudes. Espera un momento y vuelve a intentarlo."
            UserErrorCategory.SERVICE_UNAVAILABLE ->
                "El servicio no está disponible por el momento. Inténtalo de nuevo más tarde."
            UserErrorCategory.PERMISSION ->
                "Falta un permiso necesario. Concédelo en la app o en los ajustes del dispositivo."
            UserErrorCategory.NOT_FOUND ->
                "No encontramos el archivo, modelo o recurso solicitado. Comprueba que todavía exista."
            UserErrorCategory.REQUEST_TOO_LARGE ->
                "La conversación o el archivo es demasiado grande para este modelo. Reduce el contenido o compacta el contexto."
            UserErrorCategory.STORAGE ->
                "No hay espacio suficiente para terminar. Libera almacenamiento y vuelve a intentarlo."
            UserErrorCategory.CONFIGURATION ->
                "Falta configurar este servicio. Revisa el proveedor y sus credenciales."
            UserErrorCategory.EMPTY_RESPONSE ->
                "El modelo terminó sin enviar una respuesta. Puedes volver a intentarlo o elegir otro modelo."
            UserErrorCategory.CANCELLED -> "La operación fue cancelada."
            UserErrorCategory.UNKNOWN -> operationFallback(operation)
        }
        return UserFacingError(
            category = category,
            code = "$operationCode-$suffix",
            message = message,
            retryable = category in setOf(
                UserErrorCategory.NETWORK,
                UserErrorCategory.TIMEOUT,
                UserErrorCategory.RATE_LIMIT,
                UserErrorCategory.SERVICE_UNAVAILABLE,
                UserErrorCategory.EMPTY_RESPONSE,
                UserErrorCategory.UNKNOWN
            )
        )
    }

    private fun operationFallback(operation: String): String = when {
        "purchase" in operation || "billing" in operation ->
            "No pudimos completar la compra. No se realizó ningún cambio; inténtalo de nuevo."
        "sign_in" in operation || "oauth" in operation || "authorization" in operation ||
            "consent" in operation ->
            "No pudimos completar el acceso. Revisa tu cuenta y vuelve a intentarlo."
        "voice" in operation || "stt" in operation || "audio" in operation ->
            "No pudimos procesar el audio. Comprueba el micrófono y vuelve a intentarlo."
        "file" in operation || "model" in operation || "download" in operation ->
            "No pudimos procesar el archivo o la descarga. Comprueba el archivo y vuelve a intentarlo."
        "scheduled_task" in operation ->
            "No pudimos completar el cambio en la tarea programada. Revisa sus datos y vuelve a intentarlo."
        "memory" in operation ->
            "No pudimos completar el cambio en la memoria. Inténtalo de nuevo."
        "provider" in operation ->
            "No pudimos completar la configuración del proveedor. Revisa los datos e inténtalo de nuevo."
        "tool" in operation || "command" in operation || "calendar" in operation ->
            "La acción no pudo terminar. Revisa los permisos y vuelve a intentarlo."
        else -> "Algo salió mal al completar la solicitud. Inténtalo de nuevo."
    }
}
