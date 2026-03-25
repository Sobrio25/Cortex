package com.aiagents.app.data.terminal

import android.util.Log
import com.aiagents.app.data.local.SecurePreferences
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class SlackToolResult(
    val toolCallId: String,
    val success: Boolean,
    val content: String
)

@Singleton
class SlackToolHandler @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securePreferences: SecurePreferences
) {
    companion object {
        private const val TAG = "SlackToolHandler"
        private const val API_URL = "https://slack.com/api"

        const val TOOL_LIST_CHANNELS = "slack_list_channels"
        const val TOOL_GET_CHANNEL_INFO = "slack_get_channel_info"
        const val TOOL_READ_CHANNEL = "slack_read_channel"
        const val TOOL_SEND_MESSAGE = "slack_send_message"
        const val TOOL_REPLY_THREAD = "slack_reply_thread"
        const val TOOL_LIST_USERS = "slack_list_users"
        const val TOOL_GET_USER = "slack_get_user"
        const val TOOL_SEARCH_MESSAGES = "slack_search_messages"
        const val TOOL_ADD_REACTION = "slack_add_reaction"
        const val TOOL_SET_TOPIC = "slack_set_channel_topic"
        const val TOOL_LIST_FILES = "slack_list_files"
        const val TOOL_DELETE_MESSAGE = "slack_delete_message"

        val ALL_TOOL_NAMES = setOf(
            TOOL_LIST_CHANNELS, TOOL_GET_CHANNEL_INFO, TOOL_READ_CHANNEL,
            TOOL_SEND_MESSAGE, TOOL_REPLY_THREAD, TOOL_LIST_USERS,
            TOOL_GET_USER, TOOL_SEARCH_MESSAGES, TOOL_ADD_REACTION,
            TOOL_SET_TOPIC, TOOL_LIST_FILES, TOOL_DELETE_MESSAGE
        )

        fun getToolDefinitionsJson(): List<Map<String, Any>> = listOf(
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_LIST_CHANNELS,
                "description" to "List Slack channels the bot has access to.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "types" to mapOf("type" to "string", "description" to "Comma-separated channel types: public_channel, private_channel, mpim, im (default: public_channel,private_channel)"),
                        "limit" to mapOf("type" to "integer", "description" to "Max channels to return (default 100, max 1000)")
                    ), "required" to emptyList<String>())
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_GET_CHANNEL_INFO,
                "description" to "Get detailed info about a Slack channel (name, topic, purpose, member count).",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "channel" to mapOf("type" to "string", "description" to "Channel ID (e.g. C01234567)")
                    ), "required" to listOf("channel"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_READ_CHANNEL,
                "description" to "Read recent messages from a Slack channel or conversation.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "channel" to mapOf("type" to "string", "description" to "Channel ID"),
                        "limit" to mapOf("type" to "integer", "description" to "Number of messages to fetch (default 20, max 100)")
                    ), "required" to listOf("channel"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_SEND_MESSAGE,
                "description" to "Send a message to a Slack channel.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "channel" to mapOf("type" to "string", "description" to "Channel ID to send the message to"),
                        "text" to mapOf("type" to "string", "description" to "Message text (supports Slack markdown: *bold*, _italic_, `code`, ```code block```, <url|text>)")
                    ), "required" to listOf("channel", "text"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_REPLY_THREAD,
                "description" to "Reply to a specific message thread in Slack.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "channel" to mapOf("type" to "string", "description" to "Channel ID"),
                        "thread_ts" to mapOf("type" to "string", "description" to "Timestamp of the parent message to reply to"),
                        "text" to mapOf("type" to "string", "description" to "Reply text")
                    ), "required" to listOf("channel", "thread_ts", "text"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_LIST_USERS,
                "description" to "List members of the Slack workspace.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "limit" to mapOf("type" to "integer", "description" to "Max users to return (default 100)")
                    ), "required" to emptyList<String>())
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_GET_USER,
                "description" to "Get detailed profile info for a Slack user.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "user" to mapOf("type" to "string", "description" to "User ID (e.g. U01234567)")
                    ), "required" to listOf("user"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_SEARCH_MESSAGES,
                "description" to "Search messages across Slack channels.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "Search query text"),
                        "count" to mapOf("type" to "integer", "description" to "Number of results (default 20, max 100)")
                    ), "required" to listOf("query"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_ADD_REACTION,
                "description" to "Add an emoji reaction to a message.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "channel" to mapOf("type" to "string", "description" to "Channel ID where the message is"),
                        "timestamp" to mapOf("type" to "string", "description" to "Timestamp of the message to react to"),
                        "name" to mapOf("type" to "string", "description" to "Emoji name without colons (e.g. 'thumbsup', 'heart', 'eyes')")
                    ), "required" to listOf("channel", "timestamp", "name"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_SET_TOPIC,
                "description" to "Set the topic of a Slack channel.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "channel" to mapOf("type" to "string", "description" to "Channel ID"),
                        "topic" to mapOf("type" to "string", "description" to "New channel topic text")
                    ), "required" to listOf("channel", "topic"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_LIST_FILES,
                "description" to "List files shared in the Slack workspace or a specific channel.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "channel" to mapOf("type" to "string", "description" to "Channel ID to filter files (optional)"),
                        "count" to mapOf("type" to "integer", "description" to "Number of files to return (default 20, max 100)")
                    ), "required" to emptyList<String>())
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to TOOL_DELETE_MESSAGE,
                "description" to "Delete a message sent by the bot.",
                "parameters" to mapOf("type" to "object",
                    "properties" to mapOf(
                        "channel" to mapOf("type" to "string", "description" to "Channel ID"),
                        "ts" to mapOf("type" to "string", "description" to "Timestamp of the message to delete")
                    ), "required" to listOf("channel", "ts"))
            ))
        )
    }

    suspend fun executeTool(toolCallId: String, toolName: String, arguments: String): SlackToolResult {
        val token = securePreferences.getSlackToken()
        if (token.isNullOrBlank()) {
            return SlackToolResult(toolCallId, false,
                "Error: Slack is not configured. Go to MCP settings to add your Bot Token.")
        }
        return try {
            val args = JsonParser.parseString(arguments).asJsonObject
            when (toolName) {
                TOOL_LIST_CHANNELS -> listChannels(toolCallId, args, token)
                TOOL_GET_CHANNEL_INFO -> getChannelInfo(toolCallId, args, token)
                TOOL_READ_CHANNEL -> readChannel(toolCallId, args, token)
                TOOL_SEND_MESSAGE -> sendMessage(toolCallId, args, token)
                TOOL_REPLY_THREAD -> replyThread(toolCallId, args, token)
                TOOL_LIST_USERS -> listUsers(toolCallId, args, token)
                TOOL_GET_USER -> getUser(toolCallId, args, token)
                TOOL_SEARCH_MESSAGES -> searchMessages(toolCallId, args, token)
                TOOL_ADD_REACTION -> addReaction(toolCallId, args, token)
                TOOL_SET_TOPIC -> setTopic(toolCallId, args, token)
                TOOL_LIST_FILES -> listFiles(toolCallId, args, token)
                TOOL_DELETE_MESSAGE -> deleteMessage(toolCallId, args, token)
                else -> SlackToolResult(toolCallId, false, "Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            SlackToolResult(toolCallId, false, "Error: ${e.message}")
        }
    }

    // --- Tool implementations ---

    private suspend fun listChannels(id: String, args: com.google.gson.JsonObject, token: String): SlackToolResult {
        val types = args.get("types")?.asString ?: "public_channel,private_channel"
        val limit = args.get("limit")?.asInt ?: 100

        val json = get("$API_URL/conversations.list?types=$types&limit=$limit&exclude_archived=true", token)
            ?: return SlackToolResult(id, false, "Error calling Slack API")
        if (!json.get("ok")?.asBoolean.let { it == true }) {
            return SlackToolResult(id, false, "Slack error: ${json.get("error")?.asString}")
        }

        val channels = json.getAsJsonArray("channels")
        if (channels == null || channels.size() == 0) return SlackToolResult(id, true, "No channels found.")

        val formatted = buildString {
            appendLine("Slack Channels (${channels.size()}):")
            appendLine()
            channels.forEach { ch ->
                val c = ch.asJsonObject
                val cId = c.get("id")?.asString ?: ""
                val name = c.get("name")?.asString ?: ""
                val isPrivate = c.get("is_private")?.asBoolean ?: false
                val memberCount = c.get("num_members")?.asInt ?: 0
                val topic = c.getAsJsonObject("topic")?.get("value")?.asString ?: ""
                val prefix = if (isPrivate) "🔒" else "#"
                appendLine("$prefix **$name** ($cId)")
                if (topic.isNotBlank()) appendLine("  Topic: $topic")
                appendLine("  Members: $memberCount")
                appendLine()
            }
        }
        return SlackToolResult(id, true, formatted.trim())
    }

    private suspend fun getChannelInfo(id: String, args: com.google.gson.JsonObject, token: String): SlackToolResult {
        val channel = args.get("channel")?.asString ?: return SlackToolResult(id, false, "Parameter 'channel' required")

        val json = get("$API_URL/conversations.info?channel=$channel", token)
            ?: return SlackToolResult(id, false, "Error calling Slack API")
        if (!json.get("ok")?.asBoolean.let { it == true }) {
            return SlackToolResult(id, false, "Slack error: ${json.get("error")?.asString}")
        }

        val c = json.getAsJsonObject("channel")
        val formatted = buildString {
            appendLine("Channel: #${c.get("name")?.asString}")
            appendLine("ID: ${c.get("id")?.asString}")
            appendLine("Private: ${c.get("is_private")?.asBoolean ?: false}")
            appendLine("Members: ${c.get("num_members")?.asInt ?: 0}")
            appendLine("Topic: ${c.getAsJsonObject("topic")?.get("value")?.asString ?: "(none)"}")
            appendLine("Purpose: ${c.getAsJsonObject("purpose")?.get("value")?.asString ?: "(none)"}")
            appendLine("Created: ${c.get("created")?.asLong?.let { java.time.Instant.ofEpochSecond(it).toString() } ?: "unknown"}")
        }
        return SlackToolResult(id, true, formatted.trim())
    }

    private suspend fun readChannel(id: String, args: com.google.gson.JsonObject, token: String): SlackToolResult {
        val channel = args.get("channel")?.asString ?: return SlackToolResult(id, false, "Parameter 'channel' required")
        val limit = (args.get("limit")?.asInt ?: 20).coerceIn(1, 100)

        val json = get("$API_URL/conversations.history?channel=$channel&limit=$limit", token)
            ?: return SlackToolResult(id, false, "Error calling Slack API")
        if (!json.get("ok")?.asBoolean.let { it == true }) {
            return SlackToolResult(id, false, "Slack error: ${json.get("error")?.asString}")
        }

        val messages = json.getAsJsonArray("messages")
        if (messages == null || messages.size() == 0) return SlackToolResult(id, true, "No messages in this channel.")

        // Build user cache for display names
        val userIds = messages.mapNotNull { it.asJsonObject.get("user")?.asString }.distinct()
        val userNames = mutableMapOf<String, String>()
        userIds.take(20).forEach { uid ->
            try {
                val userJson = get("$API_URL/users.info?user=$uid", token)
                if (userJson?.get("ok")?.asBoolean == true) {
                    val u = userJson.getAsJsonObject("user")?.getAsJsonObject("profile")
                    userNames[uid] = u?.get("display_name")?.asString?.takeIf { it.isNotBlank() }
                        ?: u?.get("real_name")?.asString ?: uid
                }
            } catch (_: Exception) {}
        }

        val formatted = buildString {
            appendLine("Messages in channel (latest $limit):")
            appendLine()
            // Messages come newest-first, reverse for chronological
            messages.reversed().forEach { msg ->
                val m = msg.asJsonObject
                val user = m.get("user")?.asString ?: "bot"
                val displayName = userNames[user] ?: user
                val text = m.get("text")?.asString ?: ""
                val ts = m.get("ts")?.asString ?: ""
                val threadTs = m.get("thread_ts")?.asString
                val replyCount = m.get("reply_count")?.asInt
                val time = ts.substringBefore(".").toLongOrNull()?.let {
                    java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                } ?: ts

                append("[$time] **$displayName**: $text")
                if (replyCount != null && replyCount > 0) append(" ($replyCount replies)")
                appendLine()
                appendLine("  ts: $ts")
                if (threadTs != null && threadTs != ts) appendLine("  thread: $threadTs")
                appendLine()
            }
        }
        return SlackToolResult(id, true, formatted.trim())
    }

    private suspend fun sendMessage(id: String, args: com.google.gson.JsonObject, token: String): SlackToolResult {
        val channel = args.get("channel")?.asString ?: return SlackToolResult(id, false, "Parameter 'channel' required")
        val text = args.get("text")?.asString ?: return SlackToolResult(id, false, "Parameter 'text' required")

        val body = com.google.gson.JsonObject().apply {
            addProperty("channel", channel)
            addProperty("text", text)
        }.toString()

        val json = post("$API_URL/chat.postMessage", body, token)
            ?: return SlackToolResult(id, false, "Error calling Slack API")
        if (!json.get("ok")?.asBoolean.let { it == true }) {
            return SlackToolResult(id, false, "Slack error: ${json.get("error")?.asString}")
        }

        val ts = json.get("ts")?.asString
        return SlackToolResult(id, true, "Message sent successfully. ts: $ts")
    }

    private suspend fun replyThread(id: String, args: com.google.gson.JsonObject, token: String): SlackToolResult {
        val channel = args.get("channel")?.asString ?: return SlackToolResult(id, false, "Parameter 'channel' required")
        val threadTs = args.get("thread_ts")?.asString ?: return SlackToolResult(id, false, "Parameter 'thread_ts' required")
        val text = args.get("text")?.asString ?: return SlackToolResult(id, false, "Parameter 'text' required")

        val body = com.google.gson.JsonObject().apply {
            addProperty("channel", channel)
            addProperty("thread_ts", threadTs)
            addProperty("text", text)
        }.toString()

        val json = post("$API_URL/chat.postMessage", body, token)
            ?: return SlackToolResult(id, false, "Error calling Slack API")
        if (!json.get("ok")?.asBoolean.let { it == true }) {
            return SlackToolResult(id, false, "Slack error: ${json.get("error")?.asString}")
        }

        val ts = json.get("ts")?.asString
        return SlackToolResult(id, true, "Reply sent to thread $threadTs. ts: $ts")
    }

    private suspend fun listUsers(id: String, args: com.google.gson.JsonObject, token: String): SlackToolResult {
        val limit = args.get("limit")?.asInt ?: 100

        val json = get("$API_URL/users.list?limit=$limit", token)
            ?: return SlackToolResult(id, false, "Error calling Slack API")
        if (!json.get("ok")?.asBoolean.let { it == true }) {
            return SlackToolResult(id, false, "Slack error: ${json.get("error")?.asString}")
        }

        val members = json.getAsJsonArray("members")
        if (members == null || members.size() == 0) return SlackToolResult(id, true, "No users found.")

        val formatted = buildString {
            appendLine("Workspace Members:")
            appendLine()
            members.forEach { member ->
                val u = member.asJsonObject
                if (u.get("deleted")?.asBoolean == true) return@forEach
                if (u.get("is_bot")?.asBoolean == true) return@forEach
                val userId = u.get("id")?.asString ?: ""
                val profile = u.getAsJsonObject("profile")
                val displayName = profile?.get("display_name")?.asString?.takeIf { it.isNotBlank() }
                    ?: profile?.get("real_name")?.asString ?: ""
                val title = profile?.get("title")?.asString ?: ""
                val status = profile?.get("status_text")?.asString ?: ""
                val isAdmin = u.get("is_admin")?.asBoolean ?: false

                append("- **$displayName** ($userId)")
                if (isAdmin) append(" [admin]")
                appendLine()
                if (title.isNotBlank()) appendLine("  Title: $title")
                if (status.isNotBlank()) appendLine("  Status: $status")
            }
        }
        return SlackToolResult(id, true, formatted.trim())
    }

    private suspend fun getUser(id: String, args: com.google.gson.JsonObject, token: String): SlackToolResult {
        val userId = args.get("user")?.asString ?: return SlackToolResult(id, false, "Parameter 'user' required")

        val json = get("$API_URL/users.info?user=$userId", token)
            ?: return SlackToolResult(id, false, "Error calling Slack API")
        if (!json.get("ok")?.asBoolean.let { it == true }) {
            return SlackToolResult(id, false, "Slack error: ${json.get("error")?.asString}")
        }

        val u = json.getAsJsonObject("user")
        val profile = u.getAsJsonObject("profile")
        val formatted = buildString {
            appendLine("User: ${profile?.get("real_name")?.asString ?: userId}")
            appendLine("ID: ${u.get("id")?.asString}")
            appendLine("Display Name: ${profile?.get("display_name")?.asString ?: "(none)"}")
            appendLine("Email: ${profile?.get("email")?.asString ?: "(hidden)"}")
            appendLine("Title: ${profile?.get("title")?.asString ?: "(none)"}")
            appendLine("Status: ${profile?.get("status_emoji")?.asString ?: ""} ${profile?.get("status_text")?.asString ?: ""}")
            appendLine("Timezone: ${u.get("tz_label")?.asString ?: "unknown"}")
            appendLine("Admin: ${u.get("is_admin")?.asBoolean ?: false}")
        }
        return SlackToolResult(id, true, formatted.trim())
    }

    private suspend fun searchMessages(id: String, args: com.google.gson.JsonObject, token: String): SlackToolResult {
        val query = args.get("query")?.asString ?: return SlackToolResult(id, false, "Parameter 'query' required")
        val count = (args.get("count")?.asInt ?: 20).coerceIn(1, 100)

        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val json = get("$API_URL/search.messages?query=$encodedQuery&count=$count", token)
            ?: return SlackToolResult(id, false, "Error calling Slack API")
        if (!json.get("ok")?.asBoolean.let { it == true }) {
            return SlackToolResult(id, false, "Slack error: ${json.get("error")?.asString}")
        }

        val messages = json.getAsJsonObject("messages")
        val matches = messages?.getAsJsonArray("matches")
        val total = messages?.get("total")?.asInt ?: 0
        if (matches == null || matches.size() == 0) return SlackToolResult(id, true, "No messages found for: \"$query\"")

        val formatted = buildString {
            appendLine("Search results for \"$query\" ($total total):")
            appendLine()
            matches.forEachIndexed { i, match ->
                val m = match.asJsonObject
                val text = m.get("text")?.asString ?: ""
                val user = m.get("username")?.asString ?: m.get("user")?.asString ?: "unknown"
                val channel = m.getAsJsonObject("channel")?.get("name")?.asString ?: ""
                val ts = m.get("ts")?.asString ?: ""
                val permalink = m.get("permalink")?.asString ?: ""
                appendLine("${i + 1}. **$user** in #$channel:")
                appendLine("   $text")
                appendLine("   ts: $ts")
                if (permalink.isNotBlank()) appendLine("   link: $permalink")
                appendLine()
            }
        }
        return SlackToolResult(id, true, formatted.trim())
    }

    private suspend fun addReaction(id: String, args: com.google.gson.JsonObject, token: String): SlackToolResult {
        val channel = args.get("channel")?.asString ?: return SlackToolResult(id, false, "Parameter 'channel' required")
        val timestamp = args.get("timestamp")?.asString ?: return SlackToolResult(id, false, "Parameter 'timestamp' required")
        val name = args.get("name")?.asString ?: return SlackToolResult(id, false, "Parameter 'name' required")

        val body = com.google.gson.JsonObject().apply {
            addProperty("channel", channel)
            addProperty("timestamp", timestamp)
            addProperty("name", name)
        }.toString()

        val json = post("$API_URL/reactions.add", body, token)
            ?: return SlackToolResult(id, false, "Error calling Slack API")
        if (!json.get("ok")?.asBoolean.let { it == true }) {
            return SlackToolResult(id, false, "Slack error: ${json.get("error")?.asString}")
        }

        return SlackToolResult(id, true, "Reaction :$name: added successfully.")
    }

    private suspend fun setTopic(id: String, args: com.google.gson.JsonObject, token: String): SlackToolResult {
        val channel = args.get("channel")?.asString ?: return SlackToolResult(id, false, "Parameter 'channel' required")
        val topic = args.get("topic")?.asString ?: return SlackToolResult(id, false, "Parameter 'topic' required")

        val body = com.google.gson.JsonObject().apply {
            addProperty("channel", channel)
            addProperty("topic", topic)
        }.toString()

        val json = post("$API_URL/conversations.setTopic", body, token)
            ?: return SlackToolResult(id, false, "Error calling Slack API")
        if (!json.get("ok")?.asBoolean.let { it == true }) {
            return SlackToolResult(id, false, "Slack error: ${json.get("error")?.asString}")
        }

        return SlackToolResult(id, true, "Channel topic updated to: $topic")
    }

    private suspend fun listFiles(id: String, args: com.google.gson.JsonObject, token: String): SlackToolResult {
        val channel = args.get("channel")?.asString
        val count = (args.get("count")?.asInt ?: 20).coerceIn(1, 100)

        val url = buildString {
            append("$API_URL/files.list?count=$count")
            if (!channel.isNullOrBlank()) append("&channel=$channel")
        }

        val json = get(url, token)
            ?: return SlackToolResult(id, false, "Error calling Slack API")
        if (!json.get("ok")?.asBoolean.let { it == true }) {
            return SlackToolResult(id, false, "Slack error: ${json.get("error")?.asString}")
        }

        val files = json.getAsJsonArray("files")
        if (files == null || files.size() == 0) return SlackToolResult(id, true, "No files found.")

        val formatted = buildString {
            appendLine("Files (${files.size()}):")
            appendLine()
            files.forEach { file ->
                val f = file.asJsonObject
                val name = f.get("name")?.asString ?: ""
                val fileType = f.get("filetype")?.asString ?: ""
                val size = f.get("size")?.asLong ?: 0
                val user = f.get("user")?.asString ?: ""
                val created = f.get("created")?.asLong?.let {
                    java.time.Instant.ofEpochSecond(it).toString().take(10)
                } ?: ""
                val sizeStr = when {
                    size > 1_000_000 -> "${size / 1_000_000} MB"
                    size > 1_000 -> "${size / 1_000} KB"
                    else -> "$size B"
                }
                appendLine("- **$name** ($fileType, $sizeStr)")
                appendLine("  User: $user | Created: $created")
            }
        }
        return SlackToolResult(id, true, formatted.trim())
    }

    private suspend fun deleteMessage(id: String, args: com.google.gson.JsonObject, token: String): SlackToolResult {
        val channel = args.get("channel")?.asString ?: return SlackToolResult(id, false, "Parameter 'channel' required")
        val ts = args.get("ts")?.asString ?: return SlackToolResult(id, false, "Parameter 'ts' required")

        val body = com.google.gson.JsonObject().apply {
            addProperty("channel", channel)
            addProperty("ts", ts)
        }.toString()

        val json = post("$API_URL/chat.delete", body, token)
            ?: return SlackToolResult(id, false, "Error calling Slack API")
        if (!json.get("ok")?.asBoolean.let { it == true }) {
            return SlackToolResult(id, false, "Slack error: ${json.get("error")?.asString}")
        }

        return SlackToolResult(id, true, "Message deleted successfully.")
    }

    // --- HTTP helpers ---

    private suspend fun get(url: String, token: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code !in 200..299) { Log.e(TAG, "GET $url -> ${resp.code}"); return@withContext null }
            JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
        } catch (e: Exception) { Log.e(TAG, "GET error", e); null }
    }

    private suspend fun post(url: String, body: String, token: String): com.google.gson.JsonObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code !in 200..299) { Log.e(TAG, "POST $url -> ${resp.code}"); return@withContext null }
            JsonParser.parseString(resp.body?.string() ?: "").asJsonObject
        } catch (e: Exception) { Log.e(TAG, "POST error", e); null }
    }
}
