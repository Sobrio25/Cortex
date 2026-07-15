package com.aiagents.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    private companion object {
        const val DATABASE_NAME = "migration-38-41"
        const val SCHEDULED_TASK_DATABASE_NAME = "migration-43-44"
    }
}
