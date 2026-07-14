package com.aiagents.app.data.terminal

import java.net.URI

/**
 * Pure validation for the small, explicit set of external intents exposed to an agent.
 *
 * Keeping this policy independent from Android makes it straightforward to unit-test and,
 * importantly, prevents model-provided values from becoming raw intent actions or schemes.
 */
internal object SafeAppIntentPolicy {
    const val MAX_ARGUMENTS_LENGTH = 50_000
    const val MAX_APP_NAME_LENGTH = 120
    const val MAX_PACKAGE_NAME_LENGTH = 255
    const val MAX_URL_LENGTH = 2_048
    const val MAX_MAP_QUERY_LENGTH = 500
    const val MAX_PHONE_LENGTH = 40
    const val MAX_SHARE_TEXT_LENGTH = 10_000
    const val MAX_SHARE_TITLE_LENGTH = 200
    const val MAX_FILTER_LENGTH = 120
    const val MAX_MIME_TYPE_LENGTH = 127

    val allowedCapabilityQueries: Set<String> = setOf(
        "send",
        "share",
        "share_text",
        "view",
        "open_url",
        "edit",
        "open_file",
        "map_search",
        "navigate",
        "dial"
    )

    private val packageNamePattern = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")
    private val mimeTypePattern = Regex("^[A-Za-z0-9!#$&^_.+*-]+/[A-Za-z0-9!#$&^_.+*-]+$")
    private val phonePattern = Regex("^\\+?[0-9 ().-]+$")

    fun isValidPackageName(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= MAX_PACKAGE_NAME_LENGTH &&
            packageNamePattern.matches(value)

    fun isValidAppName(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_APP_NAME_LENGTH && !value.hasControlCharacters()

    fun isValidFilter(value: String): Boolean =
        value.length <= MAX_FILTER_LENGTH && !value.hasControlCharacters()

    fun isValidWebUrl(value: String): Boolean {
        if (value.isBlank() || value.length > MAX_URL_LENGTH || value.hasControlCharacters()) return false

        return try {
            val uri = URI(value)
            uri.scheme?.lowercase() in setOf("http", "https") &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null
        } catch (_: Exception) {
            false
        }
    }

    fun isValidMapQuery(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_MAP_QUERY_LENGTH && !value.hasControlCharacters()

    fun normalizePhoneNumber(value: String): String? {
        if (value.isBlank() || value.length > MAX_PHONE_LENGTH || !phonePattern.matches(value)) return null
        val normalized = buildString {
            value.forEachIndexed { index, character ->
                if (character.isDigit() || (character == '+' && index == 0)) append(character)
            }
        }
        return normalized.takeIf { candidate -> candidate.count { it.isDigit() } >= 3 }
    }

    fun isValidShareText(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_SHARE_TEXT_LENGTH && !value.hasDisallowedControls()

    fun isValidShareTitle(value: String): Boolean =
        value.length <= MAX_SHARE_TITLE_LENGTH && !value.hasControlCharacters()

    fun isValidMimeType(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= MAX_MIME_TYPE_LENGTH &&
            mimeTypePattern.matches(value)

    /**
     * Only URI shapes that correspond to a supported capability probe are accepted.
     * File paths and model-provided custom schemes are intentionally rejected.
     */
    fun isValidCapabilityUri(intentType: String, value: String): Boolean {
        if (value.isBlank() || value.length > MAX_URL_LENGTH || value.hasControlCharacters()) return false
        return when (intentType.lowercase()) {
            "view", "open_url" -> isValidWebUrl(value)
            "open_file", "edit" -> value.startsWith("content://")
            else -> false
        }
    }

    private fun String.hasControlCharacters(): Boolean = any { it.isISOControl() }

    private fun String.hasDisallowedControls(): Boolean = any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
}
