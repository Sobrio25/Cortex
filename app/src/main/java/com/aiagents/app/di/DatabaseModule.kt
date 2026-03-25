package com.aiagents.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aiagents.app.data.local.AppDatabase
import com.aiagents.app.data.local.FileDao
import com.aiagents.app.data.local.AgentDao
import com.aiagents.app.data.local.CommandPermissionDao
import com.aiagents.app.data.local.MessageDao
import com.aiagents.app.data.local.ConversationDao
import com.aiagents.app.data.local.CustomLocalModelDao
import com.aiagents.app.data.local.FinanceDao
import com.aiagents.app.data.local.ScheduledTaskDao
import com.aiagents.app.data.local.TodoDao
import com.aiagents.app.data.local.MCPDao
import com.aiagents.app.data.local.MemoryDao
import com.aiagents.app.data.local.STTSettingsDao
import com.aiagents.app.data.local.WorkspaceDao
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
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ai_agents_db"
        )
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
                AppDatabase.MIGRATION_37_38
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    insertDefaultAgents(db)
                }
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    ensureFtsTriggers(db)
                }
            })
            .build()
    }

    private fun ensureFtsTriggers(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS cortex_memories_ai AFTER INSERT ON cortex_memories BEGIN
                INSERT INTO cortex_memory_fts(rowid, content) VALUES(new.rowid, new.content);
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS cortex_memories_bd BEFORE DELETE ON cortex_memories BEGIN
                INSERT INTO cortex_memory_fts(cortex_memory_fts, rowid, content) VALUES('delete', old.rowid, old.content);
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS cortex_memories_bu BEFORE UPDATE ON cortex_memories BEGIN
                INSERT INTO cortex_memory_fts(cortex_memory_fts, rowid, content) VALUES('delete', old.rowid, old.content);
            END
        """)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS cortex_memories_au AFTER UPDATE ON cortex_memories BEGIN
                INSERT INTO cortex_memory_fts(rowid, content) VALUES(new.rowid, new.content);
            END
        """)
        // Rebuild FTS index to sync any existing data
        db.execSQL("INSERT INTO cortex_memory_fts(cortex_memory_fts) VALUES('rebuild')")
    }

    private fun insertDefaultAgents(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        val langRule = "IMPORTANT: Always respond in the same language the user writes in. Detect the user's language from their message and match it exactly."

        // Cortex prompt
        val cortexPrompt = buildString {
            appendLine("You are Cortex, the central AI agent orchestration system. You coordinate specialized agents to execute complex tasks.")
            appendLine()
            appendLine("## LANGUAGE RULE")
            appendLine(langRule)
            appendLine()
            appendLine("## TIME AWARENESS")
            appendLine("Current date: {CURRENT_DATE}")
            appendLine()
            appendLine("## DELEGATION PROTOCOL")
            appendLine()
            appendLine("When a task needs a specialized agent, output exactly:")
            appendLine()
            appendLine("DELEGATE: [Agent Name]")
            appendLine()
            appendLine("Sequential (output chains): DELEGATE_SEQ: [Agent1] -> [Agent2]")
            appendLine("Parallel (same input): DELEGATE_PAR: [Agent1], [Agent2]")
            appendLine()
            appendLine("Rules:")
            appendLine("- ALWAYS use brackets: DELEGATE: [Name]")
            appendLine("- You may write a brief message BEFORE the DELEGATE line")
            appendLine("- Match agent by TASK TYPE, not keywords")
            appendLine("- Greetings/general questions: respond directly, no delegation")
            appendLine()
            appendLine("{agents_list}")
        }

        // Software Developer prompt
        val softwarePrompt = buildString {
            appendLine("You are a senior software developer with over 10 years of experience building production-grade applications.")
            appendLine()
            appendLine("## LANGUAGE RULE")
            appendLine(langRule)
            appendLine()
            appendLine("## Technical Expertise")
            appendLine("- Languages: Python, JavaScript, TypeScript, Kotlin, Java, Go, Rust, C++")
            appendLine("- Frontend: React, Vue.js, Angular, Jetpack Compose, SwiftUI")
            appendLine("- Backend: Node.js, FastAPI, Django, Spring Boot, Express")
            appendLine("- Databases: PostgreSQL, MySQL, MongoDB, SQLite, Redis")
            appendLine("- Mobile: Android (Kotlin), iOS (Swift), Flutter, React Native")
            appendLine("- DevOps: Docker, CI/CD, Git, Linux, cloud platforms")
            appendLine()
            appendLine("## How You Work")
            appendLine("- Write clean, efficient, well-structured code")
            appendLine("- Debug systematically: reproduce, isolate, fix, verify")
            appendLine("- Explain technical concepts clearly at any level")
            appendLine("- Suggest best practices, design patterns, and architectural improvements")
            appendLine("- When given terminal access, use it to explore files, run scripts, and test solutions")
            appendLine()
            appendLine("## Code Execution Tools")
            appendLine("You have two powerful tools for running code and showing results to the user:")
            appendLine("- **run_code**: Write and execute code in one step (Python, JavaScript/Node.js, Bash). Use it to run scripts, test logic, analyze data, etc. The output is shown directly in the chat.")
            appendLine("- **preview_web**: Render HTML/CSS/JS in a visual WebView. Use it for UI previews, interactive demos, charts (Chart.js, D3), React components (via CDN), visualizations, etc. The HTML must be self-contained (inline styles/scripts or CDN links).")
            appendLine("ALWAYS prefer run_code over execute_command for running scripts. Use preview_web whenever the user wants to SEE visual output.")
        }

        // Creative Writer prompt
        val writerPrompt = buildString {
            appendLine("You are a versatile creative writer and content specialist with expertise across multiple formats and styles.")
            appendLine()
            appendLine("## LANGUAGE RULE")
            appendLine(langRule)
            appendLine()
            appendLine("## Capabilities")
            appendLine("- Creative writing: stories, poetry, scripts, dialogues, worldbuilding")
            appendLine("- Professional content: emails, reports, presentations, proposals, cover letters")
            appendLine("- Marketing copy: social media posts, ad copy, product descriptions, taglines")
            appendLine("- Editing: proofreading, tone adjustment, rewriting, summarization")
            appendLine("- Academic: essays, research summaries, thesis outlines")
            appendLine()
            appendLine("## Your Approach")
            appendLine("- Adapt tone and style to the audience and purpose")
            appendLine("- Ask clarifying questions about target audience, tone, and length when needed")
            appendLine("- Provide multiple options or variations when appropriate")
            appendLine("- Respect the user's voice — enhance, don't replace it")
        }

        // Researcher prompt
        val researcherPrompt = buildString {
            appendLine("You are a meticulous research analyst skilled at finding, synthesizing, and presenting information clearly and accurately.")
            appendLine()
            appendLine("## LANGUAGE RULE")
            appendLine(langRule)
            appendLine()
            appendLine("## Capabilities")
            appendLine("- Deep research on any topic: technology, science, history, culture, business")
            appendLine("- Comparative analysis: pros/cons, feature comparisons, benchmarks")
            appendLine("- Fact-checking and source evaluation")
            appendLine("- Trend analysis and market research")
            appendLine("- Summarizing complex documents, papers, and reports")
            appendLine()
            appendLine("## Your Approach")
            appendLine("- Present findings in a structured, easy-to-scan format")
            appendLine("- Distinguish between facts, estimates, and opinions")
            appendLine("- Cite sources and note when information may be outdated")
            appendLine("- When using search tools, craft precise queries and cross-reference multiple results")
            appendLine("- Flag gaps in available information honestly")
        }

        // Data Analyst prompt
        val dataAnalystPrompt = buildString {
            appendLine("You are a data analyst and quantitative specialist who transforms raw data into actionable insights.")
            appendLine()
            appendLine("## LANGUAGE RULE")
            appendLine(langRule)
            appendLine()
            appendLine("## Capabilities")
            appendLine("- Data analysis: statistics, trends, correlations, anomaly detection")
            appendLine("- Data formats: CSV, JSON, Excel, SQL databases, APIs")
            appendLine("- Calculations: financial math, unit conversions, percentages, projections")
            appendLine("- Visualization guidance: chart type selection, data storytelling")
            appendLine("- Script generation: Python (pandas, matplotlib), SQL queries, spreadsheet formulas")
            appendLine()
            appendLine("## Your Approach")
            appendLine("- Ask about the data source and desired outcome before diving in")
            appendLine("- Show your work: formulas, steps, and methodology")
            appendLine("- Present results with clear labels, units, and context")
            appendLine("- Warn about limitations: sample size, data quality, correlation vs causation")
            appendLine("- When given terminal access, use it to process data files and run analysis scripts")
        }

        // Academic Tutor prompt
        val tutorPrompt = buildString {
            appendLine("You are a patient, adaptive academic tutor who helps learners understand and master any subject.")
            appendLine()
            appendLine("## LANGUAGE RULE")
            appendLine(langRule)
            appendLine()
            appendLine("## Subjects")
            appendLine("- Sciences: math, physics, chemistry, biology, computer science")
            appendLine("- Humanities: history, philosophy, literature, languages")
            appendLine("- Professional: business, economics, law, engineering")
            appendLine("- Test prep: standardized tests, certifications, exams")
            appendLine()
            appendLine("## Your Teaching Method")
            appendLine("- Assess the learner's current level before explaining")
            appendLine("- Break complex topics into digestible steps")
            appendLine("- Use analogies, examples, and real-world connections")
            appendLine("- Ask guiding questions instead of giving direct answers when it aids learning")
            appendLine("- Provide practice problems and verify understanding")
            appendLine("- Adapt difficulty based on the learner's progress")
        }

        // Health & Wellness Advisor prompt
        val healthPrompt = buildString {
            appendLine("You are a health and wellness advisor who provides evidence-based guidance on fitness, nutrition, and well-being.")
            appendLine()
            appendLine("## LANGUAGE RULE")
            appendLine(langRule)
            appendLine()
            appendLine("## CRITICAL DISCLAIMER")
            appendLine("You are NOT a doctor. Always remind users to consult a healthcare professional for medical decisions, diagnoses, or treatment plans. Never prescribe medication or diagnose conditions.")
            appendLine()
            appendLine("## Areas of Guidance")
            appendLine("- Nutrition: meal planning, macros, dietary goals, healthy recipes")
            appendLine("- Fitness: workout routines, exercise form, training plans, recovery")
            appendLine("- Mental wellness: stress management, sleep hygiene, mindfulness, habits")
            appendLine("- General health: hydration, posture, ergonomics, preventive care awareness")
            appendLine()
            appendLine("## Your Approach")
            appendLine("- Ask about goals, current habits, and any restrictions before advising")
            appendLine("- Provide actionable, practical advice — not generic platitudes")
            appendLine("- Back recommendations with well-known health guidelines when possible")
            appendLine("- Suggest gradual, sustainable changes over extreme measures")
            appendLine("- Motivate without being pushy — respect the user's autonomy")
            appendLine()
            appendLine("## PubMed - OBLIGATORIO")
            appendLine("SIEMPRE usa la herramienta pubmed_search para buscar estudios científicos relevantes ANTES de dar cualquier consejo o recomendación de salud.")
            appendLine("- Busca estudios recientes y revisiones sistemáticas relacionados con la consulta del usuario")
            appendLine("- Cita los estudios encontrados (título, autores, año, PMID) en tu respuesta")
            appendLine("- Si necesitas más detalle de un artículo específico, usa pubmed_fetch_article con el PMID")
            appendLine("- Basa tus recomendaciones en la evidencia científica encontrada")
            appendLine("- Si no encuentras estudios relevantes, menciónalo y aclara que tu consejo es basado en guías generales")
        }

        val insertSql = "INSERT INTO agents (name, role, systemPrompt, temperature, maxTokens, folderPath, enableTerminal, whenToUse, createdAt, updatedAt, sarcasmLevel, creativityLevel, formalityLevel, empathyLevel, technicalPrecision, useLocalRouting, enabledTools, isSystemAgent) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

        // 1. Cortex
        db.compileStatement(insertSql).apply {
            bindString(1, "Cortex")
            bindString(2, "Agent Orchestrator")
            bindString(3, cortexPrompt)
            bindDouble(4, 0.7)
            bindLong(5, 4096)
            bindString(6, "agents/cortex")
            bindLong(7, 1)  // enableTerminal
            bindString(8, "")
            bindLong(9, now)
            bindLong(10, now)
            bindLong(11, 0)   // sarcasmLevel
            bindLong(12, 50)  // creativityLevel
            bindLong(13, 50)  // formalityLevel
            bindLong(14, 50)  // empathyLevel
            bindLong(15, 70)  // technicalPrecision
            bindLong(16, 0)   // useLocalRouting
            bindString(17, "") // enabledTools
            bindLong(18, 0)   // isSystemAgent
            executeInsert()
            close()
        }

        // 2. Programmer
        db.compileStatement(insertSql).apply {
            bindString(1, "Programmer")
            bindString(2, "Software Developer")
            bindString(3, softwarePrompt)
            bindDouble(4, 0.2)
            bindLong(5, 8192)
            bindString(6, "agents/programmer")
            bindLong(7, 1)  // enableTerminal
            bindString(8, "programming,code,development,software,bug,debug,api,deploy,git")
            bindLong(9, now)
            bindLong(10, now)
            bindLong(11, 10)
            bindLong(12, 60)
            bindLong(13, 40)
            bindLong(14, 40)
            bindLong(15, 90)
            bindLong(16, 0)   // useLocalRouting
            bindString(17, "") // enabledTools
            bindLong(18, 0)   // isSystemAgent
            executeInsert()
            close()
        }

        // 3. Creative Writer
        db.compileStatement(insertSql).apply {
            bindString(1, "Writer")
            bindString(2, "Creative Writer & Content Specialist")
            bindString(3, writerPrompt)
            bindDouble(4, 0.8)
            bindLong(5, 8192)
            bindString(6, "agents/writer")
            bindLong(7, 0)  // no terminal
            bindString(8, "write,writing,essay,story,email,blog,script,poem,letter,content,copy,edit,proofread")
            bindLong(9, now)
            bindLong(10, now)
            bindLong(11, 15)
            bindLong(12, 85)
            bindLong(13, 50)
            bindLong(14, 60)
            bindLong(15, 50)
            bindLong(16, 0)   // useLocalRouting
            bindString(17, "") // enabledTools
            bindLong(18, 0)   // isSystemAgent
            executeInsert()
            close()
        }

        // 4. Researcher
        db.compileStatement(insertSql).apply {
            bindString(1, "Researcher")
            bindString(2, "Research Analyst")
            bindString(3, researcherPrompt)
            bindDouble(4, 0.4)
            bindLong(5, 8192)
            bindString(6, "agents/researcher")
            bindLong(7, 0)  // no terminal
            bindString(8, "research,search,investigate,compare,analyze,news,report,information,facts,sources")
            bindLong(9, now)
            bindLong(10, now)
            bindLong(11, 0)
            bindLong(12, 40)
            bindLong(13, 70)
            bindLong(14, 40)
            bindLong(15, 85)
            bindLong(16, 0)   // useLocalRouting
            bindString(17, "") // enabledTools
            bindLong(18, 0)   // isSystemAgent
            executeInsert()
            close()
        }

        // 5. Data Analyst
        db.compileStatement(insertSql).apply {
            bindString(1, "Data Analyst")
            bindString(2, "Data & Quantitative Analyst")
            bindString(3, dataAnalystPrompt)
            bindDouble(4, 0.2)
            bindLong(5, 8192)
            bindString(6, "agents/data_analyst")
            bindLong(7, 1)  // enableTerminal
            bindString(8, "data,statistics,csv,excel,chart,graph,calculate,numbers,analysis,sql,math,percentage")
            bindLong(9, now)
            bindLong(10, now)
            bindLong(11, 0)
            bindLong(12, 30)
            bindLong(13, 70)
            bindLong(14, 30)
            bindLong(15, 95)
            bindLong(16, 0)   // useLocalRouting
            bindString(17, "") // enabledTools
            bindLong(18, 0)   // isSystemAgent
            executeInsert()
            close()
        }

        // 6. Academic Tutor
        db.compileStatement(insertSql).apply {
            bindString(1, "Tutor")
            bindString(2, "Academic Tutor")
            bindString(3, tutorPrompt)
            bindDouble(4, 0.5)
            bindLong(5, 8192)
            bindString(6, "agents/tutor")
            bindLong(7, 0)  // no terminal
            bindString(8, "learn,study,explain,teach,homework,exam,test,tutor,science,math,history,language,practice")
            bindLong(9, now)
            bindLong(10, now)
            bindLong(11, 5)
            bindLong(12, 60)
            bindLong(13, 50)
            bindLong(14, 80)
            bindLong(15, 75)
            bindLong(16, 0)   // useLocalRouting
            bindString(17, "") // enabledTools
            bindLong(18, 0)   // isSystemAgent
            executeInsert()
            close()
        }

        // 7. Health & Wellness Advisor
        db.compileStatement(insertSql).apply {
            bindString(1, "Health Advisor")
            bindString(2, "Health & Wellness Advisor")
            bindString(3, healthPrompt)
            bindDouble(4, 0.4)
            bindLong(5, 8192)
            bindString(6, "agents/health")
            bindLong(7, 0)  // no terminal
            bindString(8, "health,fitness,exercise,nutrition,diet,wellness,sleep,stress,workout,meal,calories,weight")
            bindLong(9, now)
            bindLong(10, now)
            bindLong(11, 0)
            bindLong(12, 40)
            bindLong(13, 50)
            bindLong(14, 75)
            bindLong(15, 70)
            bindLong(16, 0)   // useLocalRouting
            bindString(17, "") // enabledTools
            bindLong(18, 0)   // isSystemAgent
            executeInsert()
            close()
        }

        // 8. Agent Architect (system agent — hidden from UI)
        val architectPrompt = buildString {
            appendLine("You are Agent Architect, a specialist in designing AI agents. You are a SYSTEM agent — the user does not interact with you directly. Cortex delegates to you when a user requests a new agent.")
            appendLine()
            appendLine("## YOUR TASK")
            appendLine("When you receive a request to create an agent, you MUST:")
            appendLine("1. Design a complete agent with name, role, system prompt, personality, and configuration")
            appendLine("2. Call the `create_agent` tool with ALL parameters filled in")
            appendLine("3. Confirm to the user what was created")
            appendLine()
            appendLine("## PROMPT DESIGN RULES")
            appendLine("- Start with: \"You are [role description] with [key expertise].\"")
            appendLine("- ALWAYS include this language rule: \"IMPORTANT: Always respond in the same language the user writes in.\"")
            appendLine("- Structure with ## headers: Capabilities, Your Approach, Guidelines")
            appendLine("- Be specific about what the agent CAN and CANNOT do")
            appendLine("- Add disclaimers for sensitive domains (legal, medical, financial)")
            appendLine("- Keep prompts between 150-300 words — concise but complete")
            appendLine()
            appendLine("## PERSONALITY CALIBRATION (0-100)")
            appendLine("- sarcasmLevel: 0 for professional agents, 10-20 for casual ones")
            appendLine("- creativityLevel: high (70-90) for creative roles, low (20-40) for analytical")
            appendLine("- formalityLevel: high (70-80) for professional, low (30-40) for casual")
            appendLine("- empathyLevel: high (70-90) for support/health, medium (40-60) for technical")
            appendLine("- technicalPrecision: high (80-95) for technical, medium (50-70) for general")
            appendLine()
            appendLine("## TEMPERATURE GUIDE")
            appendLine("- 0.1-0.3: Analytical, data, code, legal")
            appendLine("- 0.4-0.6: General purpose, tutoring, research")
            appendLine("- 0.7-0.9: Creative writing, brainstorming")
            appendLine()
            appendLine("## whenToUse KEYWORDS")
            appendLine("CSV of Spanish AND English keywords that describe when to delegate to this agent. Example: \"legal,abogado,lawyer,contract,contrato,law,ley\"")
            appendLine()
            appendLine("## IMPORTANT")
            appendLine("- NEVER create an agent that duplicates an existing one")
            appendLine("- Agent names should be short (1-2 words)")
            appendLine("- enableTerminal should be false unless the agent needs shell access")
            appendLine("- Always respond in the user's language when confirming creation")
        }
        db.compileStatement(insertSql).apply {
            bindString(1, "Agent Architect")
            bindString(2, "Agent Designer")
            bindString(3, architectPrompt)
            bindDouble(4, 0.4)
            bindLong(5, 4096)
            bindString(6, "agents/architect")
            bindLong(7, 0)
            bindString(8, "create agent,new agent,crear agente,nuevo agente,design agent,diseñar agente")
            bindLong(9, now)
            bindLong(10, now)
            bindLong(11, 0)   // sarcasm
            bindLong(12, 70)  // creativity
            bindLong(13, 60)  // formality
            bindLong(14, 50)  // empathy
            bindLong(15, 85)  // precision
            bindLong(16, 0)   // useLocalRouting
            bindString(17, "") // enabledTools
            bindLong(18, 1)   // isSystemAgent = true
            executeInsert()
            close()
        }
    }

    @Provides
    fun provideAgentDao(database: AppDatabase): AgentDao = database.agentDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideFileDao(database: AppDatabase): FileDao = database.fileDao()

    @Provides
    fun provideWorkspaceDao(database: AppDatabase): WorkspaceDao = database.workspaceDao()

    @Provides
    fun provideCommandPermissionDao(database: AppDatabase): CommandPermissionDao = database.commandPermissionDao()

    @Provides
    fun provideSTTSettingsDao(database: AppDatabase): STTSettingsDao = database.sttSettingsDao()

    @Provides
    fun provideMCPDao(database: AppDatabase): MCPDao = database.mcpDao()

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMemoryDao(database: AppDatabase): MemoryDao = database.memoryDao()

    @Provides
    fun provideCustomLocalModelDao(database: AppDatabase): CustomLocalModelDao = database.customLocalModelDao()

    @Provides
    fun provideFinanceDao(database: AppDatabase): FinanceDao = database.financeDao()

    @Provides
    fun provideTodoDao(database: AppDatabase): TodoDao = database.todoDao()

    @Provides
    fun provideScheduledTaskDao(database: AppDatabase): ScheduledTaskDao = database.scheduledTaskDao()
}
