package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.local.MemoryDao
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.model.MemoryEntity
import com.aiagents.app.data.model.MemoryLinkEntity
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

data class MemoryToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class MemoryToolHandler @Inject constructor(
    private val memoryDao: MemoryDao,
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "MemoryToolHandler"

        /**
         * Translations per language: lang_code → (englishKey → list of translations).
         * Only the active language is loaded into the reverse lookup.
         */
        private val TRANSLATIONS_BY_LANG: Map<String, Map<String, List<String>>> = mapOf(
            "es" to mapOf(
                "name" to listOf("nombre"),
                "preferred_name" to listOf("apodo", "sobrenombre"),
                "country" to listOf("país", "pais"),
                "city" to listOf("ciudad"),
                "location" to listOf("ubicación", "ubicacion"),
                "language" to listOf("idioma"),
                "food" to listOf("comida"),
                "music" to listOf("música", "musica"),
                "job" to listOf("trabajo", "empleo"),
                "occupation" to listOf("ocupación", "ocupacion", "profesión"),
                "age" to listOf("edad"),
                "birthday" to listOf("cumpleaños"),
                "hobby" to listOf("pasatiempo"),
                "pet" to listOf("mascota"),
                "movie" to listOf("película", "pelicula"),
                "book" to listOf("libro"),
                "sport" to listOf("deporte"),
                "drink" to listOf("bebida"),
                "email" to listOf("correo"),
                "phone" to listOf("teléfono", "telefono", "celular"),
                "currency" to listOf("moneda"),
                "timezone" to listOf("zona horaria")
            )
        )

        const val TOOL_SEARCH = "memory_search"
        const val TOOL_SAVE = "memory_save"
        const val TOOL_LIST = "memory_list"
        const val TOOL_DELETE = "memory_delete"
        const val TOOL_UPDATE = "memory_update"
        const val TOOL_LINK = "memory_link"

        val ALL_TOOL_NAMES = setOf(
            TOOL_SEARCH, TOOL_SAVE, TOOL_LIST, TOOL_DELETE, TOOL_UPDATE, TOOL_LINK
        )

        /** Room is the on-demand secondary tier; active Markdown is already in prompt context. */
        val READ_TOOL_NAMES = setOf(TOOL_SEARCH, TOOL_LIST)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_SEARCH,
                "description" to "Search lower-priority or demoted secondary memory in SQLite. Active MEMORY.md/USER.md is already in context and is not duplicated here. Keys are normally English snake_case.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "Search text"),
                        "category" to mapOf("type" to "string",
                            "enum" to listOf("fact", "preference", "habit", "interaction", "relationship")),
                        "max_results" to mapOf("type" to "integer", "description" to "Max results (default 5, max 10)")
                    ),
                    "required" to listOf("query"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_SAVE,
                "description" to "Internal secondary-memory write. Active-worthy facts belong in the memory tool's Markdown targets; secondary importance is limited to 1-6.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "content" to mapOf("type" to "string", "description" to "Format 'key: value'. E.g. 'name: Gabriel', 'food: mexicana'"),
                        "category" to mapOf("type" to "string",
                            "enum" to listOf("fact", "preference", "habit", "interaction", "relationship")),
                        "subcategory" to mapOf("type" to "string", "description" to "E.g. food, work, name, location"),
                        "importance" to mapOf("type" to "integer", "description" to "1-10")
                    ),
                    "required" to listOf("content", "category", "importance"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_LIST,
                "description" to "List lower-priority and demoted secondary memories, optionally filtered by category.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "category" to mapOf("type" to "string",
                            "enum" to listOf("fact", "preference", "habit", "interaction", "relationship")),
                        "sort_by" to mapOf("type" to "string",
                            "enum" to listOf("importance", "recent", "oldest"))
                    ),
                    "required" to emptyList<String>())
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_DELETE,
                "description" to "Delete a memory by ID.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "memory_id" to mapOf("type" to "integer", "description" to "Memory ID to delete")
                    ),
                    "required" to listOf("memory_id"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_UPDATE,
                "description" to "Update content or importance of an existing memory.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "memory_id" to mapOf("type" to "integer", "description" to "Memory ID"),
                        "content" to mapOf("type" to "string", "description" to "New content (optional)"),
                        "importance" to mapOf("type" to "integer", "description" to "New importance 1-10 (optional)")
                    ),
                    "required" to listOf("memory_id"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_LINK,
                "description" to "Link two memories. Types: related, contradicts, supersedes, refines.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "source_id" to mapOf("type" to "integer", "description" to "Source memory ID"),
                        "target_id" to mapOf("type" to "integer", "description" to "Target memory ID"),
                        "link_type" to mapOf("type" to "string",
                            "enum" to listOf("related", "contradicts", "supersedes", "refines"))
                    ),
                    "required" to listOf("source_id", "target_id", "link_type"))
            ))
        )

        fun getReadToolDefinitionsJson(): List<Map<String, Any>> =
            getToolDefinitionsJson().filter { definition ->
                @Suppress("UNCHECKED_CAST")
                val function = definition["function"] as? Map<String, Any>
                function?.get("name") in READ_TOOL_NAMES
            }
    }

    /** Reverse lookup built for the active language only. Rebuilt on language change. */
    private var cachedLang: String? = null
    private var reverseTranslations: Map<String, String> = emptyMap()

    private fun getReverseLookup(): Map<String, String> {
        val lang = securePreferences.getAppLanguage().ifBlank { "en" }
        if (lang == cachedLang) return reverseTranslations

        val map = mutableMapOf<String, String>()
        TRANSLATIONS_BY_LANG[lang]?.forEach { (englishKey, translations) ->
            translations.forEach { t -> map[t.lowercase()] = englishKey }
        }
        reverseTranslations = map
        cachedLang = lang
        return map
    }

    suspend fun executeTool(toolCallId: String, toolName: String, arguments: String): MemoryToolResult {
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            when (toolName) {
                TOOL_SEARCH -> search(toolCallId, args)
                TOOL_SAVE -> save(toolCallId, args)
                TOOL_LIST -> list(toolCallId, args)
                TOOL_DELETE -> delete(toolCallId, args)
                TOOL_UPDATE -> update(toolCallId, args)
                TOOL_LINK -> link(toolCallId, args)
                else -> MemoryToolResult(toolCallId, false, "Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando $toolName", e)
            MemoryToolResult(toolCallId, false, "Error: ${e.message}")
        }
    }

    /**
     * Expands a search query by translating foreign-language terms to their English key equivalents.
     * E.g. "país" → "country", "nombre" → "name".
     * Returns the original query + any translated terms.
     */
    private fun expandQueryWithTranslations(query: String): List<String> {
        val words = query.trim().split("\\s+".toRegex())
        val queries = mutableSetOf(query) // always include original

        val lookup = getReverseLookup()
        for (word in words) {
            val lower = word.lowercase()
            // Foreign word → add English key (e.g. "país" → "country")
            lookup[lower]?.let { englishKey ->
                queries.add(englishKey)
            }
        }

        return queries.toList()
    }

    private suspend fun search(id: String, args: com.google.gson.JsonObject): MemoryToolResult {
        val query = args.get("query")?.asString ?: return MemoryToolResult(id, false, "Missing: query")
        val category = args.get("category")?.asString
        val maxResults = args.get("max_results")?.asInt?.coerceIn(1, 10) ?: 5

        val expandedQueries = expandQueryWithTranslations(query)

        // Try FTS with each expanded query
        var results = mutableListOf<MemoryEntity>()
        val seenIds = mutableSetOf<Long>()

        for (q in expandedQueries) {
            if (results.size >= maxResults) break

            val ftsQuery = q.trim().split("\\s+".toRegex())
                .filter { it.length > 1 }
                .joinToString(" ") { "$it*" }

            if (ftsQuery.isBlank()) continue

            try {
                val ftsResults = memoryDao.searchFts(ftsQuery, maxResults * 2)
                val filtered = if (category != null) {
                    ftsResults.filter { it.category == category }
                } else ftsResults

                for (r in filtered) {
                    if (seenIds.add(r.id) && results.size < maxResults) {
                        results.add(r)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "FTS query failed for '$q', trying next", e)
            }
        }

        // Fallback: if FTS found nothing, try substring match with all expanded queries
        if (results.isEmpty()) {
            val allMemories = if (category != null) {
                memoryDao.getByCategory(category, 50)
            } else {
                memoryDao.getAll(50)
            }
            for (q in expandedQueries) {
                val matches = allMemories.filter {
                    it.content.contains(q, ignoreCase = true) && seenIds.add(it.id)
                }
                results.addAll(matches)
            }
            results = results.take(maxResults).toMutableList()
        }

        if (results.isEmpty()) {
            return MemoryToolResult(id, true, "No results for: $query")
        }

        // Update access counts
        val now = System.currentTimeMillis()
        results.forEach { memoryDao.incrementAccess(it.id, now) }

        val formatted = results.joinToString("\n") { m ->
            "[${m.id}] ${m.content}"
        }
        return MemoryToolResult(id, true, formatted)
    }

    private suspend fun save(id: String, args: com.google.gson.JsonObject): MemoryToolResult {
        val content = args.get("content")?.asString ?: return MemoryToolResult(id, false, "Missing: content")
        val category = args.get("category")?.asString ?: return MemoryToolResult(id, false, "Missing: category")
        val subcategory = args.get("subcategory")?.asString ?: ""
        val importance = args.get("importance")?.asInt?.coerceIn(1, 10) ?: 5

        // Extract key from "key: value" format for matching
        val contentKey = if (content.contains(":")) content.substringBefore(":").trim() else null

        // Find existing memory with same key in same category/subcategory
        val existing = if (contentKey != null && subcategory.isNotBlank()) {
            // Match by key prefix — e.g. "name:" matches any "name: X"
            memoryDao.getByCategoryAndSubcategory(category, subcategory)
                .filter { it.content.startsWith("$contentKey:") }
        } else {
            // Fallback to FTS for non key:value content
            val ftsQuery = content.trim().split("\\s+".toRegex()).take(4)
                .filter { it.length > 1 }.joinToString(" ") { "$it*" }
            try {
                if (ftsQuery.isNotBlank()) {
                    memoryDao.searchFts(ftsQuery, 3)
                        .filter { it.category == category && it.subcategory == subcategory }
                } else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        // If matching memory exists, update it
        if (existing.isNotEmpty()) {
            val match = existing.first()
            val now = System.currentTimeMillis()

            if (match.content == content) {
                // Exact duplicate — just bump access
                memoryDao.incrementAccess(match.id, now)
                return MemoryToolResult(id, true, "Already exists [${match.id}] $content")
            }

            // Same key, different value — update in place
            val updated = match.copy(
                content = content,
                importance = maxOf(match.importance, importance),
                confidence = 1.0f,
                updatedAt = now,
                lastAccessedAt = now,
                accessCount = match.accessCount + 1
            )
            memoryDao.update(updated)
            return MemoryToolResult(id, true, "Updated [${match.id}] $content")
        }

        val now = System.currentTimeMillis()
        val memory = MemoryEntity(
            content = content,
            category = category,
            subcategory = subcategory,
            importance = importance,
            source = "cortex",
            createdAt = now,
            updatedAt = now,
            lastAccessedAt = now
        )
        val newId = memoryDao.insert(memory)
        return MemoryToolResult(id, true, "Saved [$newId] $content")
    }

    private suspend fun list(id: String, args: com.google.gson.JsonObject): MemoryToolResult {
        val category = args.get("category")?.asString
        val sortBy = args.get("sort_by")?.asString ?: "importance"

        val results = when {
            category != null -> memoryDao.getByCategory(category, 20)
            sortBy == "recent" -> memoryDao.getRecent(20)
            sortBy == "oldest" -> memoryDao.getOldest(20)
            else -> memoryDao.getAll(20)
        }

        if (results.isEmpty()) {
            return MemoryToolResult(id, true, "Empty${if (category != null) " ($category)" else ""}")
        }

        val total = memoryDao.count()
        val formatted = buildString {
            appendLine("${results.size}/$total:")
            results.forEach { m -> appendLine("[${m.id}] ${m.content}") }
        }
        return MemoryToolResult(id, true, formatted.trim())
    }

    private suspend fun delete(id: String, args: com.google.gson.JsonObject): MemoryToolResult {
        val memoryId = args.get("memory_id")?.asLong ?: return MemoryToolResult(id, false, "Missing: memory_id")
        val existing = memoryDao.getById(memoryId) ?: return MemoryToolResult(id, false, "Not found: $memoryId")
        memoryDao.deleteById(memoryId)
        return MemoryToolResult(id, true, "Deleted [$memoryId]")
    }

    private suspend fun update(id: String, args: com.google.gson.JsonObject): MemoryToolResult {
        val memoryId = args.get("memory_id")?.asLong ?: return MemoryToolResult(id, false, "Missing: memory_id")
        val existing = memoryDao.getById(memoryId) ?: return MemoryToolResult(id, false, "Not found: $memoryId")

        val newContent = args.get("content")?.asString ?: existing.content
        val newImportance = args.get("importance")?.asInt?.coerceIn(1, 10) ?: existing.importance

        val updated = existing.copy(
            content = newContent,
            importance = newImportance,
            confidence = 1.0f,
            updatedAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )
        memoryDao.update(updated)
        return MemoryToolResult(id, true, "Updated [$memoryId] $newContent")
    }

    private suspend fun link(id: String, args: com.google.gson.JsonObject): MemoryToolResult {
        val sourceId = args.get("source_id")?.asLong ?: return MemoryToolResult(id, false, "Missing: source_id")
        val targetId = args.get("target_id")?.asLong ?: return MemoryToolResult(id, false, "Missing: target_id")
        val linkType = args.get("link_type")?.asString ?: return MemoryToolResult(id, false, "Missing: link_type")

        if (linkType !in listOf("related", "contradicts", "supersedes", "refines")) {
            return MemoryToolResult(id, false, "Invalid link_type: $linkType")
        }

        val source = memoryDao.getById(sourceId) ?: return MemoryToolResult(id, false, "Not found: $sourceId")
        val target = memoryDao.getById(targetId) ?: return MemoryToolResult(id, false, "Not found: $targetId")

        // If contradicts, lower confidence of the older one
        if (linkType == "contradicts") {
            val older = if (source.createdAt < target.createdAt) source else target
            memoryDao.update(older.copy(confidence = older.confidence * 0.5f, updatedAt = System.currentTimeMillis()))
        }

        // If supersedes, lower confidence of target
        if (linkType == "supersedes") {
            memoryDao.update(target.copy(confidence = target.confidence * 0.3f, updatedAt = System.currentTimeMillis()))
        }

        val linkId = memoryDao.insertLink(MemoryLinkEntity(
            sourceId = sourceId, targetId = targetId,
            linkType = linkType, createdAt = System.currentTimeMillis()
        ))
        return MemoryToolResult(id, true, "Linked [${source.id}]--$linkType-->[${target.id}]")
    }

    /**
     * Runs periodic maintenance: confidence decay for old memories,
     * deletion of weak/expired memories. Call from a background worker.
     */
    suspend fun runMaintenance() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        memoryDao.decayOldMemories(threshold = thirtyDaysAgo, factor = 0.9f)
        memoryDao.deleteWeakMemories()
        memoryDao.deleteExpiredMemories()
        memoryDao.deleteOldestSummaries(50)
        val total = memoryDao.count()
        if (total > 500) {
            memoryDao.deleteLowestValue(total - 500)
        }
        Log.i(TAG, "Memory maintenance complete. Total memories: ${memoryDao.count()}")
    }
}
