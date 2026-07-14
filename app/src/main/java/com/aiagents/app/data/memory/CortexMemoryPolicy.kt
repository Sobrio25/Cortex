package com.aiagents.app.data.memory

import java.text.Normalizer

/**
 * Pure policy for Cortex's bounded Markdown memory.
 *
 * Hermes counts Python Unicode characters over entries joined with `\n§\n`. Java/Kotlin's
 * [String.length] counts UTF-16 code units, so all capacity checks here deliberately count Unicode
 * code points instead.
 */
object CortexMemoryPolicy {
    const val HERMES_MEMORY_MAX_CHARS = 2_200
    const val ENTRY_DELIMITER = "\n§\n"

    fun countCharacters(text: String): Int = text.codePointCount(0, text.length)

    fun serialize(entries: List<String>): String = entries.joinToString(ENTRY_DELIMITER)

    /** Parses a disk/UI representation while keeping the first occurrence of exact duplicates. */
    fun parse(
        markdown: String,
        maxChars: Int = HERMES_MEMORY_MAX_CHARS
    ): CortexMemoryPolicyResult {
        val normalized = normalizeNewlines(markdown).trim()
        val entries = if (normalized.isBlank()) {
            emptyList()
        } else {
            normalized.split(ENTRY_DELIMITER)
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
        }
        return validateFinal(
            entries = entries,
            changed = normalized != serialize(entries),
            maxChars = maxChars
        )
    }

    /** Applies all operations in memory and validates only the final state, making batches atomic. */
    fun apply(
        currentEntries: List<String>,
        operations: List<CortexMemoryOperation>,
        maxChars: Int = HERMES_MEMORY_MAX_CHARS
    ): CortexMemoryPolicyResult {
        if (operations.isEmpty()) {
            return CortexMemoryPolicyResult(
                success = false,
                changed = false,
                entries = currentEntries,
                message = "No memory operations were provided."
            )
        }

        val working = currentEntries.toMutableList()
        var changed = false

        for ((index, operation) in operations.withIndex()) {
            when (operation.action) {
                CortexMemoryAction.ADD -> {
                    val content = normalizeEntry(operation.content)
                        ?: return failure(currentEntries, "Operation ${index + 1}: add requires non-empty content.")
                    validateNewEntry(content)?.let { reason ->
                        return failure(currentEntries, "Operation ${index + 1}: $reason")
                    }
                    if (content !in working) {
                        working += content
                        changed = true
                    }
                }

                CortexMemoryAction.REPLACE -> {
                    val oldText = normalizeMatch(operation.oldText)
                        ?: return failure(currentEntries, "Operation ${index + 1}: replace requires old_text.")
                    val content = normalizeEntry(operation.content)
                        ?: return failure(currentEntries, "Operation ${index + 1}: replace requires non-empty content.")
                    validateNewEntry(content)?.let { reason ->
                        return failure(currentEntries, "Operation ${index + 1}: $reason")
                    }
                    val matches = matchingIndexes(working, oldText)
                    if (matches.size != 1) {
                        return failure(currentEntries, matchFailure(index, matches.size))
                    }
                    if (working[matches.single()] != content) {
                        working[matches.single()] = content
                        changed = true
                    }
                }

                CortexMemoryAction.REMOVE -> {
                    val oldText = normalizeMatch(operation.oldText)
                        ?: return failure(currentEntries, "Operation ${index + 1}: remove requires old_text.")
                    val matches = matchingIndexes(working, oldText)
                    if (matches.size != 1) {
                        return failure(currentEntries, matchFailure(index, matches.size))
                    }
                    working.removeAt(matches.single())
                    changed = true
                }
            }
        }

        val finalEntries = working.filter(String::isNotBlank).distinct()
        return validateFinal(finalEntries, changed, maxChars)
            .let { result -> if (result.success) result else result.copy(entries = currentEntries, changed = false) }
    }

    /** Returns null when safe; otherwise a non-sensitive rejection reason. */
    fun securityIssue(text: String): String? {
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val type = Character.getType(codePoint)
            val forbiddenControl = type == Character.CONTROL.toInt() &&
                codePoint != '\n'.code && codePoint != '\t'.code
            val invalidSurrogate = type == Character.SURROGATE.toInt()
            val invisibleOrDirectional = type == Character.FORMAT.toInt() || codePoint in BIDI_CONTROLS
            if (forbiddenControl || invalidSurrogate || invisibleOrDirectional) {
                return "content contains invisible, directional, or control Unicode characters"
            }
            offset += Character.charCount(codePoint)
        }

        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val threat = THREAT_PATTERNS.firstOrNull { it.containsMatchIn(normalized) }
        return if (threat != null) {
            "content resembles prompt injection, credential exfiltration, or persistence instructions"
        } else {
            null
        }
    }

    private fun validateFinal(
        entries: List<String>,
        changed: Boolean,
        maxChars: Int
    ): CortexMemoryPolicyResult {
        require(maxChars > 0) { "maxChars must be positive." }
        entries.forEachIndexed { index, entry ->
            validateNewEntry(entry)?.let { reason ->
                return failure(entries, "Entry ${index + 1}: $reason")
            }
        }
        val used = countCharacters(serialize(entries))
        if (used > maxChars) {
            return failure(
                entries,
                "Memory would use $used/$maxChars characters. Consolidate or remove stale entries and retry."
            )
        }
        return CortexMemoryPolicyResult(
            success = true,
            changed = changed,
            entries = entries,
            message = if (changed) "Memory updated." else "Memory already had this state; no duplicate was added."
        )
    }

    private fun validateNewEntry(entry: String): String? {
        if (entry.contains(ENTRY_DELIMITER)) {
            return "a single entry cannot contain the memory entry delimiter"
        }
        return securityIssue(entry)
    }

    private fun normalizeEntry(value: String?): String? = value
        ?.let(::normalizeNewlines)
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun normalizeMatch(value: String?): String? = value
        ?.let(::normalizeNewlines)
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun normalizeNewlines(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    private fun matchingIndexes(entries: List<String>, oldText: String): List<Int> = entries
        .mapIndexedNotNull { index, entry -> index.takeIf { entry.contains(oldText) } }

    private fun matchFailure(operationIndex: Int, matches: Int): String = when (matches) {
        0 -> "Operation ${operationIndex + 1}: old_text did not match any entry."
        else -> "Operation ${operationIndex + 1}: old_text matched $matches entries; use a unique substring."
    }

    private fun failure(entries: List<String>, message: String) = CortexMemoryPolicyResult(
        success = false,
        changed = false,
        entries = entries,
        message = message
    )

    private val BIDI_CONTROLS = setOf(
        0x061C, 0x200E, 0x200F,
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
        0x2066, 0x2067, 0x2068, 0x2069
    )

    private val THREAT_PATTERNS = listOf(
        Regex("(?is)\\b(ignore|disregard|forget|override)\\b.{0,80}\\b(previous|prior|system|developer|instructions?|prompt)\\b"),
        Regex("(?is)\\b(reveal|print|show|leak|expose)\\b.{0,80}\\b(system prompt|developer message|hidden instructions?)\\b"),
        Regex("(?is)<\\|(?:system|developer|assistant)\\|>"),
        Regex("(?is)\\b(you are now|act as)\\b.{0,80}\\b(system|developer|unrestricted|jailbreak)\\b"),
        Regex("(?is)\\b(curl|wget)\\b.{0,120}(https?://|--data|\\s-d\\s)"),
        Regex("(?is)\\b(exfiltrate|upload|send|post)\\b.{0,100}\\b(credentials?|tokens?|passwords?|private keys?|\\.ssh)\\b"),
        Regex("(?is)\\b(append|write|install|inject)\\b.{0,100}\\b(authorized_keys|ssh-rsa|ssh-ed25519)\\b"),
        Regex("(?i)(?:^|[\\s/])(?:~?/)?\\.ssh(?:/|\\b)|\\bauthorized_keys\\b"),
        Regex("(?is)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        Regex("(?is)\\b(api[_-]?key|access[_-]?token|auth[_-]?token|client[_-]?secret|token|secret|password|passwd)\\b\\s*[:=]\\s*[\"']?[a-z0-9_./+\\-=]{12,}"),
        Regex("(?i)\\bBearer\\s+[a-z0-9._~+/=-]{16,}\\b"),
        Regex("\\beyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}\\b"),
        Regex("(?i)\\bsk-(?:proj-)?[a-z0-9_-]{20,}\\b"),
        Regex("(?i)\\bgh[pousr]_[a-z0-9]{20,}\\b"),
        Regex("\\bAKIA[0-9A-Z]{16}\\b")
    )
}

enum class CortexMemoryAction {
    ADD,
    REPLACE,
    REMOVE;

    companion object {
        fun fromWireValue(value: String?): CortexMemoryAction? = when (value?.lowercase()) {
            "add" -> ADD
            "replace" -> REPLACE
            "remove" -> REMOVE
            else -> null
        }
    }
}

data class CortexMemoryOperation(
    val action: CortexMemoryAction,
    val content: String? = null,
    val oldText: String? = null
)

data class CortexMemoryPolicyResult(
    val success: Boolean,
    val changed: Boolean,
    val entries: List<String>,
    val message: String
)
