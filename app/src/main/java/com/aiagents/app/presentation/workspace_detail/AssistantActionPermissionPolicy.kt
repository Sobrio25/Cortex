package com.aiagents.app.presentation.workspace_detail

import android.Manifest
import com.aiagents.app.data.terminal.SystemAppToolHandler
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Determines runtime permissions without trusting model-generated tool arguments.
 * Invalid arguments are left to [SystemAppToolHandler] so one bad tool call cannot
 * abort the rest of a tool-call batch.
 */
internal object AssistantActionPermissionPolicy {
    private val gson = Gson()

    fun requiredPermissions(toolName: String, arguments: String): List<String> {
        if (toolName != SystemAppToolHandler.TOOL_NAME) return emptyList()

        val args = runCatching {
            gson.fromJson(arguments, JsonObject::class.java)
        }.getOrNull() ?: return emptyList()
        val action = args.get("action")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?: return emptyList()

        if (action != "call_phone" && action != "prepare_whatsapp_message") {
            return emptyList()
        }

        val paramsElement = args.get("params")
        val params = paramsElement
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return emptyList()

        return when (action) {
            "call_phone" -> {
                val phoneNumber = params.stringArgument("phone_number")
                val contact = params.stringArgument("contact")
                when {
                    !phoneNumber.isNullOrBlank() -> listOf(Manifest.permission.CALL_PHONE)
                    !contact.isNullOrBlank() -> listOf(
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.READ_CONTACTS
                    )
                    else -> emptyList()
                }
            }
            "prepare_whatsapp_message" -> {
                val contact = params.stringArgument("contact")
                val message = params.stringArgument("message")
                if (!contact.isNullOrBlank() && !message.isNullOrBlank()) {
                    listOf(Manifest.permission.READ_CONTACTS)
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun JsonObject.stringArgument(name: String): String? = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
}
