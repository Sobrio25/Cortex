package com.aiagents.app.data.memory

import com.aiagents.app.data.local.MemoryDao
import com.aiagents.app.data.model.MemoryEntity
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns Room's secondary, on-demand memory tier.
 *
 * MEMORY.md and USER.md are the active context. Room may retain useful lower-priority facts and
 * deliberately demoted MEMORY.md entries, but it must never retain another copy of active memory.
 */
@Singleton
class SecondaryMemoryStore @Inject constructor(
    private val memoryDao: MemoryDao,
    private val markdownMemoryStore: CortexMarkdownMemoryStore,
    private val profileStore: CortexProfileStore
) {
    suspend fun save(
        content: String,
        category: String = "fact",
        subcategory: String = "",
        importance: Int = DEFAULT_IMPORTANCE,
        source: String = SOURCE_SECONDARY_EXTRACTION
    ): SecondaryMemoryWriteResult {
        val normalizedContent = content.trim()
        if (normalizedContent.isBlank()) {
            return SecondaryMemoryWriteResult(false, false, null, "Secondary memory content is empty.")
        }
        if (normalizedContent.length > MAX_ENTRY_UTF16_UNITS) {
            return SecondaryMemoryWriteResult(false, false, null, "Secondary memory entry is too large.")
        }
        CortexMemoryPolicy.securityIssue(normalizedContent)?.let { issue ->
            return SecondaryMemoryWriteResult(false, false, null, "Rejected: $issue.")
        }
        if (SecondaryMemoryDedupPolicy.isRepresentedInActiveMemory(normalizedContent, activeEntries())) {
            return SecondaryMemoryWriteResult(
                success = true,
                changed = false,
                memoryId = null,
                message = "Skipped because the fact is already present in active Markdown memory."
            )
        }

        val safeCategory = category.takeIf { it in CATEGORIES } ?: "fact"
        val safeSubcategory = subcategory.trim().take(MAX_SUBCATEGORY_CHARS)
        val safeImportance = importance.coerceIn(1, MAX_SECONDARY_IMPORTANCE)
        val existing = findExisting(normalizedContent, safeCategory, safeSubcategory)
        val now = System.currentTimeMillis()

        if (existing != null) {
            val updated = existing.copy(
                content = normalizedContent,
                importance = maxOf(existing.importance, safeImportance).coerceAtMost(MAX_SECONDARY_IMPORTANCE),
                confidence = 1.0f,
                source = source,
                updatedAt = now,
                lastAccessedAt = now
            )
            if (updated == existing) {
                return SecondaryMemoryWriteResult(true, false, existing.id, "Secondary memory already exists.")
            }
            memoryDao.update(updated)
            return SecondaryMemoryWriteResult(true, true, existing.id, "Secondary memory updated.")
        }

        val id = memoryDao.insert(
            MemoryEntity(
                content = normalizedContent,
                category = safeCategory,
                subcategory = safeSubcategory,
                importance = safeImportance,
                confidence = 1.0f,
                source = source,
                createdAt = now,
                updatedAt = now,
                lastAccessedAt = now
            )
        )
        return SecondaryMemoryWriteResult(true, true, id, "Saved to secondary memory.")
    }

    /** Saves still-valid entries that the memory agent deliberately demoted from MEMORY.md. */
    suspend fun archiveDemoted(entries: List<String>): Int {
        var saved = 0
        entries.distinct().forEach { entry ->
            val result = save(
                content = entry,
                category = "fact",
                subcategory = SUBCATEGORY_DEMOTED,
                importance = DEFAULT_IMPORTANCE,
                source = SOURCE_MEMORY_DEMOTED
            )
            if (result.changed) saved++
        }
        return saved
    }

    /** Removes legacy/current rows that duplicate MEMORY.md or USER.md. */
    suspend fun removeActiveDuplicates(): Int {
        memoryDao.capImportance(MAX_SECONDARY_IMPORTANCE)
        val active = activeEntries()
        if (active.isEmpty()) return 0
        val duplicateIds = memoryDao.getAll(MAX_SCAN_ENTRIES)
            .filter { SecondaryMemoryDedupPolicy.isRepresentedInActiveMemory(it.content, active) }
            .map { it.id }
        if (duplicateIds.isNotEmpty()) memoryDao.deleteByIds(duplicateIds)
        return duplicateIds.size
    }

    private suspend fun findExisting(
        content: String,
        category: String,
        subcategory: String
    ): MemoryEntity? {
        val normalized = SecondaryMemoryDedupPolicy.normalize(content)
        val key = SecondaryMemoryDedupPolicy.key(content)
        return memoryDao.getAll(MAX_SCAN_ENTRIES).firstOrNull { existing ->
            SecondaryMemoryDedupPolicy.normalize(existing.content) == normalized ||
                (key != null &&
                    key == SecondaryMemoryDedupPolicy.key(existing.content) &&
                    existing.category == category &&
                    existing.subcategory == subcategory)
        }
    }

    private fun activeEntries(): List<String> = buildList {
        addAll(markdownMemoryStore.snapshot().entries)
        addAll(profileStore.userSnapshot().entries)
    }.filter(String::isNotBlank)

    companion object {
        const val SOURCE_SECONDARY_EXTRACTION = "secondary_extraction"
        const val SOURCE_MEMORY_DEMOTED = "memory_demoted"
        const val SUBCATEGORY_DEMOTED = "memory_demoted"
        const val MAX_SECONDARY_IMPORTANCE = 6
        private const val DEFAULT_IMPORTANCE = 4
        private const val MAX_ENTRY_UTF16_UNITS = 2_000
        private const val MAX_SUBCATEGORY_CHARS = 80
        private const val MAX_SCAN_ENTRIES = 10_000
        private val CATEGORIES = setOf("fact", "preference", "habit", "interaction", "relationship")
    }
}

data class SecondaryMemoryWriteResult(
    val success: Boolean,
    val changed: Boolean,
    val memoryId: Long?,
    val message: String
)

internal object SecondaryMemoryDedupPolicy {
    fun isRepresentedInActiveMemory(candidate: String, activeEntries: List<String>): Boolean {
        val normalizedCandidate = normalize(candidate)
        if (normalizedCandidate.isBlank()) return false
        val value = candidate.substringAfter(':', missingDelimiterValue = "")
            .let(::normalize)
            .takeIf(String::isNotBlank)
        val candidateKey = key(candidate)

        return activeEntries.any { activeEntry ->
            val normalizedActive = normalize(activeEntry)
            normalizedActive == normalizedCandidate ||
                (normalizedCandidate.length >= MIN_CONTAINED_LENGTH &&
                    (normalizedActive.contains(normalizedCandidate) ||
                        normalizedCandidate.contains(normalizedActive))) ||
                (value != null &&
                    (value.length >= MIN_VALUE_LENGTH ||
                        value.count { it == ' ' } >= 1 ||
                        candidateKey in HIGH_SIGNAL_KEYS) &&
                    normalizedActive.contains(value))
        }
    }

    fun key(content: String): String? = content
        .substringBefore(':', missingDelimiterValue = "")
        .takeIf { ':' in content }
        ?.let(::normalize)
        ?.takeIf(String::isNotBlank)

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(COMBINING_MARKS, "")
        .lowercase()
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
        .replace(WHITESPACE, " ")

    private const val MIN_CONTAINED_LENGTH = 12
    private const val MIN_VALUE_LENGTH = 8
    private val HIGH_SIGNAL_KEYS = setOf(
        "name", "preferred name", "nickname", "city", "country", "timezone", "email", "phone"
    )
    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
    private val WHITESPACE = Regex("\\s+")
}
