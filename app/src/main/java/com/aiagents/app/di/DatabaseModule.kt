package com.aiagents.app.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aiagents.app.data.local.AgentDao
import com.aiagents.app.data.local.AppDatabase
import com.aiagents.app.data.local.CommandPermissionDao
import com.aiagents.app.data.local.ConversationDao
import com.aiagents.app.data.local.CustomLocalModelDao
import com.aiagents.app.data.local.DownloadProgressDao
import com.aiagents.app.data.local.FileDao
import com.aiagents.app.data.local.FinanceDao
import com.aiagents.app.data.local.FinanceDatabase
import com.aiagents.app.data.local.MCPDao
import com.aiagents.app.data.local.MemoryDao
import com.aiagents.app.data.local.MessageDao
import com.aiagents.app.data.local.STTSettingsDao
import com.aiagents.app.data.local.ScheduledTaskDao
import com.aiagents.app.data.local.SkillDao
import com.aiagents.app.data.local.SkillReviewDao
import com.aiagents.app.data.local.SubagentExecutionDao
import com.aiagents.app.data.local.TodoDao
import com.aiagents.app.data.local.WorkspaceDao
import com.aiagents.app.data.orchestration.AgentOrchestrator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val TAG = "DatabaseModule"

    @Provides
    @Singleton
    fun provideFinanceDatabase(@ApplicationContext context: Context): FinanceDatabase =
        Room.databaseBuilder(
            context,
            FinanceDatabase::class.java,
            FinanceDatabase.DATABASE_NAME
        ).build()

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        financeDatabase: FinanceDatabase
    ): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ai_agents_db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17,
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25,
                AppDatabase.MIGRATION_25_26,
                AppDatabase.MIGRATION_26_27,
                AppDatabase.MIGRATION_27_28,
                AppDatabase.MIGRATION_28_29,
                AppDatabase.MIGRATION_29_30,
                AppDatabase.MIGRATION_30_31,
                AppDatabase.MIGRATION_31_32,
                AppDatabase.MIGRATION_32_33,
                AppDatabase.MIGRATION_33_34,
                AppDatabase.MIGRATION_34_35,
                AppDatabase.MIGRATION_35_36,
                AppDatabase.MIGRATION_36_37,
                AppDatabase.MIGRATION_37_38,
                AppDatabase.MIGRATION_38_39,
                AppDatabase.MIGRATION_39_40,
                AppDatabase.MIGRATION_40_41,
                AppDatabase.MIGRATION_41_42,
                AppDatabase.MIGRATION_42_43,
                AppDatabase.MIGRATION_43_44,
                AppDatabase.MIGRATION_44_45,
                AppDatabase.MIGRATION_45_46,
                AppDatabase.MIGRATION_46_47
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    insertDefaultAssistant(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    runCatching {
                        migrateLegacyFinanceData(
                            sourceDatabase = db,
                            targetDatabase = financeDatabase.openHelper.writableDatabase
                        )
                    }.onFailure { error ->
                        // Keep the legacy table intact so migration can retry on
                        // the next launch without losing financial records.
                        Log.e(TAG, "Unable to migrate legacy finance data", error)
                    }
                    AppDatabase.repairMemoryFtsTriggers(db)
                    AppDatabase.ensureBuiltInSkills(db)
                }
            })
            .build()

    private fun migrateLegacyFinanceData(
        sourceDatabase: SupportSQLiteDatabase,
        targetDatabase: SupportSQLiteDatabase
    ) {
        val hasLegacyTable = sourceDatabase.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'finance_transactions'"
        ).use { it.moveToFirst() }
        if (!hasLegacyTable) return

        var migratedRows = 0
        targetDatabase.beginTransaction()
        try {
            val insert = targetDatabase.compileStatement(
                """
                INSERT OR REPLACE INTO finance_transactions (
                    id, type, amount, currency, category, description,
                    date, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            try {
                sourceDatabase.query(
                    """
                    SELECT id, type, amount, currency, category, description, date, createdAt
                    FROM finance_transactions
                    ORDER BY id
                    """.trimIndent()
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        insert.clearBindings()
                        insert.bindLong(1, cursor.getLong(0))
                        insert.bindString(2, cursor.getString(1))
                        insert.bindDouble(3, cursor.getDouble(2))
                        insert.bindString(4, cursor.getString(3))
                        insert.bindString(5, cursor.getString(4))
                        insert.bindString(6, cursor.getString(5))
                        insert.bindLong(7, cursor.getLong(6))
                        insert.bindLong(8, cursor.getLong(7))
                        insert.bindLong(9, cursor.getLong(7))
                        insert.executeInsert()
                        migratedRows++
                    }
                }
            } finally {
                insert.close()
            }
            targetDatabase.setTransactionSuccessful()
        } finally {
            targetDatabase.endTransaction()
        }

        // Removal happens only after the target transaction commits.
        sourceDatabase.execSQL("DROP TABLE finance_transactions")
        Log.i(TAG, "Migrated $migratedRows finance rows to ${FinanceDatabase.DATABASE_NAME}")
    }

    /** New installations start with one configurable assistant; workers are created on demand. */
    private fun insertDefaultAssistant(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT INTO agents (
                name, role, systemPrompt, temperature, maxTokens, folderPath,
                enableTerminal, whenToUse, createdAt, updatedAt, sarcasmLevel,
                creativityLevel, formalityLevel, empathyLevel, technicalPrecision,
                useLocalRouting, enabledTools, isSystemAgent
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                "Assistant",
                "Agent Orchestrator",
                AgentOrchestrator.DEFAULT_ORCHESTRATOR_PROMPT,
                0.7,
                8192,
                "agents/assistant",
                1,
                "",
                now,
                now,
                0,
                50,
                50,
                50,
                70,
                0,
                "",
                0
            )
        )
    }

    @Provides
    fun provideSubagentExecutionDao(database: AppDatabase): SubagentExecutionDao =
        database.subagentExecutionDao()

    @Provides
    fun provideAgentDao(database: AppDatabase): AgentDao = database.agentDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideFileDao(database: AppDatabase): FileDao = database.fileDao()

    @Provides
    fun provideWorkspaceDao(database: AppDatabase): WorkspaceDao = database.workspaceDao()

    @Provides
    fun provideCommandPermissionDao(database: AppDatabase): CommandPermissionDao =
        database.commandPermissionDao()

    @Provides
    fun provideSTTSettingsDao(database: AppDatabase): STTSettingsDao = database.sttSettingsDao()

    @Provides
    fun provideMCPDao(database: AppDatabase): MCPDao = database.mcpDao()

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMemoryDao(database: AppDatabase): MemoryDao = database.memoryDao()

    @Provides
    fun provideCustomLocalModelDao(database: AppDatabase): CustomLocalModelDao =
        database.customLocalModelDao()

    @Provides
    fun provideFinanceDao(database: FinanceDatabase): FinanceDao = database.financeDao()

    @Provides
    fun provideTodoDao(database: AppDatabase): TodoDao = database.todoDao()

    @Provides
    fun provideScheduledTaskDao(database: AppDatabase): ScheduledTaskDao =
        database.scheduledTaskDao()

    @Provides
    fun provideDownloadProgressDao(database: AppDatabase): DownloadProgressDao =
        database.downloadProgressDao()

    @Provides
    fun provideSkillDao(database: AppDatabase): SkillDao = database.skillDao()

    @Provides
    fun provideSkillReviewDao(database: AppDatabase): SkillReviewDao = database.skillReviewDao()
}
