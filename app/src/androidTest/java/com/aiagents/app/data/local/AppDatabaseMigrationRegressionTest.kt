package com.aiagents.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiagents.app.data.orchestration.AgentOrchestrator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression coverage kept separate from the historical migration smoke test. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationRegressionTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrates41To47RepairingFtsAndPreservingUserCustomizedData() {
        helper.createDatabase(DATABASE_NAME, 41).apply {
            insertAgent(
                id = 1,
                name = "Cortex personalizado",
                role = "Agent Orchestrator",
                prompt = "Eres Cortex, el orquestador central de agentes AI.",
                folder = "agents/cortex",
                createdAt = 10,
                updatedAt = 10
            )
            insertAgent(
                id = 2,
                name = "Programmer",
                role = "Programming",
                prompt = "generated",
                folder = "agents/programmer",
                createdAt = 20,
                updatedAt = 20
            )
            insertAgent(
                id = 3,
                name = "Writer",
                role = "Writing",
                prompt = "customized by user",
                folder = "agents/writer",
                createdAt = 30,
                updatedAt = 31
            )
            execSQL(
                """
                INSERT INTO workspaces (
                    id, name, description, activeAgentId, selectedModel, systemPrompt,
                    externalStorageUri, createdAt, updatedAt
                ) VALUES (10, 'Beta', 'persistir', 2, '', '', NULL, 1, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO scheduled_tasks (
                    id, workspaceId, agentName, prompt, scheduleType, scheduleValue,
                    label, enabled, lastRunAt, lastResult, nextRunAt, runCount, createdAt
                ) VALUES (11, 10, 'Programmer', 'prueba', 'daily', '08:00', '', 1,
                    NULL, NULL, 100, 0, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO cortex_memories (
                    id, content, category, subcategory, importance, confidence, source,
                    accessCount, createdAt, updatedAt, lastAccessedAt, expiresAt
                ) VALUES (12, 'memoria original', 'beta', 'migration', 5, 1.0,
                    'test', 0, 1, 1, 1, NULL)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            47,
            false,
            AppDatabase.MIGRATION_41_42,
            AppDatabase.MIGRATION_42_43,
            AppDatabase.MIGRATION_43_44,
            AppDatabase.MIGRATION_44_45,
            AppDatabase.MIGRATION_45_46,
            AppDatabase.MIGRATION_46_47
        ).use { database ->
            database.query("SELECT COUNT(*) FROM agents WHERE id = 2").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT systemPrompt FROM agents WHERE id = 3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("customized by user", cursor.getString(0))
            }
            database.query("SELECT systemPrompt FROM agents WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(AgentOrchestrator.DEFAULT_ORCHESTRATOR_PROMPT, cursor.getString(0))
            }
            database.query("SELECT activeAgentId FROM workspaces WHERE id = 10").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
            }
            database.query(
                "SELECT agentName, conversationId FROM scheduled_tasks WHERE id = 11"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Cortex personalizado", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }

            database.execSQL("UPDATE cortex_memories SET content = 'memoria actualizada' WHERE id = 12")
            database.query(
                "SELECT rowid FROM cortex_memory_fts WHERE cortex_memory_fts MATCH 'actualizada'"
            ).use { cursor -> assertTrue(cursor.moveToFirst()) }
            database.execSQL("DELETE FROM cortex_memories WHERE id = 12")
            database.query(
                "SELECT rowid FROM cortex_memory_fts WHERE cortex_memory_fts MATCH 'actualizada'"
            ).use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertAgent(
        id: Long,
        name: String,
        role: String,
        prompt: String,
        folder: String,
        createdAt: Long,
        updatedAt: Long
    ) {
        execSQL(
            """
            INSERT INTO agents (
                id, name, role, systemPrompt, temperature, maxTokens, folderPath,
                enableTerminal, whenToUse, createdAt, updatedAt, sarcasmLevel,
                creativityLevel, formalityLevel, empathyLevel, technicalPrecision,
                useLocalRouting, enabledTools, isSystemAgent
            ) VALUES (?, ?, ?, ?, 0.7, 4096, ?, 1, '', ?, ?, 50, 50, 50, 50, 50, 0, '', 1)
            """.trimIndent(),
            arrayOf<Any>(id, name, role, prompt, folder, createdAt, updatedAt)
        )
    }

    private companion object {
        const val DATABASE_NAME = "migration-regression-41-47"
    }
}
