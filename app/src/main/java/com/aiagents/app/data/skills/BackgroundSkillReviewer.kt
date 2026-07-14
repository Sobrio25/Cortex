package com.aiagents.app.data.skills

import com.aiagents.app.data.local.LocalModelRepository
import com.aiagents.app.data.local.LocalLLMClient
import com.aiagents.app.data.remote.AIClientFactory
import com.aiagents.app.data.remote.ChatMessage
import com.aiagents.app.data.runtime.RuntimeContextProvider
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.SkillDraftInput
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background reviewer agent. It prefers an on-device model with no tools/network and falls back
 * to the conservative deterministic reviewer when local inference is unavailable or inconclusive.
 */
@Singleton
class BackgroundSkillReviewer @Inject constructor(
    private val localModelRepository: LocalModelRepository,
    private val aiClientFactory: AIClientFactory,
    private val runtimeContextProvider: RuntimeContextProvider
) {
    suspend fun review(redactedTranscript: String): LocalSkillCandidate? {
        val localModel = localModelRepository.getDownloadedModels().firstOrNull()
            ?: return LocalSkillReviewer.review(redactedTranscript)

        val candidate = runCatching {
            runtimeContextProvider.refreshIdentityFromMemory()
            val client = aiClientFactory.createClient(ProviderType.LOCAL, "", null)
            try {
                val response = client.chat(
                    model = localModel.id,
                    messages = listOf(ChatMessage("user", redactedTranscript)),
                    systemPrompt = runtimeContextProvider.enrich(
                        basePrompt = REVIEW_PROMPT,
                        agentName = "Skill review agent",
                        agentRole = "Local background workflow reviewer"
                    ),
                    temperature = 0.1f,
                    maxTokens = 1_024
                ).getOrThrow()
                parseCandidate(response)
            } finally {
                (client as? LocalLLMClient)?.unloadModel()
            }
        }.getOrNull()

        return candidate ?: LocalSkillReviewer.review(redactedTranscript)
    }

    private fun parseCandidate(raw: String): LocalSkillCandidate? {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (cleaned.equals("null", ignoreCase = true) || cleaned.isBlank()) return null
        val json = JsonParser.parseString(cleaned).asJsonObject
        fun field(name: String): String = json.get(name)?.asString?.trim().orEmpty()
        val draft = SkillDraftInput(
            name = field("name"),
            description = field("description"),
            whenToUse = field("when_to_use"),
            instructions = field("instructions")
        )
        // Apply the same strict limits used by persistence before accepting model output.
        val validated = com.aiagents.app.data.repository.SkillRepository.validate(draft)
        return LocalSkillCandidate(
            draft = validated,
            reason = field("reason").take(300).ifBlank {
                "El agente local detectó un flujo repetible."
            }
        )
    }

    private companion object {
        val REVIEW_PROMPT = """
            You review a bounded, already-redacted conversation to decide whether the user repeated
            a useful workflow that should become a reusable skill. You have no tools and must not
            perform any action. Be conservative: if there are not at least two substantially similar
            user intents, return exactly null.

            Otherwise return one JSON object only with: name, description, when_to_use, instructions,
            reason. The skill must be general, actionable, safe, contain no personal data, credentials,
            URLs, private paths, or transcript quotes, and must require confirmation before external or
            irreversible actions. It will be stored as DRAFT for manual review, never activated.
        """.trimIndent()
    }
}
