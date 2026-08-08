package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.model.PermissionLevel
import com.aiagents.app.domain.model.Message
import com.aiagents.app.domain.model.MessageRole
import com.aiagents.app.domain.model.ToolCall
import com.aiagents.app.domain.model.ToolResult
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import javax.inject.Inject

data class TerminalToolDefinition(
    val name: String = "execute_command",
    val description: String = "Execute a shell command in the workspace directory. Use for: file operations, running scripts, searching text, etc.",
    val parameters: ToolParameters = ToolParameters()
)

data class ToolParameters(
    val type: String = "object",
    val properties: ToolProperties = ToolProperties(),
    val required: List<String> = listOf("command")
)

data class ToolProperties(
    @SerializedName("command")
    val command: ToolProperty = ToolProperty(
        type = "string",
        description = "Shell command to execute"
    ),
    @SerializedName("working_directory")
    val workingDirectory: ToolProperty = ToolProperty(
        type = "string",
        description = "Optional working directory"
    ),
    @SerializedName("timeout")
    val timeout: ToolProperty = ToolProperty(
        type = "integer",
        description = "Max execution time in seconds (default 30)"
    )
)

data class ToolProperty(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

data class ExecuteCommandArgs(
    val command: String,
    val workingDirectory: String? = null,
    val timeout: Int? = null
)

class ToolHandler @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val permissionManager: CommandPermissionManager
) {
    companion object {
        private const val TAG = "ToolHandler"
        val TERMINAL_TOOL = TerminalToolDefinition()
        
        fun getAllToolDefinitionsJson(
            workspacePath: String? = null,
            includeTerminal: Boolean = true
        ): List<Map<String, Any>> {
            val tools = mutableListOf<Map<String, Any>>()
            if (includeTerminal) {
                tools.addAll(getToolDefinitionsJson(workspacePath))
            }
            tools.addAll(FileToolHandler.getToolDefinitionsJson(workspacePath))
            tools.addAll(AgentSelectionToolHandler.getToolDefinitionsJson())
            tools.addAll(CalendarToolHandler.getToolDefinitionsJson())
            tools.addAll(SystemAppToolHandler.getToolDefinitionsJson())
            tools.addAll(AcademicSearchToolHandler.getToolDefinitionsJson())
            tools.addAll(WeatherToolHandler.getToolDefinitionsJson())
            tools.addAll(ImageGenerationToolHandler.getToolDefinitionsJson())
            return tools
        }

        fun getToolDefinitionsJson(defaultWorkingDir: String? = null): List<Map<String, Any>> {
            val workingDirDescription = if (defaultWorkingDir != null) {
                "Directorio de trabajo donde se ejecuta el comando. Por defecto: $defaultWorkingDir"
            } else {
                TERMINAL_TOOL.parameters.properties.workingDirectory.description
            }
            
            return listOf(
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to TERMINAL_TOOL.name,
                        "description" to TERMINAL_TOOL.description,
                        "parameters" to mapOf(
                            "type" to TERMINAL_TOOL.parameters.type,
                            "properties" to mapOf(
                                "command" to mapOf(
                                    "type" to TERMINAL_TOOL.parameters.properties.command.type,
                                    "description" to TERMINAL_TOOL.parameters.properties.command.description
                                ),
                                "working_directory" to mapOf(
                                    "type" to TERMINAL_TOOL.parameters.properties.workingDirectory.type,
                                    "description" to workingDirDescription
                                ),
                                "timeout" to mapOf(
                                    "type" to TERMINAL_TOOL.parameters.properties.timeout.type,
                                    "description" to TERMINAL_TOOL.parameters.properties.timeout.description
                                )
                            ),
                            "required" to TERMINAL_TOOL.parameters.required
                        )
                    )
                )
            )
        }
    }
    
    private val gson = Gson()
    
    fun parseToolCall(toolCall: ToolCall): ToolExecutionRequest? {
        return try {
            if (toolCall.function.name != TERMINAL_TOOL.name) {
                Log.w(TAG, "Unknown tool: ${toolCall.function.name}")
                return null
            }
            
            Log.d(TAG, "Parsing tool call: id=${toolCall.id}, args=${toolCall.function.arguments}")
            
            val args = gson.fromJson(toolCall.function.arguments, ExecuteCommandArgs::class.java)
            
            Log.d(TAG, "Parsed args: command='${args.command}', workingDir=${args.workingDirectory}, timeout=${args.timeout}")
            
            ToolExecutionRequest(
                toolCallId = toolCall.id,
                command = args.command,
                workingDirectory = args.workingDirectory,
                timeout = args.timeout?.toLong()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tool call: ${toolCall.function.arguments}", e)
            null
        }
    }
    
    suspend fun checkPermission(request: ToolExecutionRequest): PermissionCheckResult {
        return permissionManager.checkPermission(request.command)
    }

    /** Bloquea de forma permanente (glob sobre el comando base) un comando denegado. */
    suspend fun blockCommand(command: String) {
        permissionManager.grantPermission(command, PermissionLevel.BLOCKED)
    }
    
    suspend fun executeWithPermission(
        request: ToolExecutionRequest,
        defaultWorkingDirectory: String? = null
    ): ToolExecutionResult {
        val permissionResult = checkPermission(request)
        
        // Usar el directorio por defecto si no se especificó uno en el request
        val workingDir = request.workingDirectory ?: defaultWorkingDirectory
        val requestWithWorkingDir = request.copy(workingDirectory = workingDir)
        
        return when (permissionResult) {
            is PermissionCheckResult.Blocked -> ToolExecutionResult(
                toolCallId = request.toolCallId,
                command = request.command,
                success = false,
                output = permissionResult.reason,
                permissionRequired = false
            )
            is PermissionCheckResult.Allowed -> executeCommand(requestWithWorkingDir)
            is PermissionCheckResult.NeedsConfirmation -> ToolExecutionResult(
                toolCallId = request.toolCallId,
                command = request.command,
                success = false,
                output = permissionResult.message ?: "Se requiere permiso para ejecutar este comando",
                permissionRequired = true,
                permissionRequest = permissionResult.request
            )
        }
    }
    
    suspend fun executeAfterPermissionGranted(
        request: ToolExecutionRequest,
        permissionLevel: PermissionLevel,
        defaultWorkingDirectory: String? = null
    ): ToolExecutionResult {
        permissionManager.grantPermission(request.command, permissionLevel)
        // Usar el directorio por defecto si no se especificó uno en el request
        val workingDir = request.workingDirectory ?: defaultWorkingDirectory
        val requestWithWorkingDir = request.copy(workingDirectory = workingDir)
        return executeCommand(requestWithWorkingDir)
    }
    
    private fun executeCommand(request: ToolExecutionRequest): ToolExecutionResult {
        val result = shellExecutor.execute(
            command = request.command,
            workingDirectory = request.workingDirectory,
            timeoutSeconds = request.timeout ?: 30L
        )
        
        return ToolExecutionResult(
            toolCallId = request.toolCallId,
            command = request.command,
            success = result.isSuccess,
            exitCode = result.exitCode,
            output = result.combinedOutput,
            stdout = result.stdout,
            stderr = result.stderr,
            executionTimeMs = result.executionTimeMs,
            timedOut = result.timedOut,
            permissionRequired = false
        )
    }
    
    fun formatResultForLLM(result: ToolExecutionResult): String {
        return buildString {
            append("Comando ejecutado: ${result.command}\n")
            append("Estado: ${if (result.success) "ÉXITO" else "FALLO"}\n")
            if (result.exitCode != null && result.exitCode != 0) {
                append("Código de salida: ${result.exitCode}\n")
            }
            if (result.timedOut) {
                append("Tiempo agotado\n")
            }
            append("Tiempo de ejecución: ${result.executionTimeMs}ms\n")
            append("--- Salida ---\n")
            append(result.output)
            append("\n--- Fin de salida ---")
        }
    }
}

data class ToolExecutionRequest(
    val toolCallId: String,
    val command: String,
    val workingDirectory: String? = null,
    val timeout: Long? = null
)

data class ToolExecutionResult(
    val toolCallId: String,
    val command: String,
    val success: Boolean,
    val output: String,
    val exitCode: Int? = null,
    val stdout: String? = null,
    val stderr: String? = null,
    val executionTimeMs: Long = 0,
    val timedOut: Boolean = false,
    val permissionRequired: Boolean = false,
    val permissionRequest: PermissionRequest? = null
)
