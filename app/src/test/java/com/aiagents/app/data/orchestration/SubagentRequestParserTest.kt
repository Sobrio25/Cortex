package com.aiagents.app.data.orchestration

import com.aiagents.app.domain.model.SubagentExecutionMode
import com.aiagents.app.domain.model.SubagentFailurePolicy
import com.aiagents.app.domain.model.SubagentRole
import com.aiagents.app.domain.model.SubagentWorkspacePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentRequestParserTest {
    @Test
    fun parsesTypedBatch() {
        val result = SubagentRequestParser.parse(
            """
            {
              "mode": "sequential",
              "failure_policy": "fail_fast",
              "tasks": [
                {
                  "agent_name": "Researcher",
                  "goal": "Collect primary sources",
                  "context": "Use official documentation",
                  "acceptance_criteria": "At least two sources",
                  "role": "leaf",
                  "workspace_policy": "read_only_shared",
                  "capabilities": ["google_docs"],
                  "max_iterations": 12
                },
                {
                  "agent_name": "Writer",
                  "goal": "Write the summary",
                  "workspace_policy": "write_exclusive"
                }
              ]
            }
            """.trimIndent()
        ).getOrThrow()

        assertEquals(SubagentExecutionMode.SEQUENTIAL, result.mode)
        assertEquals(SubagentFailurePolicy.FAIL_FAST, result.failurePolicy)
        assertEquals(2, result.tasks.size)
        assertEquals(SubagentRole.LEAF, result.tasks.first().role)
        assertEquals(setOf("google_docs"), result.tasks.first().capabilities)
        assertEquals(SubagentWorkspacePolicy.WRITE_EXCLUSIVE, result.tasks.last().workspacePolicy)
        assertEquals(12, result.tasks.first().maxIterations)
    }

    @Test
    fun acceptsLegacySingleTaskShape() {
        val result = SubagentRequestParser.parse(
            """{"agent_name":"Programmer","task":"Fix tests","mode":"parallel"}"""
        ).getOrThrow()

        assertEquals(1, result.tasks.size)
        assertEquals("Fix tests", result.tasks.single().goal)
    }

    @Test
    fun agentNameIsOptionalForEphemeralWorkers() {
        val result = SubagentRequestParser.parse(
            """{"tasks":[{"goal":"Review the implementation"}]}"""
        ).getOrThrow()

        assertEquals("", result.tasks.single().agentName)
        assertEquals("Review the implementation", result.tasks.single().goal)
    }

    @Test
    fun rejectsUnknownModeAndOversizedBatches() {
        assertTrue(
            SubagentRequestParser.parse(
                """{"mode":"sometimes","tasks":[{"agent_name":"A","goal":"B"}]}"""
            ).isFailure
        )
        val tasks = (1..13).joinToString(",") {
            """{"agent_name":"A","goal":"Task $it"}"""
        }
        assertTrue(SubagentRequestParser.parse("""{"tasks":[$tasks]}""").isFailure)
        assertTrue(
            SubagentRequestParser.parse(
                """{"tasks":[{"agent_name":"A","goal":"B","capabilities":["google_everything"]}]}"""
            ).isFailure
        )
    }
}
