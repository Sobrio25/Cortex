package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.auth.ProviderCredentialResolver
import com.aiagents.app.data.events.AgentChangeNotifier
import com.aiagents.app.data.local.ConversationDao
import com.aiagents.app.data.local.MemoryDao
import com.aiagents.app.data.local.MessageDao
import com.aiagents.app.data.memory.SecondaryMemoryStore
import com.aiagents.app.data.model.ConversationEntity
import com.aiagents.app.data.model.MessageEntity
import com.aiagents.app.data.remote.AIClientFactory
import com.aiagents.app.data.remote.ChatMessage
import com.aiagents.app.data.runtime.RuntimeContextProvider
import com.aiagents.app.domain.model.ProviderType
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event-driven memory extraction system.
 * 
 * Triggers:
 * - App startup: extracts from all conversations with new content
 * - Active conversation: extracts from inactive conversations
 * - Conversation resumed: checks if re-extraction is needed
 * 
 * Uses lastMemoryExtraction timestamp per conversation to track state.
 */
@Singleton
class MemoryExtractor @Inject constructor(
    private val memoryDao: MemoryDao,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val aiClientFactory: AIClientFactory,
    private val providerCredentialResolver: ProviderCredentialResolver,
    private val runtimeContextProvider: RuntimeContextProvider,
    private val secondaryMemoryStore: SecondaryMemoryStore,
    private val changeNotifier: AgentChangeNotifier
) {
    private val extractionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var activeExtractionJob: Job? = null

    private val foregroundRequestCount = AtomicInteger(0)
    
    @Volatile
    private var lastBatchExtractionTime = 0L
    
    companion object {
        private const val TAG = "MemoryExtractor"
        private const val MAX_MESSAGES_PER_EXTRACTION = 15
        private const val MIN_MESSAGES_FOR_EXTRACTION = 4
        private const val MAX_TOTAL_MEMORIES = 500  // Increased from 200
        private const val MAX_CONVERSATION_SUMMARIES = 50  // Increased proportionally
        private const val EXTRACTION_COOLDOWN_MS = 60_000L  // 1 minute between batches
        private const val RECALL_MAX_RESULTS = 10
        
        /**
         * Structured prompt that teaches the LLM the exact database schema
         * to generate searchable memories with proper key conventions.
         */
        private const val EXTRACTION_SYSTEM_PROMPT = """
You are the secondary-memory curator. Analyze conversations and retain only useful details that are not important enough for active MEMORY.md or USER.md.

## TWO-TIER MEMORY CONTRACT
- MEMORY.md and USER.md are active, high-priority memory and are already included in your system context.
- cortex_memories is a searchable SQLite secondary archive.
- NEVER copy, paraphrase, or restate a fact already present in MEMORY.md or USER.md.
- High-value identity, strong preferences, durable corrections, and stable operating conventions belong in active Markdown; the main memory tool handles them, so skip them here.
- Save only lower-priority but potentially useful details, minor preferences/habits, and compact historical context.
- Entries deliberately demoted from MEMORY.md are archived automatically by the main memory tool; do not recreate them from conversation text.

## DATABASE SCHEMA (cortex_memories)
Fields you populate via extraction:
- content: "key: value" format (REQUIRED). 
  • Key: English snake_case (searchable by other agents)
  • Value: User's language as stated in conversation
- category: fact | preference | habit | interaction | relationship
- subcategory: Topic for grouping (e.g., "name", "food", "work")
- importance: 1-6 (secondary tier only; anything higher belongs in active Markdown)

## KEY CONVENTIONS
Use concise English snake_case keys that identify the topic, for example:
  occasional_food, minor_hobby, secondary_tool, past_project, recent_topic,
  travel_detail, entertainment_detail, temporary_context

## OUTPUT FORMAT
Return ONLY a JSON array. Use [] if nothing to extract.

[
  {
    "content": "key: value",
    "category": "fact|preference|habit|interaction|relationship",
    "subcategory": "topic",
    "importance": 1-6
  }
]

## EXTRACTION RULES
1. Content MUST be "key: value" format, max 60 characters
2. Key MUST be English snake_case from conventions above
3. Value in user's original language
4. Subcategory should match the key's topic category
5. Importance scale:
   5-6 = Useful secondary detail that may matter again
   3-4 = Minor preference, habit, or context
   1-2 = Compact historical/interaction context
6. Maximum 5 extractions per conversation
7. Only explicit facts or strongly implied information
8. Do NOT invent or assume
9. If a fact belongs in active Markdown or already appears there, omit it

## EXAMPLES
✓ GOOD: {"content": "minor_hobby: armar teclados", "category": "habit", "subcategory": "hobby", "importance": 4}
✓ GOOD: {"content": "past_project: app de inventario", "category": "interaction", "subcategory": "project", "importance": 3}
✗ BAD: {"content": "preferred_name: Gabo", "category": "fact", "subcategory": "name", "importance": 6}  // Active-profile material
✗ BAD: {"content": "El usuario se llama Gabriel"}  // Wrong: not key:value, not English key
✗ BAD: {"content": "nombre: Gabriel"}  // Wrong: key must be English
"""

        private const val SUMMARY_PROMPT = """
Summarize this conversation as 'topic: result' (max 80 chars).
Example: 'room_migration: upgraded v31 to v32 adding finance tables'
Use same language as the conversation.
"""

        private const val RECALL_SYSTEM_PROMPT = """
You are the memory recall assistant. The user asks about something from their history, and below you will find raw entries retrieved from a secondary memory archive.

Synthesize these entries into a concise, well-structured answer in the user's language. Rules:
- Answer directly and naturally, like a personal assistant recalling something about the user.
- Group related facts; do not enumerate every entry verbatim.
- If the entries are sparse, contradictory, or incomplete, say so honestly.
- Never invent facts that are not present in the entries.
- Keep the answer under 200 words.
"""
    }
    
    /**
     * EVENT: App startup or user is actively chatting.
     * Extracts memories from conversations that have new content.
     * 
     * @param excludeConversationId Currently active conversation to skip (optional)
     * @param modelId Model to use for extraction
     * @param provider Provider to use
     */
    fun triggerExtraction(
        excludeConversationId: Long? = null,
        modelId: String,
        provider: ProviderType
    ) {
        if (foregroundRequestCount.get() > 0) {
            Log.d(TAG, "Extraction skipped: foreground chat active")
            return
        }
        // Prevent spam: max 1 batch per minute
        val now = System.currentTimeMillis()
        if (now - lastBatchExtractionTime < EXTRACTION_COOLDOWN_MS) {
            Log.d(TAG, "Extraction skipped: cooldown active")
            return
        }
        if (activeExtractionJob?.isActive == true) {
            Log.d(TAG, "Extraction skipped: another batch is active")
            return
        }

        activeExtractionJob = extractionScope.launch {
            var completed = false
            try {
                if (foregroundRequestCount.get() > 0) return@launch
                val conversations = if (excludeConversationId != null) {
                    // Normal case: exclude active conversation
                    conversationDao.getConversationsNeedingExtraction(
                        excludeConversationId = excludeConversationId,
                        limit = 5
                    )
                } else {
                    // App startup: check all conversations
                    conversationDao.getAllConversationsNeedingExtraction(limit = 10)
                }
                
                if (conversations.isEmpty()) {
                    Log.d(TAG, "No conversations need extraction")
                    return@launch
                }
                
                Log.i(TAG, "Processing ${conversations.size} conversations for memory extraction")
                
                for (conversation in conversations) {
                    processConversation(conversation, modelId, provider)
                    // Small delay to avoid overwhelming the API
                    delay(500)
                }
                completed = true
            } catch (e: CancellationException) {
                Log.d(TAG, "Memory extraction cancelled for foreground chat")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Batch extraction failed", e)
            } finally {
                if (completed) lastBatchExtractionTime = System.currentTimeMillis()
                activeExtractionJob = null
            }
        }
    }

    /** Frees the selected model immediately when a foreground request starts. */
    fun beginForegroundRequest() {
        foregroundRequestCount.incrementAndGet()
        activeExtractionJob?.cancel()
        activeExtractionJob = null
    }

    fun endForegroundRequest() {
        foregroundRequestCount.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
    }
    
    /**
     * EVENT: User just resumed/reopened a specific conversation.
     * Checks if this conversation needs re-extraction due to new messages.
     * 
     * @return true if extraction was performed
     */
    suspend fun checkConversationOnResume(
        conversationId: Long,
        modelId: String,
        provider: ProviderType
    ): Boolean {
        if (foregroundRequestCount.get() > 0) return false
        val conversation = conversationDao.getConversationById(conversationId) ?: return false
        
        // Check if there are new messages since last extraction
        if (!conversation.hasNewContent()) {
            return false
        }
        
        // Verify there are enough new messages
        val newMessageCount = conversation.lastMemoryExtraction?.let { lastExtraction ->
            conversationDao.countMessagesSince(conversationId, lastExtraction)
        } ?: Int.MAX_VALUE
        
        if (newMessageCount < MIN_MESSAGES_FOR_EXTRACTION) {
            Log.d(TAG, "Conversation $conversationId has only $newMessageCount new messages, skipping")
            return false
        }
        
        return processConversation(conversation, modelId, provider)
    }
    
    /**
     * Processes a single conversation: extracts memories and updates timestamp.
     * 
     * @return true if extraction succeeded
     */
    private suspend fun processConversation(
        conversation: ConversationEntity,
        modelId: String,
        provider: ProviderType
    ): Boolean {
        return try {
            Log.d(TAG, "Extracting memories from conversation ${conversation.id}")
            val removedDuplicates = secondaryMemoryStore.removeActiveDuplicates()
            if (removedDuplicates > 0) {
                Log.i(TAG, "Removed $removedDuplicates secondary memories duplicated in active Markdown")
            }
            
            // Get messages since last extraction (or all if never extracted)
            val allMessages = messageDao.getMessagesForConversation(conversation.id).first()
            val messagesToAnalyze = if (conversation.lastMemoryExtraction != null) {
                // Only get messages newer than last extraction
                allMessages.filter { 
                    it.role == "USER" || it.role == "ASSISTANT" 
                }.filter { it.timestamp > conversation.lastMemoryExtraction!! }
                    .takeLast(MAX_MESSAGES_PER_EXTRACTION)
            } else {
                // Never extracted: get last N messages
                allMessages.filter { 
                    it.role == "USER" || it.role == "ASSISTANT" 
                }.takeLast(MAX_MESSAGES_PER_EXTRACTION)
            }
            
            if (messagesToAnalyze.size < MIN_MESSAGES_FOR_EXTRACTION) {
                // Mark as processed anyway to avoid re-checking
                conversationDao.updateLastMemoryExtraction(conversation.id)
                return false
            }
            
            // Call LLM
            val extractedMemories = extractWithLLM(messagesToAnalyze, modelId, provider)
            
            // Save to database
            var savedCount = 0
            for (memory in extractedMemories) {
                if (saveMemory(memory)) savedCount++
            }
            
            // Also generate a summary for substantial conversations
            if (allMessages.size >= 10 && conversation.lastMemoryExtraction == null) {
                if (generateConversationSummary(conversation, allMessages, modelId, provider)) {
                    savedCount++
                }
            }
            
            // Update timestamp (even if no memories found, to avoid re-processing)
            conversationDao.updateLastMemoryExtraction(conversation.id)
            
            // Enforce global cap
            enforceMemoryCap()

            if (savedCount > 0) {
                changeNotifier.memorySaved(
                    target = AgentChangeNotifier.TARGET_ARCHIVE,
                    itemCount = savedCount
                )
            }

            Log.i(TAG, "Saved $savedCount memories from conversation ${conversation.id}")
            true
            
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract from conversation ${conversation.id}", e)
            // Don't update timestamp on failure, will retry next time
            false
        }
    }
    
    /**
     * Calls LLM to extract memories from messages.
     */
    private suspend fun extractWithLLM(
        messages: List<MessageEntity>,
        modelId: String,
        provider: ProviderType
    ): List<MemoryExtraction> {
        runtimeContextProvider.refreshIdentityFromMemory()
        val credentials = providerCredentialResolver.resolve(provider) ?: return emptyList()
        val client = aiClientFactory.createClient(provider, credentials.apiKey, credentials.baseUrl)
        
        val conversationText = messages.joinToString("\n") { msg ->
            val role = when (msg.role) {
                "USER" -> "User"
                "ASSISTANT" -> "Assistant"
                else -> "System"
            }
            "$role: ${msg.content.take(800)}"  // Limit message length
        }
        
        val result = client.chat(
            model = modelId,
            messages = listOf(ChatMessage(role = "user", content = conversationText)),
            systemPrompt = runtimeContextProvider.enrich(
                EXTRACTION_SYSTEM_PROMPT,
                "Memory extractor",
                "Internal memory maintenance"
            ),
            temperature = 0.1f,
            maxTokens = 1024
        )
        
        val response = result.getOrElse { throw it }
        if (response.isBlank()) {
            throw IllegalStateException("Memory extraction model returned an empty response")
        }
        return parseExtractions(response)
    }
    
    /**
     * Parses LLM response into structured extractions.
     */
    private fun parseExtractions(response: String): List<MemoryExtraction> {
        return try {
            val jsonStr = response.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            
            if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
            
            val array = JsonParser.parseString(jsonStr).asJsonArray
            array.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    MemoryExtraction(
                        content = obj.get("content")?.asString ?: return@mapNotNull null,
                        category = obj.get("category")?.asString ?: "fact",
                        subcategory = obj.get("subcategory")?.asString ?: "",
                        importance = obj.get("importance")?.asInt
                            ?.coerceIn(1, SecondaryMemoryStore.MAX_SECONDARY_IMPORTANCE) ?: 4
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse extraction element", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse extraction response: $response", e)
            emptyList()
        }
    }
    
    /**
     * Saves a single memory, handling deduplication.
     */
    private suspend fun saveMemory(extraction: MemoryExtraction): Boolean {
        return secondaryMemoryStore.save(
            content = extraction.content,
            category = extraction.category,
            subcategory = extraction.subcategory,
            importance = extraction.importance,
            source = SecondaryMemoryStore.SOURCE_SECONDARY_EXTRACTION
        ).changed
    }
    
    /**
     * Generates a summary for a substantial conversation.
     */
    private suspend fun generateConversationSummary(
        conversation: ConversationEntity,
        messages: List<MessageEntity>,
        modelId: String,
        provider: ProviderType
    ): Boolean {
        return try {
            val userAssistantMsgs = messages.filter { 
                it.role == "USER" || it.role == "ASSISTANT" 
            }
            
            if (userAssistantMsgs.size < 10) return false
            runtimeContextProvider.refreshIdentityFromMemory()
            
            val credentials = providerCredentialResolver.resolve(provider) ?: return false
            val client = aiClientFactory.createClient(provider, credentials.apiKey, credentials.baseUrl)
            
            val conversationText = userAssistantMsgs.joinToString("\n") { msg ->
                val role = if (msg.role == "USER") "User" else "Assistant"
                "$role: ${msg.content.take(300)}"
            }
            
            val result = client.chat(
                model = modelId,
                messages = listOf(ChatMessage(role = "user", content = conversationText)),
                systemPrompt = runtimeContextProvider.enrich(
                    SUMMARY_PROMPT,
                    "Conversation summarizer",
                    "Internal memory maintenance"
                ),
                temperature = 0.1f,
                maxTokens = 256
            )
            
            val summary = result.getOrNull()?.trim() ?: return false
            if (summary.length < 10) return false
            
            val changed = secondaryMemoryStore.save(
                content = summary,
                category = "interaction",
                subcategory = "conversation_summary",
                importance = 2,
                source = "summary"
            ).changed
            
            memoryDao.deleteOldestSummaries(MAX_CONVERSATION_SUMMARIES)
            Log.i(TAG, "Saved conversation summary")
            changed
            
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Summary generation failed (non-critical)", e)
            false
        }
    }
    
    /**
     * Searches secondary memory (FTS) for [query] and asks the LLM to synthesize
     * the raw entries into a concise narrative. Returns null when nothing is found
     * or the summarization fails. Access counts are bumped for the retrieved entries.
     */
    suspend fun recallAndSummarize(query: String, modelId: String, provider: ProviderType): String? {
        val ftsQuery = query.trim().split("\\s+".toRegex())
            .filter { it.length > 1 }
            .joinToString(" ") { "$it*" }
        if (ftsQuery.isBlank()) return null

        val results = try {
            memoryDao.searchFts(ftsQuery, RECALL_MAX_RESULTS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FTS recall query failed: '$query'", e)
            emptyList()
        }
        if (results.isEmpty()) return null

        val now = System.currentTimeMillis()
        results.forEach { memoryDao.incrementAccess(it.id, now) }

        return try {
            runtimeContextProvider.refreshIdentityFromMemory()
            val credentials = providerCredentialResolver.resolve(provider) ?: return null
            val client = aiClientFactory.createClient(provider, credentials.apiKey, credentials.baseUrl)

            val memoryText = results.joinToString("\n") { m ->
                "- ${m.content} (categoría: ${m.category}, importancia: ${m.importance})"
            }
            val result = client.chat(
                model = modelId,
                messages = listOf(ChatMessage(role = "user", content = "Consulta: $query\n\nMemorias encontradas:\n$memoryText")),
                systemPrompt = runtimeContextProvider.enrich(
                    RECALL_SYSTEM_PROMPT,
                    "Memory recall",
                    "Internal memory maintenance"
                ),
                temperature = 0.3f,
                maxTokens = 512
            )
            result.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Recall summarization failed (non-critical)", e)
            null
        }
    }

    /**
     * Enforces global memory cap.
     */
    private suspend fun enforceMemoryCap() {
        val total = memoryDao.count()
        if (total > MAX_TOTAL_MEMORIES) {
            val excess = total - MAX_TOTAL_MEMORIES
            memoryDao.deleteLowestValue(excess)
            Log.i(TAG, "Enforced memory cap: removed $excess memories")
        }
    }
    
    data class MemoryExtraction(
        val content: String,
        val category: String,
        val subcategory: String,
        val importance: Int
    )
}
