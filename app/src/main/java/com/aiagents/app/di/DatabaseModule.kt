package com.aiagents.app.di

import android.content.Context
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
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
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
                AppDatabase.MIGRATION_43_44
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    insertDefaultCortex(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    AppDatabase.repairMemoryFtsTriggers(db)
                    AppDatabase.ensureBuiltInSkills(db)
                }
            })
            .build()

    /** New installations start with Cortex only; task workers are created in memory on demand. */
    private fun insertDefaultCortex(db: SupportSQLiteDatabase) {
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
                "Cortex",
                "Agent Orchestrator",
                AgentOrchestrator.DEFAULT_CORTEX_PROMPT,
                0.7,
                8192,
                "agents/cortex",
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
    fun provideFinanceDao(database: AppDatabase): FinanceDao = database.financeDao()

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
