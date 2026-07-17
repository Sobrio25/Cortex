package com.aiagents.app.data.diagnostics

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/** Defense-in-depth redaction for any diagnostic string before it reaches Logcat. */
object SecureDiagnosticRedactor {
    private const val REDACTED = "[REDACTED]"

    private val sensitiveKeys = setOf(
        "authorization", "apikey", "api_key", "api-key", "access_token", "accesstoken",
        "refresh_token", "refreshtoken", "client_secret", "clientsecret", "password",
        "prompt", "system_prompt", "systemprompt", "content", "messages", "arguments"
    )

    private val sensitiveJsonValue = Regex(
        """(?i)(\"(?:authorization|api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|prompt|system[_-]?prompt|content|messages|arguments)\"\s*:\s*)\"(?:\\.|[^\"])*\""""
    )
    private val sensitiveAssignment = Regex(
        """(?i)\b(authorization|api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|password|prompt|system[_-]?prompt|content|messages|arguments)\s*[:=]\s*([^\s,;]+)"""
    )
    private val bearerCredential = Regex("""(?i)\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=-]+""")
    private val knownSecret = Regex(
        """\b(?:sk-[A-Za-z0-9_-]{8,}|gh[pousr]_[A-Za-z0-9_]{8,}|AIza[A-Za-z0-9_-]{12,})\b"""
    )
    private val secretQueryParameter = Regex(
        """(?i)([?&](?:api[_-]?key|access[_-]?token|token|key)=)[^&\s]+"""
    )

    fun redact(value: String): String {
        val structured = runCatching { JsonParser.parseString(value) }.getOrNull()
            ?.takeIf { it.isJsonObject || it.isJsonArray }
            ?.let(::redactJson)
            ?.toString()
            ?: value
        return structured
        .replace(bearerCredential) { match -> "${match.groupValues[1]} $REDACTED" }
        .replace(sensitiveJsonValue) { match -> "${match.groupValues[1]}\"$REDACTED\"" }
        .replace(sensitiveAssignment) { match -> "${match.groupValues[1]}=$REDACTED" }
        .replace(knownSecret, REDACTED)
        .replace(secretQueryParameter) { match -> "${match.groupValues[1]}$REDACTED" }
    }

    private fun redactJson(element: JsonElement): JsonElement = when {
        element.isJsonObject -> JsonObject().also { safe ->
            element.asJsonObject.entrySet().forEach { (key, child) ->
                safe.add(
                    key,
                    if (key.lowercase() in sensitiveKeys) JsonPrimitive(REDACTED) else redactJson(child)
                )
            }
        }
        element.isJsonArray -> JsonArray().also { safe ->
            element.asJsonArray.forEach { child -> safe.add(redactJson(child)) }
        }
        else -> element.deepCopy()
    }
}
