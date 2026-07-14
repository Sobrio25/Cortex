package com.aiagents.app.data.terminal

import com.aiagents.app.data.memory.CortexMemoryMutationResult
import com.aiagents.app.data.memory.CortexMemorySnapshot
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CortexMemoryToolHandlerTest {
    @Test
    fun schemaExposesOnlyBoundedMemoryAndUserMutationsWithoutReadAction() {
        val definitions = CortexMemoryToolHandler.getToolDefinitionsJson()

        assertEquals(1, definitions.size)
        val definition = Gson().toJsonTree(definitions.single()).asJsonObject
        val function = definition.getAsJsonObject("function")
        val parameters = function.getAsJsonObject("parameters")
        val properties = parameters.getAsJsonObject("properties")

        assertEquals("function", definition.get("type").asString)
        assertEquals(CortexMemoryToolHandler.TOOL_NAME, function.get("name").asString)
        assertTrue(function.get("description").asString.contains("MEMORY.md (2200 chars)"))
        assertTrue(function.get("description").asString.contains("USER.md (1375 chars)"))
        assertEquals(
            listOf("memory", "user"),
            stringValues(properties.target().getAsJsonArray("enum"))
        )
        assertEquals(listOf("target"), stringValues(parameters.getAsJsonArray("required")))

        val topLevelActions = stringValues(properties.action().getAsJsonArray("enum"))
        val batchActions = stringValues(
            properties.action("operations")
                .getAsJsonObject("items")
                .getAsJsonObject("properties")
                .action()
                .getAsJsonArray("enum")
        )

        assertEquals(listOf("add", "replace", "remove"), topLevelActions)
        assertEquals(topLevelActions, batchActions)
        assertFalse(topLevelActions.contains("read"))
        assertFalse(batchActions.contains("read"))
    }

    @Test
    fun boundedMemoryMutationIsNotAvailableToSubagentsOrBackgroundWorkers() {
        assertFalse(ToolExecutionProfiles.SUBAGENT.contains(CortexMemoryToolHandler.TOOL_NAME))
        assertFalse(ToolExecutionProfiles.BACKGROUND.contains(CortexMemoryToolHandler.TOOL_NAME))
        assertTrue(ToolExecutionProfiles.SUBAGENT.contains(MemoryToolHandler.TOOL_SEARCH))
        assertTrue(ToolExecutionProfiles.BACKGROUND.contains(MemoryToolHandler.TOOL_SEARCH))
        assertFalse(ToolExecutionProfiles.SUBAGENT.contains(MemoryToolHandler.TOOL_SAVE))
        assertFalse(ToolExecutionProfiles.BACKGROUND.contains(MemoryToolHandler.TOOL_SAVE))
    }

    @Test
    fun successfulResponseDoesNotPersistAnotherCopyOfMemoryInConversationHistory() {
        val snapshot = CortexMemorySnapshot(
            content = "Preferencia duradera",
            entries = listOf("Preferencia duradera"),
            usedChars = 20,
            maxChars = 2_200,
            usagePercent = 1,
            revision = "revision",
            storageError = null
        )
        val success = JsonParser.parseString(
            CortexMemoryToolResponseFormatter.result(
                CortexMemoryMutationResult(true, true, "updated", snapshot)
            )
        ).asJsonObject
        val recoverableFailure = JsonParser.parseString(
            CortexMemoryToolResponseFormatter.result(
                CortexMemoryMutationResult(false, false, "overflow", snapshot)
            )
        ).asJsonObject
        val unauthorized = JsonParser.parseString(
            CortexMemoryToolResponseFormatter.error("denied", snapshot)
        ).asJsonObject

        assertTrue(success.get("done").asBoolean)
        assertFalse(success.has("current_entries"))
        assertTrue(recoverableFailure.has("current_entries"))
        assertEquals("Preferencia duradera", recoverableFailure.getAsJsonArray("current_entries")[0].asString)
        assertFalse(unauthorized.has("current_entries"))
    }

    @Test
    fun responsePreservesTheSelectedUserTarget() {
        val snapshot = CortexMemorySnapshot(
            content = "# USER.md",
            entries = listOf("# USER.md"),
            usedChars = 9,
            maxChars = 1_375,
            usagePercent = 1,
            revision = "user-revision",
            storageError = null
        )

        val response = JsonParser.parseString(
            CortexMemoryToolResponseFormatter.result(
                CortexMemoryMutationResult(true, true, "updated", snapshot),
                target = CortexMemoryToolHandler.TARGET_USER
            )
        ).asJsonObject

        assertEquals("user", response.get("target").asString)
        assertEquals("9/1375", response.get("usage").asString)
    }

    @Test
    fun fourthRecoverableFailureBecomesTerminalAndStopsRepeatingEntries() {
        val limiter = CortexMemoryFailureLimiter()
        assertFalse(limiter.isTerminalAfterFailure("turn-1"))
        assertFalse(limiter.isTerminalAfterFailure("turn-1"))
        assertFalse(limiter.isTerminalAfterFailure("turn-1"))
        assertTrue(limiter.isTerminalAfterFailure("turn-1"))

        val snapshot = CortexMemorySnapshot(
            content = "entrada existente",
            entries = listOf("entrada existente"),
            usedChars = 17,
            maxChars = 2_200,
            usagePercent = 1,
            revision = "revision",
            storageError = null
        )
        val terminal = JsonParser.parseString(
            CortexMemoryToolResponseFormatter.terminalFailure(
                CortexMemoryMutationResult(false, false, "overflow", snapshot)
            )
        ).asJsonObject

        assertTrue(terminal.get("done").asBoolean)
        assertFalse(terminal.has("current_entries"))
        assertTrue(terminal.get("next_action").asString.contains("Do not call memory again"))

        limiter.reset("turn-1")
        assertFalse(limiter.isTerminalAfterFailure("turn-1"))
    }

    private fun JsonObject.target(): JsonObject = getAsJsonObject("target")

    private fun JsonObject.action(name: String = "action"): JsonObject = getAsJsonObject(name)

    private fun stringValues(array: com.google.gson.JsonArray): List<String> =
        array.map { it.asString }
}
