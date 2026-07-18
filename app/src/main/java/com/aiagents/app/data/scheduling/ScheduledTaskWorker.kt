package com.aiagents.app.data.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiagents.app.MainActivity
import com.aiagents.app.R
import com.aiagents.app.data.local.ScheduledTaskDao
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.local.ChatPreferences
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.data.repository.FileRepository
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolResult
import com.aiagents.app.domain.model.ProviderType
import com.aiagents.app.data.terminal.ToolExecutionProfiles
import com.aiagents.app.data.model.ScheduledTaskEntity
import com.aiagents.app.domain.model.Conversation
import com.aiagents.app.domain.model.Agent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that executes a scheduled agent task in the background.
 * Handles a simplified tool loop (non-interactive tools only).
 */
@HiltWorker
class ScheduledTaskWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scheduledTaskDao: ScheduledTaskDao,
    private val repository: AgentRepository,
    private val fileRepository: FileRepository,
    private val securePreferences: SecurePreferences,
    private val chatPreferences: ChatPreferences,
    private val taskSchedulerManager: TaskSchedulerManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ScheduledTaskWorker"
        const val KEY_TASK_ID = "task_id"
        const val EXTRA_CONVERSATION_ID = "scheduled_task_conversation_id"
        const val EXTRA_WORKSPACE_ID = "scheduled_task_workspace_id"
        private const val CHANNEL_ID = "scheduled_tasks"
        private const val MAX_TOOL_ITERATIONS = 10
    }

    private data class AgentExecutionResult(
        val content: String,
        val agentId: Long
    )

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId < 0) return Result.failure()

        val task = scheduledTaskDao.getById(taskId)
        if (task == null || !task.enabled) {
            Log.w(TAG, "Task $taskId not found or disabled")
            return Result.success()
        }

        Log.d(TAG, "Executing scheduled task $taskId: '${task.label}'")

        return try {
            val conversationId = ensureConversation(task)
            val execution = executeAgentTask(
                task.agentName,
                task.prompt,
                task.workspaceId,
                conversationId
            )
            val resultText = execution.content
            persistExecution(task, conversationId, execution)
            val summary = resultText.take(500)

            // Schedule next run if recurring
            val nextRun = taskSchedulerManager.computeNextRun(
                task.scheduleType, task.scheduleValue, System.currentTimeMillis()
            )

            if (nextRun != null) {
                scheduledTaskDao.markExecuted(taskId, System.currentTimeMillis(), summary, nextRun)
                taskSchedulerManager.scheduleAlarm(task.copy(nextRunAt = nextRun, lastRunAt = System.currentTimeMillis()))
            } else {
                // One-time task — disable after execution
                scheduledTaskDao.markExecuted(taskId, System.currentTimeMillis(), summary, 0L)
                scheduledTaskDao.setEnabled(taskId, false)
            }

            sendNotification(task, conversationId, summary)
            Log.d(TAG, "Task $taskId completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Task $taskId failed", e)
            // Still schedule next run on failure
            val nextRun = taskSchedulerManager.computeNextRun(
                task.scheduleType, task.scheduleValue, System.currentTimeMillis()
            )
            if (nextRun != null) {
                scheduledTaskDao.markExecuted(taskId, System.currentTimeMillis(), "Error: ${e.message}", nextRun)
                taskSchedulerManager.scheduleAlarm(task.copy(nextRunAt = nextRun))
            }
            Result.retry()
        }
    }

    /**
     * Execute an agent's prompt with tool support. Simplified loop for background execution.
     */
    private suspend fun executeAgentTask(
        agentName: String?,
        prompt: String,
        workspaceId: Long,
        conversationId: Long
    ): AgentExecutionResult {
        val agent = if (agentName != null) {
            repository.getAgentByName(agentName) ?: repository.getOrchestratorAgent()
        } else {
            repository.getOrchestratorAgent()
        } ?: throw IllegalStateException("No agent available")

        val messages = mutableListOf(Message(role = MessageRole.USER, content = prompt))
        var finalContent = ""
        val workspace = repository.getWorkspaceById(workspaceId)
        val conversationModel = repository.getConversationById(conversationId)
            ?.selectedModelOverride
            .orEmpty()
        val selectedModelKey = conversationModel.takeIf { it.contains('|') }
            ?: chatPreferences.defaultModel.value.takeIf { it.contains('|') }
            ?: workspace?.selectedModel?.takeIf { it.contains('|') }
            ?: securePreferences.getSelectedModels().firstOrNull()
            ?: throw IllegalStateException("La tarea no tiene un modelo configurado")
        val provider = runCatching {
            ProviderType.valueOf(selectedModelKey.substringBefore('|'))
        }.getOrElse {
            throw IllegalStateException("Proveedor inválido en la tarea programada")
        }
        val model = selectedModelKey.substringAfter('|').trim()
            .takeIf(String::isNotEmpty)
            ?: throw IllegalStateException("Modelo inválido en la tarea programada")

        repeat(MAX_TOOL_ITERATIONS) { iteration ->
            val response = repository.chatWithTools(
                agent = agent,
                messages = messages,
                overrideModel = model,
                overrideProvider = provider,
                enableTerminal = agent.enableTerminal,
                workspaceFolderPath = fileRepository.getWorkspaceFolderPath(workspaceId),
                allowedToolNames = ToolExecutionProfiles.BACKGROUND
            ).getOrThrow()

            finalContent = response.content ?: ""

            val toolCalls = response.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                return AgentExecutionResult(finalContent, agent.id)
            }

            // Add assistant message with tool calls
            messages.add(Message(role = MessageRole.ASSISTANT, content = finalContent, toolCalls = toolCalls))

            // Execute each tool call
            for (tc in toolCalls) {
                val result = executeToolInBackground(agent, tc, workspaceId)
                messages.add(Message(
                    role = MessageRole.TOOL,
                    content = result.content,
                    toolResults = listOf(result)
                ))
            }
        }

        return AgentExecutionResult(finalContent, agent.id)
    }

    private suspend fun ensureConversation(task: ScheduledTaskEntity): Long {
        val existing = task.conversationId
            ?.let { repository.getConversationById(it) }
            ?.takeIf { it.workspaceId == task.workspaceId }
        if (existing != null) return existing.id

        val title = "Cron: ${task.label.ifBlank { "Tarea programada" }}".take(80)
        val conversationId = repository.createConversation(
            Conversation(workspaceId = task.workspaceId, title = title)
        )
        scheduledTaskDao.setConversationId(task.id, conversationId)
        return conversationId
    }

    private suspend fun persistExecution(
        task: ScheduledTaskEntity,
        conversationId: Long,
        execution: AgentExecutionResult
    ) {
        val runMessage = buildString {
            append("⏰ Ejecución programada")
            task.label.takeIf(String::isNotBlank)?.let { append(": $it") }
            append("\n\n")
            append(task.prompt)
        }
        repository.addMessage(
            task.workspaceId,
            conversationId,
            Message(role = MessageRole.USER, content = runMessage),
            execution.agentId
        )
        repository.addMessage(
            task.workspaceId,
            conversationId,
            Message(role = MessageRole.ASSISTANT, content = execution.content),
            execution.agentId
        )
        repository.touchConversation(conversationId)
    }

    /**
     * Execute a tool call in background context. Only supports non-interactive tools.
     */
    private suspend fun executeToolInBackground(
        agent: Agent,
        toolCall: ToolCall,
        workspaceId: Long
    ): ToolResult {
        val name = toolCall.function.name
        val args = toolCall.function.arguments
        val id = toolCall.id
        val wsPath = fileRepository.getWorkspaceFolderPath(workspaceId)

        return try {
            val content = when (name) {
                // File operations
                "read_text_file", "read_image_file", "read_pdf_file", "write_file", "list_files" -> {
                    repository.getFileToolHandler().executeTool(id, name, args, workspaceId).content
                }
                // Web search
                "duckduckgo_search" -> {
                    repository.getDuckDuckGoSearchToolHandler().executeTool(id, args).content
                }
                "brave_web_search" -> {
                    repository.getBraveSearchToolHandler().executeTool(id, args, repository.getBraveApiKey()).content
                }
                "serpapi_search" -> {
                    repository.getSerpAPIToolHandler().executeTool(id, args, repository.getSerpApiKey()).content
                }
                in com.aiagents.app.data.terminal.UnifiedWebToolHandler.ALL_TOOL_NAMES -> {
                    repository.getUnifiedWebToolHandler().executeTool(id, name, args).content
                }
                in com.aiagents.app.data.terminal.SkillToolHandler.ALL_TOOL_NAMES -> {
                    repository.getSkillToolHandler().executeTool(id, name, args).content
                }
                // Memory
                in com.aiagents.app.data.terminal.MemoryToolHandler.ALL_TOOL_NAMES -> {
                    if (name !in com.aiagents.app.data.terminal.MemoryToolHandler.READ_TOOL_NAMES) {
                        "Error: semantic-memory writes are disabled in background tasks."
                    } else {
                        repository.getMemoryToolHandler().executeTool(id, name, args).content
                    }
                }
                // Terminal
                "execute_command" -> {
                    val handler = repository.getToolHandler()
                    val request = handler.parseToolCall(toolCall)
                    if (request != null) {
                        val result = handler.executeWithPermission(request, wsPath)
                        handler.formatResultForLLM(result)
                    } else "Error parsing command"
                }
                // GitHub
                in com.aiagents.app.data.terminal.GitHubToolHandler.ALL_TOOL_NAMES -> {
                    repository.getGitHubToolHandler().executeTool(id, name, args).content
                }
                // Notion
                in com.aiagents.app.data.terminal.NotionToolHandler.ALL_TOOL_NAMES -> {
                    repository.getNotionToolHandler().executeTool(id, name, args).content
                }
                // Slack
                in com.aiagents.app.data.terminal.SlackToolHandler.ALL_TOOL_NAMES -> {
                    repository.getSlackToolHandler().executeTool(id, name, args).content
                }
                // App control
                "app_control" -> {
                    repository.getAppControlToolHandler().executeTool(id, args, workspaceId).content
                }
                else -> "Tool '$name' is not available in background execution."
            }
            ToolResult(id, name, content)
        } catch (e: Exception) {
            Log.e(TAG, "Background tool error: $name", e)
            ToolResult(id, name, "Error: ${e.message}")
        }
    }

    private fun sendNotification(task: ScheduledTaskEntity, conversationId: Long, body: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure channel exists
        val channel = NotificationChannel(
            CHANNEL_ID, "Scheduled Tasks",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Results from scheduled agent tasks" }
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            applicationContext,
            task.id.toInt(),
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
                putExtra(EXTRA_WORKSPACE_ID, task.workspaceId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(task.label.ifBlank { "Scheduled Task" })
            .setContentText(body.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(500)))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}
