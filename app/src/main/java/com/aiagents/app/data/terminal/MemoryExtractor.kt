package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.auth.ProviderCredentialResolver
import com.aiagents.app.data.local.ConversationDao
import com.aiagents.app.data.local.MemoryDao
import com.aiagents.app.data.local.MessageDao
import com.aiagents.app.data.model.ConversationEntity
import com.aiagents.app.data.model.MemoryEntity
import com.aiagents.app.data.model.MessageEntity
import com.aiagents.app.data.remote.AIClientFactory
import com.aiagents.app.data.remote.ChatMessage
import com.aiagents.app.data.runtime.RuntimeContextProvider
import com.aiagents.app.domain.model.ProviderType
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
    private val runtimeContextProvider: RuntimeContextProvider
) {
    private val extractionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    @Volatile
    private var lastBatchExtractionTime = 0L
    
    companion object {
        private const val TAG = "MemoryExtractor"
        private const val MAX_MESSAGES_PER_EXTRACTION = 15
        private const val MIN_MESSAGES_FOR_EXTRACTION = 4
        private const val MAX_TOTAL_MEMORIES = 500  // Increased from 200
        private const val MAX_CONVERSATION_SUMMARIES = 50  // Increased proportionally
        private const val EXTRACTION_COOLDOWN_MS = 60_000L  // 1 minute between batches
        
        /**
         * Structured prompt that teaches the LLM the exact database schema
         * to generate searchable memories with proper key conventions.
         */
        private const val EXTRACTION_SYSTEM_PROMPT = """
You are a Memory Extraction Specialist. Analyze conversations and extract factual information about the user into a structured memory database.

## DATABASE SCHEMA (cortex_memories)
Fields you populate via extraction:
- content: "key: value" format (REQUIRED). 
  • Key: English snake_case (searchable by other agents)
  • Value: User's language as stated in conversation
- category: fact | preference | habit | interaction | relationship
- subcategory: Topic for grouping (e.g., "name", "food", "work")
- importance: 1-10 (determines priority in agent prompts)

## KEY CONVENTIONS (CRITICAL - Use these exact keys)
Personal Identity:
  name, preferred_name, nickname, age, birthday, birth_year
  
Location & Contact:
  city, country, timezone, location, email, phone
  
Work & Education:
  job_title, occupation, company, industry, experience_level
  education_level, university, field_of_study
  
Preferences:
  favorite_food, favorite_music, favorite_movie, favorite_book, 
  favorite_color, preferred_language, communication_style
  
Habits & Lifestyle:
  work_schedule, sleep_schedule, exercise_routine, dietary_restrictions,
  hobbies, pets, family_status
  
Technical (if applicable):
  programming_languages, preferred_stack, experience_with, tools_used

## OUTPUT FORMAT
Return ONLY a JSON array. Use [] if nothing to extract.

[
  {
    "content": "key: value",
    "category": "fact|preference|habit|interaction|relationship",
    "subcategory": "topic",
    "importance": 1-10
  }
]

## EXTRACTION RULES
1. Content MUST be "key: value" format, max 60 characters
2. Key MUST be English snake_case from conventions above
3. Value in user's original language
4. Subcategory should match the key's topic category
5. Importance scale:
   10 = Core identity (name, critical preferences)
   8-9 = Strong preferences, important context
   5-7 = Useful context
   3-4 = Minor details
6. Maximum 5 extractions per conversation
7. Only explicit facts or strongly implied information
8. Do NOT invent or assume

## EXAMPLES
✓ GOOD: {"content": "name: Gabriel Hernández", "category": "fact", "subcategory": "name", "importance": 10}
✓ GOOD: {"content": "favorite_food: tacos al pastor", "category": "preference", "subcategory": "food", "importance": 8}
✓ GOOD: {"content": "work_schedule: nocturno", "category": "habit", "subcategory": "work_schedule", "importance": 6}
✗ BAD: {"content": "El usuario se llama Gabriel"}  // Wrong: not key:value, not English key
✗ BAD: {"content": "nombre: Gabriel"}  // Wrong: key must be English
"""

        private const val SUMMARY_PROMPT = """
Summarize this conversation as 'topic: result' (max 80 chars).
Example: 'room_migration: upgraded v31 to v32 adding finance tables'
Use same language as the conversation.
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
        // Prevent spam: max 1 batch per minute
        val now = System.currentTimeMillis()
        if (now - lastBatchExtractionTime < EXTRACTION_COOLDOWN_MS) {
            Log.d(TAG, "Extraction skipped: cooldown active")
            return
        }
        lastBatchExtractionTime = now
        
        extractionScope.launch {
            try {
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
                
            } catch (e: Exception) {
                Log.e(TAG, "Batch extraction failed", e)
            }
        }
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
                generateConversationSummary(conversation, allMessages, modelId, provider)
            }
            
            // Update timestamp (even if no memories found, to avoid re-processing)
            conversationDao.updateLastMemoryExtraction(conversation.id)
            
            // Enforce global cap
            enforceMemoryCap()
            
            Log.i(TAG, "Saved $savedCount memories from conversation ${conversation.id}")
            true
            
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
        
        return parseExtractions(result.getOrNull() ?: "")
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
                        importance = obj.get("importance")?.asInt?.coerceIn(1, 10) ?: 5
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
        // Check for duplicates by key prefix
        val contentKey = if (extraction.content.contains(":")) {
            extraction.content.substringBefore(":").trim()
        } else null
        
        val existing = if (contentKey != null && extraction.subcategory.isNotBlank()) {
            memoryDao.getByCategoryAndSubcategory(extraction.category, extraction.subcategory)
                .filter { it.content.startsWith("$contentKey:") }
        } else {
            // Fallback to FTS for non key:value content
            val ftsQuery = extraction.content.trim().split("\\s+".toRegex()).take(4)
                .filter { it.length > 1 }.joinToString(" ") { "$it*" }
            try {
                if (ftsQuery.isNotBlank()) {
                    memoryDao.searchFts(ftsQuery, 3)
                        .filter { it.category == extraction.category && it.subcategory == extraction.subcategory }
                } else emptyList()
            } catch (_: Exception) { emptyList() }
        }
        
        val now = System.currentTimeMillis()
        
        if (existing.isNotEmpty()) {
            // Update existing
            val match = existing.first()
            if (match.content != extraction.content) {
                memoryDao.update(match.copy(
                    content = extraction.content,
                    importance = maxOf(match.importance, extraction.importance),
                    confidence = 1.0f,
                    updatedAt = now,
                    lastAccessedAt = now
                ))
                return true
            }
        } else {
            // Insert new
            memoryDao.insert(MemoryEntity(
                content = extraction.content,
                category = extraction.category,
                subcategory = extraction.subcategory,
                importance = extraction.importance,
                confidence = 1.0f,
                source = "extraction",
                createdAt = now,
                updatedAt = now,
                lastAccessedAt = now
            ))
            return true
        }
        return false
    }
    
    /**
     * Generates a summary for a substantial conversation.
     */
    private suspend fun generateConversationSummary(
        conversation: ConversationEntity,
        messages: List<MessageEntity>,
        modelId: String,
        provider: ProviderType
    ) {
        try {
            val userAssistantMsgs = messages.filter { 
                it.role == "USER" || it.role == "ASSISTANT" 
            }
            
            if (userAssistantMsgs.size < 10) return
            runtimeContextProvider.refreshIdentityFromMemory()
            
            val credentials = providerCredentialResolver.resolve(provider) ?: return
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
            
            val summary = result.getOrNull()?.trim() ?: return
            if (summary.length < 10) return
            
            val now = System.currentTimeMillis()
            memoryDao.insert(MemoryEntity(
                content = summary,
                category = "interaction",
                subcategory = "conversation_summary",
                importance = 2,
                source = "summary",
                createdAt = now,
                updatedAt = now,
                lastAccessedAt = now
            ))
            
            memoryDao.deleteOldestSummaries(MAX_CONVERSATION_SUMMARIES)
            Log.i(TAG, "Saved conversation summary")
            
        } catch (e: Exception) {
            Log.w(TAG, "Summary generation failed (non-critical)", e)
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
