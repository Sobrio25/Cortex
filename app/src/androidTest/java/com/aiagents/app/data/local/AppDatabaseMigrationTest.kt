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

    private companion object {
        const val DATABASE_NAME = "migration-38-41"
    }
}
