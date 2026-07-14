package com.aiagents.app.data.orchestration

import android.util.Log
import com.aiagents.app.data.local.ConversationDao
import com.aiagents.app.data.remote.extractThinkingFromContent
import com.aiagents.app.data.remote.removeThinkingTags
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.Conversation
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.domain.model.SubagentBudget
import com.aiagents.app.domain.model.SubagentExecutionMode
import com.aiagents.app.domain.model.SubagentFailurePolicy
import com.aiagents.app.domain.model.SubagentResult
import com.aiagents.app.domain.model.SubagentRole
import com.aiagents.app.domain.model.SubagentTaskEnvelope
import com.aiagents.app.domain.model.SubagentToolPermission
import com.aiagents.app.domain.model.SubagentWorkspacePolicy
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolResult
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

sealed class SubAgentProgress {
    abstract val taskId: String
    abstract val agentName: String

    data class Queued(override val taskId: String, override val agentName: String) : SubAgentProgress()
    data class Started(
        override val taskId: String,
        override val agentName: String,
        val subConversationId: Long
    ) : SubAgentProgress()
    data class Streaming(
        override val taskId: String,
        override val agentName: String,
        val content: String
    ) : SubAgentProgress()
    data class ToolUse(
        override val taskId: String,
        override val agentName: String,
        val toolName: String
    ) : SubAgentProgress()
    data class Completed(
        override val taskId: String,
        override val agentName: String,
        val finalResult: String,
        val subConversationId: Long
    ) : SubAgentProgress()
    data class Failed(
        override val taskId: String,
        override val agentName: String,
        val error: String
    ) : SubAgentProgress()
    data class Cancelled(
        override val taskId: String,
        override val agentName: String,
        val reason: String
    ) : SubAgentProgress()
}

data class IsolatedExecutionResult(
    val finalContent: String,
    val subConversationId: Long,
    val success: Boolean,
    val agentName: String,
    val taskId: String = "",
    val summary: String = finalContent,
    val filesModified: List<String> = emptyList(),
    val testsRun: List<String> = emptyList(),
    val exitReason: String = if (success) "completed" else "failed",
    val errorCode: String? = null
)

private data class LoopResult(
    val content: String,
    val filesModified: List<String>,
    val testsRun: List<String>,
    val exitReason: String
)

/**
 * Carries content that was already streamed to the UI when the provider closes the
 * connection before emitting a terminal chunk. Without this wrapper the outer
 * execution boundary can only see the transport error and discards useful work.
 */
private class PartialSubagentResponseException(
    val partialContent: String,
    val filesModified: List<String>,
    val testsRun: List<String>,
    cause: Throwable
) : Exception(cause.message, cause)

internal object SubagentRetryPolicy {
    fun isRateLimited(error: Throwable): Boolean =
        generateSequence(error as Throwable?) { it.cause }.any { cause ->
            val message = cause.message.orEmpty()
            message.contains("429") || message.contains("rate limit", ignoreCase = true)
        }

    fun maxRetries(error: Throwable): Int = if (isRateLimited(error)) 3 else 2

    fun delayMillis(error: Throwable, retryNumber: Int): Long {
        val attempt = retryNumber.coerceAtLeast(1)
        return if (isRateLimited(error)) {
            (2_000L * (1L shl (attempt - 1))).coerceAtMost(8_000L)
        } else {
            500L * (1L shl (attempt - 1))
        }
    }
}

@Singleton
class IsolatedAgentExecutor @Inject constructor(
    private val repository: AgentRepository,
    private val conversationDao: ConversationDao,
    private val runtime: SubagentRuntime,
    private val scheduler: SubagentScheduler
) {
    companion object {
        private const val TAG = "IsolatedAgentExecutor"
        const val DEFAULT_MAX_ITERATIONS = 25
        const val MAX_DELEGATION_DEPTH = 3
    }

    suspend fun executeInIsolation(
        agent: Agent,
        task: SubagentTaskEnvelope,
        overrideModel: String,
        overrideProvider: ProviderType?,
        workspacePath: String,
        toolExecutor: suspend (SubagentTaskEnvelope, Agent, ToolCall, String, Long, Long) -> ToolResult,
        progressCallback: (SubAgentProgress) -> Unit
    ): IsolatedExecutionResult {
        require(task.depth <= MAX_DELEGATION_DEPTH) {
            "Maximum subagent depth ($MAX_DELEGATION_DEPTH) exceeded"
        }

        runtime.enqueue(task)
        progressCallback(SubAgentProgress.Queued(task.taskId, task.agentName))
        val currentJob = currentCoroutineContext()[Job]
            ?: error("Subagent execution requires a coroutine Job")
        runtime.register(task.taskId, currentJob)

        return try {
            scheduler.run(task) {
                executeScheduled(
                    agent = agent,
                    task = task,
                    overrideModel = overrideModel,
                    overrideProvider = overrideProvider,
                    workspacePath = workspacePath,
                    toolExecutor = toolExecutor,
                    progressCallback = progressCallback
                )
            }
        } catch (cancelled: CancellationException) {
            runtime.cancelled(task, cancelled.message ?: "Cancelled")
            progressCallback(
                SubAgentProgress.Cancelled(task.taskId, task.agentName, cancelled.message ?: "Cancelled")
            )
            throw cancelled
        }
    }

    /** Compatibility adapter for old internal call sites while they migrate to task envelopes. */
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
        val modelKey = "${overrideProvider?.name ?: ProviderType.LOCAL.name}|$overrideModel"
        val workspacePolicy = SubagentWorkspacePolicy.WRITE_EXCLUSIVE
        val allowedTools = SubagentCapabilityPolicy.allowedTools(
            workspacePolicy = workspacePolicy,
            role = SubagentRole.LEAF,
            depth = delegationDepth + 1,
            maxDepth = MAX_DELEGATION_DEPTH
        )
        val task = SubagentTaskEnvelope(
            parentConversationId = parentConversationId,
            workspaceId = workspaceId,
            agentId = agent.id,
            agentName = agent.name,
            goal = taskDescription,
            context = context,
            mode = SubagentExecutionMode.PARALLEL,
            failurePolicy = SubagentFailurePolicy.FAIL_FAST,
            role = SubagentRole.LEAF,
            depth = delegationDepth + 1,
            modelKey = modelKey,
            allowedTools = allowedTools,
            toolPermissions = SubagentCapabilityPolicy.permissions(allowedTools),
            workspacePolicy = workspacePolicy,
            budget = SubagentBudget(maxIterations = DEFAULT_MAX_ITERATIONS)
        )
        return executeInIsolation(
            agent = agent,
            task = task,
            overrideModel = overrideModel,
            overrideProvider = overrideProvider,
            workspacePath = workspacePath,
            toolExecutor = { _, delegatedAgent, toolCall, path, delegatedWorkspaceId, _ ->
                toolExecutor(delegatedAgent, toolCall, path, delegatedWorkspaceId)
            },
            progressCallback = progressCallback
        )
    }

    private suspend fun executeScheduled(
        agent: Agent,
        task: SubagentTaskEnvelope,
        overrideModel: String,
        overrideProvider: ProviderType?,
        workspacePath: String,
        toolExecutor: suspend (SubagentTaskEnvelope, Agent, ToolCall, String, Long, Long) -> ToolResult,
        progressCallback: (SubAgentProgress) -> Unit
    ): IsolatedExecutionResult {
        val startedAt = System.currentTimeMillis()
        val subConversation = Conversation(
            workspaceId = task.workspaceId,
            title = "[${agent.name}] ${task.goal.take(80)}",
            parentConversationId = task.parentConversationId,
            delegationAgentName = agent.name,
            delegationTask = task.goal.take(500),
            status = "active"
        )
        val subConversationId = repository.createConversation(subConversation)
        runtime.markRunning(task.taskId, subConversationId)
        progressCallback(SubAgentProgress.Started(task.taskId, agent.name, subConversationId))
        Log.d(TAG, "Started task=${task.taskId} agent=${agent.name} depth=${task.depth}")

        return try {
            val loop = executeLoop(
                agent = agent,
                task = task,
                subConversationId = subConversationId,
                overrideModel = overrideModel,
                overrideProvider = overrideProvider,
                workspacePath = workspacePath,
                toolExecutor = toolExecutor,
                progressCallback = progressCallback
            )
            val completedWithinBudget = loop.exitReason != "iteration_budget_exhausted"
            conversationDao.updateConversationStatus(
                subConversationId,
                if (completedWithinBudget) "completed" else "failed"
            )
            val result = SubagentResult(
                taskId = task.taskId,
                agentName = agent.name,
                subConversationId = subConversationId,
                success = completedWithinBudget,
                finalContent = loop.content,
                summary = loop.content.take(task.budget.maxResultChars),
                filesModified = loop.filesModified,
                testsRun = loop.testsRun,
                exitReason = loop.exitReason,
                errorCode = if (completedWithinBudget) null else "ITERATION_BUDGET_EXHAUSTED",
                errorMessage = if (completedWithinBudget) null else
                    "The subagent reached its iteration budget before returning a final answer.",
                startedAt = startedAt
            )
            runtime.complete(task, result)
            if (completedWithinBudget) {
                progressCallback(
                    SubAgentProgress.Completed(task.taskId, agent.name, loop.content, subConversationId)
                )
            } else {
                progressCallback(
                    SubAgentProgress.Failed(
                        task.taskId,
                        agent.name,
                        "Iteration budget exhausted"
                    )
                )
            }
            result.toLegacy()
        } catch (cancelled: CancellationException) {
            conversationDao.updateConversationStatus(subConversationId, "cancelled")
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Task ${task.taskId} failed for ${agent.name}", error)
            conversationDao.updateConversationStatus(subConversationId, "failed")
            val partialFailure = error as? PartialSubagentResponseException
            val rootError = partialFailure?.cause ?: error
            val message = partialFailure?.partialContent?.let { partial ->
                buildString {
                    append(partial)
                    appendLine()
                    appendLine()
                    append("[Resultado parcial recuperado: la conexión terminó antes de confirmar la respuesta completa")
                    rootError.message?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                    append("]")
                }
            } ?: "Error: ${error.message ?: error::class.java.simpleName}"
            val result = SubagentResult(
                taskId = task.taskId,
                agentName = agent.name,
                subConversationId = subConversationId,
                success = false,
                finalContent = message,
                summary = message.take(task.budget.maxResultChars),
                filesModified = partialFailure?.filesModified.orEmpty(),
                testsRun = partialFailure?.testsRun.orEmpty(),
                exitReason = if (partialFailure != null) "partial_transport_failure" else "exception",
                errorCode = rootError::class.java.simpleName,
                errorMessage = rootError.message,
                startedAt = startedAt
            )
            runtime.complete(task, result)
            progressCallback(SubAgentProgress.Failed(task.taskId, agent.name, message))
            result.toLegacy()
        }
    }

    private suspend fun executeLoop(
        agent: Agent,
        task: SubagentTaskEnvelope,
        subConversationId: Long,
        overrideModel: String,
        overrideProvider: ProviderType?,
        workspacePath: String,
        toolExecutor: suspend (SubagentTaskEnvelope, Agent, ToolCall, String, Long, Long) -> ToolResult,
        progressCallback: (SubAgentProgress) -> Unit
    ): LoopResult {
        val userContent = buildString {
            appendLine("## Goal")
            appendLine(task.goal)
            if (task.context.isNotBlank()) {
                appendLine()
                appendLine("## Relevant context")
                appendLine(task.context)
            }
            if (task.acceptanceCriteria.isNotBlank()) {
                appendLine()
                appendLine("## Acceptance criteria")
                appendLine(task.acceptanceCriteria)
            }
            appendLine()
            appendLine("## Result contract")
            appendLine("Return a concise final result. State what was completed, files changed, tests/checks run, and any remaining blocker.")
        }
        val userMessage = Message(role = MessageRole.USER, content = userContent)
        repository.addMessage(task.workspaceId, subConversationId, userMessage, agent.id)

        val isolatedMessages = mutableListOf(userMessage)
        val filesModified = linkedSetOf<String>()
        val testsRun = linkedSetOf<String>()
        var lastContent = ""
        var exitReason = "model_finished"

        repeat(task.budget.maxIterations) { iteration ->
            val contentBuilder = StringBuilder()
            val reasoningBuilder = StringBuilder()
            var toolCalls: List<ToolCall>? = null
            var streamAttempt = 0
            while (true) {
                try {
                    repository.chatWithToolsStreaming(
                        agent = agent,
                        messages = isolatedMessages,
                        overrideModel = overrideModel,
                        overrideProvider = overrideProvider,
                        enableTerminal = agent.enableTerminal,
                        workspaceFolderPath = workspacePath,
                        allowedToolNames = task.allowedTools
                    ).collect { chunk ->
                        chunk.error?.let { throw IllegalStateException(it) }
                        chunk.content?.let { delta ->
                            contentBuilder.append(delta)
                            progressCallback(
                                SubAgentProgress.Streaming(task.taskId, agent.name, contentBuilder.toString())
                            )
                        }
                        chunk.reasoning?.let(reasoningBuilder::append)
                        if (chunk.done) toolCalls = chunk.toolCalls
                    }
                    break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val rawPartial = contentBuilder.toString()
                    val cleanedPartial = (removeThinkingTags(rawPartial) ?: rawPartial).ifBlank { rawPartial }
                    if (cleanedPartial.isNotBlank()) {
                        // Persist what the user already saw before unwinding the failed stream.
                        repository.addMessage(
                            task.workspaceId,
                            subConversationId,
                            Message(
                                role = MessageRole.ASSISTANT,
                                content = cleanedPartial,
                                reasoning = reasoningBuilder.toString().ifBlank { null }
                            ),
                            agent.id
                        )
                        throw PartialSubagentResponseException(
                            partialContent = cleanedPartial,
                            filesModified = filesModified.toList(),
                            testsRun = testsRun.toList(),
                            cause = error
                        )
                    }
                    val maxRetries = SubagentRetryPolicy.maxRetries(error)
                    if (streamAttempt < maxRetries) {
                        streamAttempt += 1
                        Log.w(
                            TAG,
                            "Retrying task=${task.taskId} iteration=$iteration after empty stream failure " +
                                "($streamAttempt/$maxRetries)",
                            error
                        )
                        reasoningBuilder.clear()
                        toolCalls = null
                        kotlinx.coroutines.delay(
                            SubagentRetryPolicy.delayMillis(error, streamAttempt)
                        )
                        continue
                    }
                    throw error
                }
            }

            val fullContent = contentBuilder.toString()
            val thinkingFromTags = extractThinkingFromContent(fullContent)
            val cleanedContent = removeThinkingTags(fullContent) ?: fullContent
            val reasoning = reasoningBuilder.toString().ifBlank { null } ?: thinkingFromTags
            val assistantMessage = Message(
                role = MessageRole.ASSISTANT,
                content = cleanedContent,
                toolCalls = toolCalls.orEmpty(),
                reasoning = reasoning
            )
            repository.addMessage(task.workspaceId, subConversationId, assistantMessage, agent.id)
            isolatedMessages.add(assistantMessage)
            lastContent = cleanedContent

            if (toolCalls.isNullOrEmpty()) return LoopResult(
                content = lastContent,
                filesModified = filesModified.toList(),
                testsRun = testsRun.toList(),
                exitReason = exitReason
            )

            for (toolCall in toolCalls.orEmpty()) {
                progressCallback(SubAgentProgress.ToolUse(task.taskId, agent.name, toolCall.function.name))
                trackArtifacts(toolCall, filesModified, testsRun)
                val result = toolExecutor(
                    task,
                    agent,
                    toolCall,
                    workspacePath,
                    task.workspaceId,
                    subConversationId
                )
                val toolMessage = Message(
                    role = MessageRole.TOOL,
                    content = result.content,
                    toolResults = listOf(result)
                )
                repository.addMessage(task.workspaceId, subConversationId, toolMessage, agent.id)
                isolatedMessages.add(toolMessage)
            }
            if (iteration == task.budget.maxIterations - 1) exitReason = "iteration_budget_exhausted"
        }

        return LoopResult(lastContent, filesModified.toList(), testsRun.toList(), exitReason)
    }

    private fun trackArtifacts(
        toolCall: ToolCall,
        filesModified: MutableSet<String>,
        testsRun: MutableSet<String>
    ) {
        val args = runCatching { JsonParser.parseString(toolCall.function.arguments).asJsonObject }.getOrNull()
        if (toolCall.function.name == "write_file") {
            val path = args?.get("path")?.asString ?: args?.get("file_path")?.asString
            if (!path.isNullOrBlank()) filesModified += path
        }
        if (toolCall.function.name == "execute_command") {
            val command = args?.get("command")?.asString.orEmpty()
            if (command.contains("test", ignoreCase = true) || command.contains("check", ignoreCase = true)) {
                testsRun += command.take(500)
            }
        }
    }

    private fun SubagentResult.toLegacy(): IsolatedExecutionResult = IsolatedExecutionResult(
        finalContent = finalContent,
        subConversationId = subConversationId ?: -1L,
        success = success,
        agentName = agentName,
        taskId = taskId,
        summary = summary,
        filesModified = filesModified,
        testsRun = testsRun,
        exitReason = exitReason,
        errorCode = errorCode
    )
}
