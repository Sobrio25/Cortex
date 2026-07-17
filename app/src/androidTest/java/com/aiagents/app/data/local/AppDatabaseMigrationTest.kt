package com.aiagents.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiagents.app.data.orchestration.AgentOrchestrator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrates38To41WithoutLosingExistingRows() {
        helper.createDatabase(DATABASE_NAME, 38).apply {
            execSQL(
                """
                INSERT INTO workspaces (
                    id, name, description, activeAgentId, selectedModel, systemPrompt,
                    externalStorageUri, createdAt, updatedAt
                ) VALUES (99, 'Migración', 'dato existente', NULL, '', '', NULL, 1, 1)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            41,
            true,
            AppDatabase.MIGRATION_38_39,
            AppDatabase.MIGRATION_39_40,
            AppDatabase.MIGRATION_40_41
        ).use { database ->
            database.query("SELECT description FROM workspaces WHERE id = 99").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("dato existente", cursor.getString(0))
            }
            database.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN " +
                    "('download_progress', 'skills', 'skill_reviews')"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(3, cursor.getInt(0))
            }
            database.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' " +
                    "AND name = 'subagent_executions'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrates43To44AndPreservesScheduledTasks() {
        helper.createDatabase(SCHEDULED_TASK_DATABASE_NAME, 43).apply {
            execSQL(
                """
                INSERT INTO scheduled_tasks (
                    id, workspaceId, agentName, prompt, scheduleType, scheduleValue,
                    label, enabled, lastRunAt, lastResult, nextRunAt, runCount, createdAt
                ) VALUES (
                    7, 1, 'Cortex', 'Resume las noticias', 'daily', '07:00',
                    'Noticias', 1, NULL, NULL, 1000, 0, 1
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            SCHEDULED_TASK_DATABASE_NAME,
            44,
            true,
            AppDatabase.MIGRATION_43_44
        ).use { database ->
            database.query(
                "SELECT prompt, conversationId FROM scheduled_tasks WHERE id = 7"
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("Resume las noticias", cursor.getString(0))
                assertEquals(true, cursor.isNull(1))
            }
            database.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' " +
                    "AND name = 'index_scheduled_tasks_conversationId'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrates44To47PreservingDataAndNormalizingLegacyOrchestratorIdentity() {
        helper.createDatabase(LATEST_CHAIN_DATABASE_NAME, 44).apply {
            execSQL(
                """
                INSERT INTO agents (
                    id, name, role, systemPrompt, temperature, maxTokens, folderPath,
                    enableTerminal, whenToUse, createdAt, updatedAt, sarcasmLevel,
                    creativityLevel, formalityLevel, empathyLevel, technicalPrecision,
                    useLocalRouting, enabledTools, isSystemAgent
                ) VALUES (
                    77, 'Clawdy', 'Agent Orchestrator',
                    'Eres Cortex, el orquestador central de agentes AI.',
                    0.7, 4096, 'agents/cortex', 1, '', 1, 1,
                    50, 50, 50, 50, 50, 0, '', 1
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO finance_transactions (
                    id, type, amount, currency, category, description, date, createdAt
                ) VALUES (88, 'EXPENSE', 42.5, 'MXN', 'Prueba', 'Dato legado', 1, 1)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            LATEST_CHAIN_DATABASE_NAME,
            47,
            false,
            AppDatabase.MIGRATION_44_45,
            AppDatabase.MIGRATION_45_46,
            AppDatabase.MIGRATION_46_47
        ).use { database ->
            database.query("SELECT name, systemPrompt FROM agents WHERE id = 77").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("Clawdy", cursor.getString(0))
                assertEquals(AgentOrchestrator.DEFAULT_ORCHESTRATOR_PROMPT, cursor.getString(1))
            }
            database.query(
                "SELECT amount, description FROM finance_transactions WHERE id = 88"
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(42.5, cursor.getDouble(0), 0.0)
                assertEquals("Dato legado", cursor.getString(1))
            }
        }
    }

    @Test
    fun migrates46To47SeparatingVoiceAssistantConversations() {
        helper.createDatabase(ASSISTANT_CONTEXT_DATABASE_NAME, 46).apply {
            execSQL(
                "INSERT INTO workspaces (id, name, description, activeAgentId, selectedModel, systemPrompt, externalStorageUri, createdAt, updatedAt) " +
                    "VALUES (90, '__global__', '', NULL, '', '', NULL, 1, 1), " +
                    "(91, 'Proyecto', '', NULL, '', '', NULL, 1, 1)"
            )
            execSQL(
                "INSERT INTO conversations (id, workspaceId, title, createdAt, updatedAt, parentConversationId, delegationAgentName, delegationTask, status, lastMemoryExtraction) " +
                    "VALUES (92, 90, 'Assistant', 1, 1, NULL, NULL, NULL, 'active', NULL), " +
                    "(93, 91, 'Chat', 1, 1, NULL, NULL, NULL, 'active', NULL)"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            ASSISTANT_CONTEXT_DATABASE_NAME,
            47,
            true,
            AppDatabase.MIGRATION_46_47
        ).use { database ->
            database.query("SELECT contextKind FROM conversations WHERE id = 92").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("VOICE_ASSISTANT", cursor.getString(0))
            }
            database.query("SELECT contextKind FROM conversations WHERE id = 93").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("CHAT", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrates47To48ImportingLatestEnabledVoiceSettingsBeforeDroppingTable() {
        helper.createDatabase(VOICE_DATABASE_NAME, 47).apply {
            execSQL(
                """
                INSERT INTO stt_settings (
                    id, workspaceId, enabled, mode, localModelType, localEngine,
                    cloudProvider, apiKey, language, createdAt, updatedAt
                ) VALUES
                    (1, 10, 1, 'LOCAL', 'AUTO', 'AUTO',
                        'ANDROID_SPEECH_RECOGNIZER', '', 'es', 1, 10),
                    (2, 20, 1, 'CLOUD', 'AUTO', 'AUTO',
                        'DEEPGRAM', 'secret', 'en', 1, 20)
                """.trimIndent()
            )
            close()
        }

        var imported: LegacyWorkspaceVoiceSettings? = null
        val migration = AppDatabase.migration47To48 { settings ->
            imported = settings
            true
        }
        helper.runMigrationsAndValidate(
            VOICE_DATABASE_NAME,
            48,
            true,
            migration
        ).use { database ->
            assertEquals("DEEPGRAM", imported?.cloudProvider)
            assertEquals("secret", imported?.apiKey)
            assertEquals("en", imported?.language)
            database.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'stt_settings'"
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertFalse(cursor.getInt(0) != 0)
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-38-41"
        const val SCHEDULED_TASK_DATABASE_NAME = "migration-43-44"
        const val LATEST_CHAIN_DATABASE_NAME = "migration-44-47"
        const val ASSISTANT_CONTEXT_DATABASE_NAME = "migration-46-47-assistant-context"
        const val VOICE_DATABASE_NAME = "migration-47-48-global-voice"
    }
}
