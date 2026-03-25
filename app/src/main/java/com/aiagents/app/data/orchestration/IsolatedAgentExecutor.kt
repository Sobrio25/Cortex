package com.aiagents.app.data.orchestration

import android.util.Log
import com.aiagents.app.data.local.ConversationDao
import com.aiagents.app.data.local.MessageDao
import com.aiagents.app.data.model.ConversationEntity
import com.aiagents.app.data.model.MessageEntity
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.Conversation
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolResult
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.data.remote.StreamingChunk
import com.aiagents.app.data.remote.extractThinkingFromContent
import com.aiagents.app.data.remote.removeThinkingTags
import javax.inject.Inject
import javax.inject.Singleton

sealed class SubAgentProgress {
    data class Started(val agentName: String, val subConversationId: Long) : SubAgentProgress()
    data class Streaming(val agentName: String, val content: String) : SubAgentProgress()
    data class ToolUse(val agentName: String, val toolName: String) : SubAgentProgress()
    data class Completed(val agentName: String, val finalResult: String, val subConversationId: Long) : SubAgentProgress()
    data class Failed(val agentName: String, val error: String) : SubAgentProgress()
}

data class IsolatedExecutionResult(
    val finalContent: String,
    val subConversationId: Long,
    val success: Boolean,
    val agentName: String
)

@Singleton
class IsolatedAgentExecutor @Inject constructor(
    private val repository: AgentRepository,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    companion object {
        private const val TAG = "IsolatedAgentExecutor"
        private const val MAX_TOOL_CALL_DEPTH = 25
        /** Maximum nesting depth for sub-agent delegation (prevents infinite recursion) */
        const val MAX_DELEGATION_DEPTH = 3
    }

    /**
     * Executes an agent's task in an isolated sub-conversation.
     * All intermediate tool calls and responses are stored in the sub-conversation,
     * keeping the parent conversation clean.
     *
     * @param agent The agent to execute
     * @param taskDescription The task to perform
     * @param context Minimal context extracted from the parent conversation
     * @param parentConversationId The parent conversation's ID
     * @param workspaceId The workspace ID
     * @param overrideModel Model ID to use (without provider prefix)
     * @param overrideProvider Provider type
     * @param workspacePath Workspace folder path for tool execution
     * @param toolExecutor Lambda that executes a tool call and returns a ToolResult
     * @param progressCallback Callback for reporting progress to the UI
     * @param delegationDepth Current delegation nesting depth (0 = top-level)
     */
    suspend fun executeInIsolation(
        agent: Agent,
        taskDescription: String,
        context: String,
        parentConversationId: Long,
        workspaceId: Long,
        overrideModel: String,
        overrideProvider: ProviderType?,
        workspacePath: String,
        toolExecutor: suspend (Agent, ToolCall, String, Long) -> ToolResult,
        progressCallback: (SubAgentProgress) -> Unit,
        delegationDepth: Int = 0
    ): IsolatedExecutionResult {
        // Prevent infinite recursive delegation
        if (delegationDepth >= MAX_DELEGATION_DEPTH) {
            Log.w(TAG, "Max delegation depth ($MAX_DELEGATION_DEPTH) reached for ${agent.name}, executing without further delegation")
        }

        // 1. Create sub-conversation in Room
        val subConversation = Conversation(
            workspaceId = workspaceId,
            title = "[${agent.name}] ${taskDescription.take(80)}",
            parentConversationId = parentConversationId,
            delegationAgentName = agent.name,
            delegationTask = taskDescription.take(500),
            status = "active"
        )
        val subConversationId = repository.createConversation(subConversation)
        Log.d(TAG, "Created sub-conversation $subConversationId for agent ${agent.name} (depth=$delegationDepth)")

        progressCallback(SubAgentProgress.Started(agent.name, subConversationId))

        return try {
            val result = executeLoop(
                agent = agent,
                taskDescription = taskDescription,
                context = context,
                subConversationId = subConversationId,
                workspaceId = workspaceId,
                overrideModel = overrideModel,
                overrideProvider = overrideProvider,
                workspacePath = workspacePath,
                toolExecutor = toolExecutor,
                progressCallback = progressCallback
            )

            // Mark sub-conversation as completed
            conversationDao.updateConversationStatus(subConversationId, "completed")

            progressCallback(SubAgentProgress.Completed(agent.name, result, subConversationId))

            IsolatedExecutionResult(
                finalContent = result,
                subConversationId = subConversationId,
                success = true,
                agentName = agent.name
            )
        } catch (e: Exception) {
            Log.e(TAG, "Isolated execution failed for ${agent.name}", e)
            conversationDao.updateConversationStatus(subConversationId, "failed")
            val errorMsg = "Error: ${e.message}"
            progressCallback(SubAgentProgress.Failed(agent.name, errorMsg))

            IsolatedExecutionResult(
                finalContent = errorMsg,
                subConversationId = subConversationId,
                success = false,
                agentName = agent.name
            )
        }
    }

    private suspend fun executeLoop(
        agent: Agent,
        taskDescription: String,
        context: String,
        subConversationId: Long,
        workspaceId: Long,
        overrideModel: String,
        overrideProvider: ProviderType?,
        workspacePath: String,
        toolExecutor: suspend (Agent, ToolCall, String, Long) -> ToolResult,
        progressCallback: (SubAgentProgress) -> Unit
    ): String {
        // Build the initial user message with context + task
        val userContent = buildString {
            if (context.isNotBlank()) {
                appendLine("## Contexto")
                appendLine(context)
                appendLine()
            }
            appendLine("## Tarea")
            appendLine(taskDescription)
        }

        // Save the initial user message in the sub-conversation
        val userMessage = Message(role = MessageRole.USER, content = userContent)
        repository.addMessage(workspaceId, subConversationId, userMessage, agent.id)

        // Build message list for the API (isolated - only sub-conversation messages)
        val isolatedMessages = mutableListOf(userMessage)
        var lastContent = ""

        for (depth in 0 until MAX_TOOL_CALL_DEPTH) {
            // Call API with streaming
            val contentBuilder = StringBuilder()
            val reasoningBuilder = StringBuilder()
            var toolCalls: List<ToolCall>? = null
            var hasError = false

            val streamFlow = repository.chatWithToolsStreaming(
                agent = agent,
                messages = isolatedMessages,
                overrideModel = overrideModel,
                overrideProvider = overrideProvider,
                enableTerminal = agent.enableTerminal,
                workspaceFolderPath = workspacePath
            )

            streamFlow.collect { chunk ->
                if (chunk.error != null) {
                    hasError = true
                    throw Exception(chunk.error)
                }
                chunk.content?.let { delta ->
                    contentBuilder.append(delta)
                    progressCallback(SubAgentProgress.Streaming(agent.name, contentBuilder.toString()))
                }
                chunk.reasoning?.let { delta ->
                    reasoningBuilder.append(delta)
                }
                if (chunk.done) {
                    toolCalls = chunk.toolCalls
                }
            }

            val fullContent = contentBuilder.toString()
            val fullReasoning = reasoningBuilder.toString().ifBlank { null }

            // Handle <think> tags
            val thinkingFromTags = extractThinkingFromContent(fullContent)
            val cleanedContent = removeThinkingTags(fullContent) ?: fullContent
            val finalReasoning = fullReasoning ?: thinkingFromTags

            // Save assistant response in sub-conversation
            val assistantMessage = Message(
                role = MessageRole.ASSISTANT,
                content = cleanedContent,
                toolCalls = toolCalls ?: emptyList(),
                reasoning = finalReasoning
            )
            repository.addMessage(workspaceId, subConversationId, assistantMessage, agent.id)
            isolatedMessages.add(assistantMessage)

            lastContent = cleanedContent

            // If no tool calls, we're done
            if (toolCalls.isNullOrEmpty()) {
                break
            }

            // Execute tool calls and save results in sub-conversation
            val toolResults = mutableListOf<Message>()
            for (tc in toolCalls!!) {
                progressCallback(SubAgentProgress.ToolUse(agent.name, tc.function.name))
                Log.d(TAG, "[${agent.name}] Executing tool: ${tc.function.name}")

                val result = toolExecutor(agent, tc, workspacePath, workspaceId)

                val toolMessage = Message(
                    role = MessageRole.TOOL,
                    content = result.content,
                    toolResults = listOf(result)
                )
                repository.addMessage(workspaceId, subConversationId, toolMessage, agent.id)
                isolatedMessages.add(toolMessage)
                toolResults.add(toolMessage)
            }
        }

        return lastContent
    }
}
