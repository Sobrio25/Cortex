package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.local.ConversationDao
import com.aiagents.app.data.local.ScheduledTaskDao
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.model.ConversationEntity
import com.aiagents.app.data.model.ScheduledTaskEntity
import com.aiagents.app.data.scheduling.TaskSchedulerManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class ScheduledTaskToolResult(
    val toolCallId: String,
    val toolName: String = TOOL_NAME,
    val success: Boolean,
    val content: String
) {
    companion object {
        const val TOOL_NAME = "schedule_task"
    }
}

@Singleton
class ScheduledTaskToolHandler @Inject constructor(
    private val scheduledTaskDao: ScheduledTaskDao,
    private val conversationDao: ConversationDao,
    private val schedulerManager: TaskSchedulerManager,
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "ScheduledTaskTool"
        const val TOOL_NAME = "schedule_task"
        val ALL_TOOL_NAMES = setOf(TOOL_NAME)

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to TOOL_NAME,
                    "description" to """Manage scheduled agent tasks (cron jobs). Agents execute prompts automatically at specified times. Each full result is saved in the linked chat so the user can continue the conversation.

Actions:
- "create": Create a scheduled task. Params:
    prompt (required): What the agent should do
    schedule_type: "once"|"daily"|"weekly"|"interval"
    schedule_value: Depends on type:
      once: ISO datetime "2026-03-25T09:00"
      daily: "HH:mm" (e.g. "09:00")
      weekly: "MON,WED,FRI HH:mm" (e.g. "MON,FRI 08:30")
      interval: "30m", "2h", "1d"
    label (optional): Human-readable name
    agent_name (optional): Agent to run (defaults to the configured main assistant)
- "list": List all scheduled tasks
- "delete": Delete a task. Params: id
- "toggle": Enable/disable a task. Params: id, enabled (true/false)""",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "action" to mapOf("type" to "string"),
                            "params" to mapOf("type" to "object")
                        ),
                        "required" to listOf("action")
                    )
                )
            )
        )
    }

    private val gson = Gson()

    suspend fun executeTool(
        toolCallId: String,
        arguments: String,
        workspaceId: Long,
        conversationId: Long? = null
    ): ScheduledTaskToolResult {
        return try {
            val args = gson.fromJson(arguments, JsonObject::class.java) ?: JsonObject()
            val action = args.get("action")?.asString
                ?: return ScheduledTaskToolResult(toolCallId, success = false, content = "Missing 'action'.")
            val params = args.getAsJsonObject("params") ?: JsonObject()

            when (action) {
                "create" -> createTask(toolCallId, params, workspaceId, conversationId)
                "list" -> listTasks(toolCallId)
                "delete" -> deleteTask(toolCallId, params)
                "toggle" -> toggleTask(toolCallId, params)
                else -> ScheduledTaskToolResult(toolCallId, success = false, content = "Unknown action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in schedule_task", e)
            ScheduledTaskToolResult(toolCallId, success = false, content = "Error: ${e.message}")
        }
    }

    private suspend fun createTask(
        toolCallId: String,
        params: JsonObject,
        workspaceId: Long,
        conversationId: Long?
    ): ScheduledTaskToolResult {
        val prompt = params.get("prompt")?.asString
            ?: return ScheduledTaskToolResult(toolCallId, success = false, content = "Missing 'prompt'.")
        val scheduleType = params.get("schedule_type")?.asString ?: "once"
        val scheduleValue = params.get("schedule_value")?.asString
            ?: return ScheduledTaskToolResult(toolCallId, success = false, content = "Missing 'schedule_value'.")
        val label = params.get("label")?.asString ?: prompt.take(60)
        val agentName = params.get("agent_name")?.asString

        // Validate and compute first run time
        val nextRun = schedulerManager.computeNextRun(scheduleType, scheduleValue)
            ?: return ScheduledTaskToolResult(toolCallId, success = false,
                content = "Invalid schedule. Type: $scheduleType, Value: $scheduleValue. " +
                    "Formats: once='2026-03-25T09:00', daily='09:00', weekly='MON,FRI 09:00', interval='30m'")
        val linkedConversationId = resolveConversationId(
            requestedConversationId = conversationId,
            workspaceId = workspaceId,
            label = label
        )

        val entity = ScheduledTaskEntity(
            workspaceId = workspaceId,
            conversationId = linkedConversationId,
            agentName = agentName,
            prompt = prompt,
            scheduleType = scheduleType,
            scheduleValue = scheduleValue,
            label = label,
            nextRunAt = nextRun
        )

        val id = scheduledTaskDao.insert(entity)
        val saved = entity.copy(id = id)
        schedulerManager.scheduleAlarm(saved)

        val nextRunStr = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(nextRun), ZoneId.systemDefault()
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        Log.i(TAG, "Created scheduled task $id: '$label' ($scheduleType: $scheduleValue)")

        return ScheduledTaskToolResult(toolCallId, success = true,
            content = "Scheduled task created (id: $id).\nLabel: $label\nSchedule: $scheduleType $scheduleValue\nNext run: $nextRunStr\nAgent: ${agentName ?: securePreferences.getAssistantName() ?: "Assistant"}\nChat: $linkedConversationId")
    }

    private suspend fun resolveConversationId(
        requestedConversationId: Long?,
        workspaceId: Long,
        label: String
    ): Long {
        val existing = requestedConversationId
            ?.takeIf { it > 0 }
            ?.let { conversationDao.getConversationById(it) }
            ?.takeIf { it.workspaceId == workspaceId }
        if (existing != null) return existing.id

        val title = "Cron: ${label.ifBlank { "Tarea programada" }}".take(80)
        return conversationDao.insertConversation(
            ConversationEntity(workspaceId = workspaceId, title = title)
        )
    }

    private suspend fun listTasks(toolCallId: String): ScheduledTaskToolResult {
        val tasks = scheduledTaskDao.getAll()
        if (tasks.isEmpty()) {
            return ScheduledTaskToolResult(toolCallId, success = true, content = "No scheduled tasks.")
        }

        val info = buildString {
            appendLine("Scheduled tasks (${tasks.size}):")
            tasks.forEach { task ->
                val status = if (task.enabled) "ON" else "OFF"
                val nextStr = if (task.nextRunAt > 0) {
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(task.nextRunAt), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                } else "—"
                val lastStr = task.lastRunAt?.let {
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                } ?: "never"
                appendLine("  [${task.id}] $status | ${task.label} | ${task.scheduleType} ${task.scheduleValue} | chat: ${task.conversationId ?: "pending"} | next: $nextStr | last: $lastStr | runs: ${task.runCount}")
            }
        }
        return ScheduledTaskToolResult(toolCallId, success = true, content = info.trim())
    }

    private suspend fun deleteTask(toolCallId: String, params: JsonObject): ScheduledTaskToolResult {
        val id = params.get("id")?.asLong
            ?: return ScheduledTaskToolResult(toolCallId, success = false, content = "Missing 'id'.")
        val task = scheduledTaskDao.getById(id)
            ?: return ScheduledTaskToolResult(toolCallId, success = false, content = "Task $id not found.")

        schedulerManager.cancelAlarm(id)
        scheduledTaskDao.delete(id)
        return ScheduledTaskToolResult(toolCallId, success = true, content = "Deleted scheduled task $id: '${task.label}'")
    }

    private suspend fun toggleTask(toolCallId: String, params: JsonObject): ScheduledTaskToolResult {
        val id = params.get("id")?.asLong
            ?: return ScheduledTaskToolResult(toolCallId, success = false, content = "Missing 'id'.")
        val enabled = params.get("enabled")?.asBoolean
            ?: return ScheduledTaskToolResult(toolCallId, success = false, content = "Missing 'enabled'.")

        val task = scheduledTaskDao.getById(id)
            ?: return ScheduledTaskToolResult(toolCallId, success = false, content = "Task $id not found.")

        scheduledTaskDao.setEnabled(id, enabled)

        if (enabled) {
            // Recompute next run if enabling
            val nextRun = schedulerManager.computeNextRun(task.scheduleType, task.scheduleValue)
            if (nextRun != null) {
                scheduledTaskDao.markExecuted(id, task.lastRunAt ?: 0L, task.lastResult, nextRun)
                schedulerManager.scheduleAlarm(task.copy(enabled = true, nextRunAt = nextRun))
            }
        } else {
            schedulerManager.cancelAlarm(id)
        }

        return ScheduledTaskToolResult(toolCallId, success = true,
            content = "Task $id '${task.label}' ${if (enabled) "enabled" else "disabled"}.")
    }
}
