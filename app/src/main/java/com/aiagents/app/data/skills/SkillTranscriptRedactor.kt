package com.aiagents.app.data.skills

import java.util.Locale

enum class SkillTranscriptRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

data class SkillTranscriptMessage(
    val role: SkillTranscriptRole,
    val content: String
)

/**
 * Redacts and bounds chat input before it is persisted for local skill review.
 * System prompts and tool results are deliberately excluded.
 */
object SkillTranscriptRedactor {
    private const val MAX_MESSAGES = 12
    private const val MAX_MESSAGE_CHARS = 700
    private const val MAX_TRANSCRIPT_CHARS = 7_000

    private val secretAssignment = Regex(
        "(?i)\\b(api[ _-]?key|token|secret|password|passphrase|contraseña)\\s*[:=]\\s*[^\\s,;]+"
    )
    private val bearerToken = Regex("(?i)\\bbearer\\s+[a-z0-9._~+/=-]{8,}")
    private val providerToken = Regex(
        "(?i)\\b(sk-[a-z0-9_-]{8,}|hf_[a-z0-9_-]{8,}|ghp_[a-z0-9_-]{8,}|xox[baprs]-[a-z0-9-]{8,})\\b"
    )
    private val email = Regex("(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b")
    private val url = Regex("(?i)https?://\\S+")
    private val userPath = Regex("(?i)(/users/[^/\\s]+|/home/[^/\\s]+)(/[^\\s]*)?")
    private val longNumber = Regex("(?<!\\d)\\d(?:[ -]?\\d){8,}(?!\\d)")
    private val phone = Regex("(?<!\\w)(?:\\+?\\d{1,3}[ .-]?)?(?:\\(?\\d{2,4}\\)?[ .-]?){2,4}\\d{2,4}(?!\\w)")
    private val whitespace = Regex("[\\t\\r ]+")

    fun redact(messages: List<SkillTranscriptMessage>): String {
        val eligible = messages
            .asSequence()
            .filter { it.role == SkillTranscriptRole.USER || it.role == SkillTranscriptRole.ASSISTANT }
            .filter { it.content.isNotBlank() }
            .toList()
            .takeLast(MAX_MESSAGES)

        val redactedLines = eligible.map { message ->
            val label = when (message.role) {
                SkillTranscriptRole.USER -> "USUARIO"
                SkillTranscriptRole.ASSISTANT -> "ASISTENTE"
                else -> error("Rol no permitido")
            }
            "$label: ${redactContent(message.content)}"
        }

        var usedChars = 0
        val newestLines = mutableListOf<String>()
        for (line in redactedLines.asReversed()) {
            val additionalChars = line.length + if (newestLines.isEmpty()) 0 else 1
            if (usedChars + additionalChars > MAX_TRANSCRIPT_CHARS) break
            newestLines += line
            usedChars += additionalChars
        }
        return newestLines.asReversed().joinToString("\n")
    }

    fun redactContent(raw: String): String {
        var value = raw
            .replace('\n', ' ')
            .take(MAX_MESSAGE_CHARS * 2)
        value = secretAssignment.replace(value) { match ->
            "${match.groupValues[1].lowercase(Locale.ROOT)}=[REDACTADO]"
        }
        value = bearerToken.replace(value, "Bearer [REDACTADO]")
        value = providerToken.replace(value, "[TOKEN_REDACTADO]")
        value = email.replace(value, "[EMAIL_REDACTADO]")
        value = url.replace(value, "[ENLACE]")
        value = userPath.replace(value, "[RUTA_PRIVADA]")
        value = longNumber.replace(value, "[NUMERO_REDACTADO]")
        value = phone.replace(value, "[TELEFONO_REDACTADO]")
        return whitespace.replace(value, " ").trim().take(MAX_MESSAGE_CHARS)
    }
}
