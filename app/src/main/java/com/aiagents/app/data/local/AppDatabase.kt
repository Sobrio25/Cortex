package com.aiagents.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aiagents.app.data.model.AgentEntity
import com.aiagents.app.data.model.CommandPermissionEntity
import com.aiagents.app.data.model.FileEntity
import com.aiagents.app.data.model.MemoryEntity
import com.aiagents.app.data.model.MemoryFtsEntity
import com.aiagents.app.data.model.MemoryLinkEntity
import com.aiagents.app.data.model.MessageEntity
import com.aiagents.app.data.model.ConversationEntity
import com.aiagents.app.data.model.CustomLocalModelEntity
import com.aiagents.app.data.model.DownloadProgressEntity
import com.aiagents.app.data.model.ScheduledTaskEntity
import com.aiagents.app.data.model.SkillEntity
import com.aiagents.app.data.model.SkillReviewEntity
import com.aiagents.app.data.model.SubagentExecutionEntity
import com.aiagents.app.data.model.TodoEntity
import com.aiagents.app.data.model.MCPServerEntity
import com.aiagents.app.data.model.STTSettingsEntity
import com.aiagents.app.data.model.WorkspaceEntity
import com.aiagents.app.data.orchestration.AgentOrchestrator
import com.aiagents.app.domain.model.SkillCreatorBuiltin
import com.aiagents.app.domain.model.AndroidAppControlBuiltin
import com.aiagents.app.domain.model.WeatherWidgetsBuiltin

@Database(
    entities = [
        AgentEntity::class,
        MessageEntity::class,
        FileEntity::class,
        WorkspaceEntity::class,
        CommandPermissionEntity::class,
        STTSettingsEntity::class,
        MCPServerEntity::class,
        ConversationEntity::class,
        MemoryEntity::class,
        MemoryFtsEntity::class,
        MemoryLinkEntity::class,
        CustomLocalModelEntity::class,
        TodoEntity::class,
        ScheduledTaskEntity::class,
        DownloadProgressEntity::class,
        SkillEntity::class,
        SkillReviewEntity::class,
        SubagentExecutionEntity::class
    ],
    version = 46,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun agentDao(): AgentDao
    abstract fun messageDao(): MessageDao
    abstract fun fileDao(): FileDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun commandPermissionDao(): CommandPermissionDao
    abstract fun sttSettingsDao(): STTSettingsDao
    abstract fun mcpDao(): MCPDao
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao
    abstract fun customLocalModelDao(): CustomLocalModelDao
    abstract fun todoDao(): TodoDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun downloadProgressDao(): DownloadProgressDao
    abstract fun skillDao(): SkillDao
    abstract fun skillReviewDao(): SkillReviewDao
    abstract fun subagentExecutionDao(): SubagentExecutionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS workspaces (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        activeAgentId INTEGER,
                        systemPrompt TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(activeAgentId) REFERENCES agents(id) ON DELETE SET NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workspaces_activeAgentId ON workspaces(activeAgentId)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS messages_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workspaceId INTEGER NOT NULL,
                        agentId INTEGER,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        attachedFiles TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(workspaceId) REFERENCES workspaces(id) ON DELETE CASCADE,
                        FOREIGN KEY(agentId) REFERENCES agents(id) ON DELETE SET NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_new_workspaceId ON messages_new(workspaceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_new_agentId ON messages_new(agentId)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS files_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workspaceId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        path TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        size INTEGER NOT NULL,
                        generatedByAI INTEGER NOT NULL DEFAULT 0,
                        uploadedAt INTEGER NOT NULL,
                        FOREIGN KEY(workspaceId) REFERENCES workspaces(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_files_new_workspaceId ON files_new(workspaceId)")

                db.execSQL("DROP TABLE messages")
                db.execSQL("DROP TABLE files")
                db.execSQL("ALTER TABLE messages_new RENAME TO messages")
                db.execSQL("ALTER TABLE files_new RENAME TO files")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workspaces ADD COLUMN selectedModel TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agents DROP COLUMN providerType")
                db.execSQL("ALTER TABLE agents DROP COLUMN modelName")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS command_permissions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        commandPattern TEXT NOT NULL,
                        permissionLevel TEXT NOT NULL,
                        grantedAt INTEGER NOT NULL,
                        lastUsedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("ALTER TABLE agents ADD COLUMN enableTerminal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolCallsJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolResultsJson TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agents ADD COLUMN whenToUse TEXT NOT NULL DEFAULT ''")

                val now = System.currentTimeMillis()
                val simplePrompt = "Eres Cortex, el orquestador central de agentes AI. Tu rol es analizar las peticiones del usuario y determinar que agente especializado debe manejar cada tarea. Usa DELEGAR: [nombre del agente] para delegar."

                db.compileStatement(
                    "INSERT INTO agents (name, role, systemPrompt, temperature, maxTokens, folderPath, enableTerminal, whenToUse, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                ).apply {
                    bindString(1, "Cortex")
                    bindString(2, "Orquestador de Agentes")
                    bindString(3, simplePrompt)
                    bindDouble(4, 0.7)
                    bindLong(5, 4096)
                    bindString(6, "agents/")
                    bindLong(7, 1)
                    bindString(8, "")
                    bindLong(9, now)
                    bindLong(10, now)
                    executeInsert()
                    close()
                }
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Simplified migration - just update the role, skip large prompt updates
                db.compileStatement("UPDATE agents SET role = ?, updatedAt = ? WHERE name = ?").apply {
                    bindString(1, "Orquestador de Agentes")
                    bindLong(2, System.currentTimeMillis())
                    bindString(3, "Cortex")
                    executeUpdateDelete()
                    close()
                }
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()

                val financialPrompt = "Eres un asesor financiero experto en finanzas personales, inversiones y planificacion financiera. Ayudas con presupuestos, estrategias de inversion y educacion financiera."
                val softwarePrompt = "Eres un programador senior con experiencia en multiples lenguajes y frameworks. Ayudas con codigo limpio, debugging, arquitectura y mejores practicas."

                db.compileStatement(
                    "INSERT INTO agents (name, role, systemPrompt, temperature, maxTokens, folderPath, enableTerminal, whenToUse, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                ).apply {
                    bindString(1, "Asesor Financiero")
                    bindString(2, "Asesor Financiero")
                    bindString(3, financialPrompt)
                    bindDouble(4, 0.3)
                    bindLong(5, 8192)
                    bindString(6, "agents/financiero")
                    bindLong(7, 0)
                    bindString(8, "finanzas,inversiones,presupuesto")
                    bindLong(9, now)
                    bindLong(10, now)
                    executeInsert()
                    close()
                }

                db.compileStatement(
                    "INSERT INTO agents (name, role, systemPrompt, temperature, maxTokens, folderPath, enableTerminal, whenToUse, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                ).apply {
                    bindString(1, "Programador")
                    bindString(2, "Programador de Software")
                    bindString(3, softwarePrompt)
                    bindDouble(4, 0.2)
                    bindLong(5, 8192)
                    bindString(6, "agents/programador")
                    bindLong(7, 1)
                    bindString(8, "programacion,codigo,desarrollo")
                    bindLong(9, now)
                    bindLong(10, now)
                    executeInsert()
                    close()
                }
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No-op migration - prompts are handled in onCreate callback
                // This avoids SQL injection issues with large prompt strings
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoning TEXT")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migration to fix initialization issues
                // Re-insert default agents with simplified prompts if they don't exist
                val now = System.currentTimeMillis()
                
                // Check if Cortex exists, if not insert it
                val cursor = db.query("SELECT COUNT(*) FROM agents WHERE name = ?", arrayOf("Cortex"))
                var cortexExists = false
                if (cursor.moveToFirst()) {
                    cortexExists = cursor.getInt(0) > 0
                }
                cursor.close()
                
                if (!cortexExists) {
                    db.compileStatement(
                        "INSERT INTO agents (name, role, systemPrompt, temperature, maxTokens, folderPath, enableTerminal, whenToUse, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    ).apply {
                        bindString(1, "Cortex")
                        bindString(2, "Orquestador de Agentes")
                        bindString(3, "Eres Cortex, el orquestador central de agentes AI.")
                        bindDouble(4, 0.7)
                        bindLong(5, 4096)
                        bindString(6, "agents/cortex")
                        bindLong(7, 1)
                        bindString(8, "")
                        bindLong(9, now)
                        bindLong(10, now)
                        executeInsert()
                        close()
                    }
                }
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Agregar campos de personalización de personalidad
                db.execSQL("ALTER TABLE agents ADD COLUMN sarcasmLevel INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE agents ADD COLUMN creativityLevel INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE agents ADD COLUMN formalityLevel INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE agents ADD COLUMN empathyLevel INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE agents ADD COLUMN technicalPrecision INTEGER NOT NULL DEFAULT 70")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Actualizar el prompt de Cortex con instrucciones claras y estrictas de delegacion
                val newCortexPrompt = "Eres Cortex, el sistema central de orquestacion de agentes AI. Coordinas agentes especializados para ejecutar tareas complejas.\n\n" +
                    "## PROTOCOLO DE DELEGACION - REGLAS CRITICAS\n\n" +
                    "Cuando la tarea requiere un agente especializado, tu respuesta DEBE incluir exactamente esta linea:\n\n" +
                    "DELEGAR: [Nombre del Agente]\n\n" +
                    "Para tareas en secuencia (la salida de uno alimenta al siguiente):\n" +
                    "DELEGAR_SECUENCIAL: [Agente1] -> [Agente2]\n\n" +
                    "Para tareas en paralelo (todos trabajan sobre la misma solicitud):\n" +
                    "DELEGAR_PARALELO: [Agente1], [Agente2]\n\n" +
                    "IMPORTANTE:\n" +
                    "- Usa SIEMPRE los corchetes: DELEGAR: [Nombre] -- no DELEGAR: Nombre\n" +
                    "- Puedes escribir texto explicativo ANTES de la linea DELEGAR\n" +
                    "- NO escribas frases como 'voy a delegarlo' sin incluir la linea DELEGAR\n" +
                    "- Si ningun agente especializado es adecuado, responde tu directamente sin usar DELEGAR\n\n" +
                    "{agents_list}"
                db.compileStatement("UPDATE agents SET systemPrompt = ?, updatedAt = ? WHERE name = ?").apply {
                    bindString(1, newCortexPrompt)
                    bindLong(2, System.currentTimeMillis())
                    bindString(3, "Cortex")
                    executeUpdateDelete()
                    close()
                }
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migración defectuosa - reparada en MIGRATION_14_15
                // Los DEFAULT values no deben estar aquí porque Room los maneja a nivel de aplicación
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stt_settings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workspaceId INTEGER NOT NULL,
                        mode TEXT NOT NULL,
                        localModelType TEXT NOT NULL,
                        cloudProvider TEXT NOT NULL,
                        apiKey TEXT NOT NULL,
                        language TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recrear la tabla stt_settings sin DEFAULT values en SQL
                db.execSQL("DROP TABLE IF EXISTS stt_settings")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stt_settings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workspaceId INTEGER NOT NULL,
                        mode TEXT NOT NULL,
                        localModelType TEXT NOT NULL,
                        cloudProvider TEXT NOT NULL,
                        apiKey TEXT NOT NULL,
                        language TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Crear tabla de servidores MCP
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS mcp_servers (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL DEFAULT 0,
                        configJson TEXT NOT NULL DEFAULT '{}',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)

                // Insertar Brave Search como servidor por defecto
                val now = System.currentTimeMillis()
                db.execSQL("""
                    INSERT INTO mcp_servers (id, name, description, isEnabled, configJson, createdAt, updatedAt)
                    VALUES ('brave_search', 'Brave Search', 'Búsqueda web privada y gratuita', 0, '{}', $now, $now)
                """)
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Agregar campo enabled (STT deshabilitado por defecto)
                db.execSQL("ALTER TABLE stt_settings ADD COLUMN enabled INTEGER NOT NULL DEFAULT 0")
                // Agregar campo localEngine para seleccion de motor local
                db.execSQL("ALTER TABLE stt_settings ADD COLUMN localEngine TEXT NOT NULL DEFAULT 'AUTO'")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Detectar qué columnas existen actualmente para manejar cualquier estado previo
                val cursor = db.query("PRAGMA table_info(stt_settings)", emptyArray())
                val columns = mutableSetOf<String>()
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0) columns.add(cursor.getString(nameIdx))
                }
                cursor.close()

                db.execSQL("ALTER TABLE stt_settings RENAME TO stt_settings_old")

                // Crear tabla con el schema exacto que Room espera (sin SQL defaults ni índice)
                db.execSQL("""
                    CREATE TABLE stt_settings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workspaceId INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        mode TEXT NOT NULL,
                        localModelType TEXT NOT NULL,
                        localEngine TEXT NOT NULL,
                        cloudProvider TEXT NOT NULL,
                        apiKey TEXT NOT NULL,
                        language TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)

                val enabledSql = if ("enabled" in columns) "COALESCE(enabled, 0)" else "0"
                val localEngineSql = if ("localEngine" in columns) "COALESCE(localEngine, 'AUTO')" else "'AUTO'"

                db.execSQL("""
                    INSERT INTO stt_settings
                        (id, workspaceId, enabled, mode, localModelType, localEngine, cloudProvider, apiKey, language, createdAt, updatedAt)
                    SELECT
                        id,
                        workspaceId,
                        $enabledSql,
                        COALESCE(mode, 'LOCAL'),
                        COALESCE(localModelType, 'AUTO'),
                        $localEngineSql,
                        COALESCE(cloudProvider, 'ANDROID_SPEECH_RECOGNIZER'),
                        COALESCE(apiKey, ''),
                        COALESCE(language, 'es'),
                        COALESCE(createdAt, 0),
                        COALESCE(updatedAt, 0)
                    FROM stt_settings_old
                """)

                db.execSQL("DROP TABLE stt_settings_old")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agents ADD COLUMN useLocalRouting INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agents ADD COLUMN enabledTools TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                val langRule = "IMPORTANT: Always respond in the same language the user writes in. Detect the user's language from their message and match it exactly."

                data class DefaultAgent(
                    val name: String, val role: String, val prompt: String,
                    val temp: Double, val maxTokens: Long, val folder: String,
                    val terminal: Long, val whenToUse: String,
                    val sarcasm: Long, val creativity: Long, val formality: Long,
                    val empathy: Long, val precision: Long
                )

                val agents = listOf(
                    DefaultAgent(
                        "Cortex", "Agent Orchestrator",
                        "You are Cortex, the central AI agent orchestration system. You coordinate specialized agents to execute complex tasks.\n\n## LANGUAGE RULE\n$langRule\n\n## TIME AWARENESS - IMPORTANT\nYou must ALWAYS be aware of the current date and time.\nThe current date is: {CURRENT_DATE}\n\nWhen the user asks about:\n- Current events, news, or date-dependent information\n- Tasks tied to specific dates (meetings, reminders, deadlines)\n- Temporal context (what day/month it is)\nUse the current date as your reference.\n\n## DELEGATION PROTOCOL - CRITICAL RULES\n\nWhen a task requires a specialized agent, your response MUST include exactly this line:\n\nDELEGAR: [Agent Name]\n\nFor sequential tasks (output of one feeds the next):\nDELEGAR_SECUENCIAL: [Agent1] -> [Agent2]\n\nFor parallel tasks (all work on the same request):\nDELEGAR_PARALELO: [Agent1], [Agent2]\n\nIMPORTANT:\n- ALWAYS use brackets: DELEGAR: [Name] -- not DELEGAR: Name\n- You may write explanatory text BEFORE the DELEGAR line\n- NEVER write phrases like 'I will delegate it' without including the DELEGAR line\n- If no specialized agent is suitable, respond directly without using DELEGAR\n\n{agents_list}\n",
                        0.7, 4096, "agents/cortex", 1, "",
                        0, 50, 50, 50, 70
                    ),
                    DefaultAgent(
                        "Programmer", "Software Developer",
                        "You are a senior software developer with over 10 years of experience building production-grade applications.\n\n## LANGUAGE RULE\n$langRule\n\n## Technical Expertise\n- Languages: Python, JavaScript, TypeScript, Kotlin, Java, Go, Rust, C++\n- Frontend: React, Vue.js, Angular, Jetpack Compose, SwiftUI\n- Backend: Node.js, FastAPI, Django, Spring Boot, Express\n- Databases: PostgreSQL, MySQL, MongoDB, SQLite, Redis\n- Mobile: Android (Kotlin), iOS (Swift), Flutter, React Native\n- DevOps: Docker, CI/CD, Git, Linux, cloud platforms\n\n## How You Work\n- Write clean, efficient, well-structured code\n- Debug systematically: reproduce, isolate, fix, verify\n- Explain technical concepts clearly at any level\n- Suggest best practices, design patterns, and architectural improvements\n- When given terminal access, use it to explore files, run scripts, and test solutions\n",
                        0.2, 8192, "agents/programmer", 1,
                        "programming,code,development,software,bug,debug,api,deploy,git",
                        10, 60, 40, 40, 90
                    ),
                    DefaultAgent(
                        "Writer", "Creative Writer & Content Specialist",
                        "You are a versatile creative writer and content specialist with expertise across multiple formats and styles.\n\n## LANGUAGE RULE\n$langRule\n\n## Capabilities\n- Creative writing: stories, poetry, scripts, dialogues, worldbuilding\n- Professional content: emails, reports, presentations, proposals, cover letters\n- Marketing copy: social media posts, ad copy, product descriptions, taglines\n- Editing: proofreading, tone adjustment, rewriting, summarization\n- Academic: essays, research summaries, thesis outlines\n\n## Your Approach\n- Adapt tone and style to the audience and purpose\n- Ask clarifying questions about target audience, tone, and length when needed\n- Provide multiple options or variations when appropriate\n- Respect the user's voice — enhance, don't replace it\n",
                        0.8, 8192, "agents/writer", 0,
                        "write,writing,essay,story,email,blog,script,poem,letter,content,copy,edit,proofread",
                        15, 85, 50, 60, 50
                    ),
                    DefaultAgent(
                        "Researcher", "Research Analyst",
                        "You are a meticulous research analyst skilled at finding, synthesizing, and presenting information clearly and accurately.\n\n## LANGUAGE RULE\n$langRule\n\n## Capabilities\n- Deep research on any topic: technology, science, history, culture, business\n- Comparative analysis: pros/cons, feature comparisons, benchmarks\n- Fact-checking and source evaluation\n- Trend analysis and market research\n- Summarizing complex documents, papers, and reports\n\n## Your Approach\n- Present findings in a structured, easy-to-scan format\n- Distinguish between facts, estimates, and opinions\n- Cite sources and note when information may be outdated\n- When using search tools, craft precise queries and cross-reference multiple results\n- Flag gaps in available information honestly\n",
                        0.4, 8192, "agents/researcher", 0,
                        "research,search,investigate,compare,analyze,news,report,information,facts,sources",
                        0, 40, 70, 40, 85
                    ),
                    DefaultAgent(
                        "Data Analyst", "Data & Quantitative Analyst",
                        "You are a data analyst and quantitative specialist who transforms raw data into actionable insights.\n\n## LANGUAGE RULE\n$langRule\n\n## Capabilities\n- Data analysis: statistics, trends, correlations, anomaly detection\n- Data formats: CSV, JSON, Excel, SQL databases, APIs\n- Calculations: financial math, unit conversions, percentages, projections\n- Visualization guidance: chart type selection, data storytelling\n- Script generation: Python (pandas, matplotlib), SQL queries, spreadsheet formulas\n\n## Your Approach\n- Ask about the data source and desired outcome before diving in\n- Show your work: formulas, steps, and methodology\n- Present results with clear labels, units, and context\n- Warn about limitations: sample size, data quality, correlation vs causation\n- When given terminal access, use it to process data files and run analysis scripts\n",
                        0.2, 8192, "agents/data_analyst", 1,
                        "data,statistics,csv,excel,chart,graph,calculate,numbers,analysis,sql,math,percentage",
                        0, 30, 70, 30, 95
                    ),
                    DefaultAgent(
                        "Tutor", "Academic Tutor",
                        "You are a patient, adaptive academic tutor who helps learners understand and master any subject.\n\n## LANGUAGE RULE\n$langRule\n\n## Subjects\n- Sciences: math, physics, chemistry, biology, computer science\n- Humanities: history, philosophy, literature, languages\n- Professional: business, economics, law, engineering\n- Test prep: standardized tests, certifications, exams\n\n## Your Teaching Method\n- Assess the learner's current level before explaining\n- Break complex topics into digestible steps\n- Use analogies, examples, and real-world connections\n- Ask guiding questions instead of giving direct answers when it aids learning\n- Provide practice problems and verify understanding\n- Adapt difficulty based on the learner's progress\n",
                        0.5, 8192, "agents/tutor", 0,
                        "learn,study,explain,teach,homework,exam,test,tutor,science,math,history,language,practice",
                        5, 60, 50, 80, 75
                    ),
                    DefaultAgent(
                        "Health Advisor", "Health & Wellness Advisor",
                        "You are a health and wellness advisor who provides evidence-based guidance on fitness, nutrition, and well-being.\n\n## LANGUAGE RULE\n$langRule\n\n## CRITICAL DISCLAIMER\nYou are NOT a doctor. Always remind users to consult a healthcare professional for medical decisions, diagnoses, or treatment plans. Never prescribe medication or diagnose conditions.\n\n## Areas of Guidance\n- Nutrition: meal planning, macros, dietary goals, healthy recipes\n- Fitness: workout routines, exercise form, training plans, recovery\n- Mental wellness: stress management, sleep hygiene, mindfulness, habits\n- General health: hydration, posture, ergonomics, preventive care awareness\n\n## Your Approach\n- Ask about goals, current habits, and any restrictions before advising\n- Provide actionable, practical advice — not generic platitudes\n- Back recommendations with well-known health guidelines when possible\n- Suggest gradual, sustainable changes over extreme measures\n- Motivate without being pushy — respect the user's autonomy\n",
                        0.4, 8192, "agents/health", 0,
                        "health,fitness,exercise,nutrition,diet,wellness,sleep,stress,workout,meal,calories,weight",
                        0, 40, 50, 75, 70
                    )
                )

                val insertSql = """INSERT INTO agents (name, role, systemPrompt, temperature, maxTokens, folderPath, enableTerminal, whenToUse, createdAt, updatedAt, sarcasmLevel, creativityLevel, formalityLevel, empathyLevel, technicalPrecision)
                    SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                    WHERE NOT EXISTS (SELECT 1 FROM agents WHERE name = ?)"""

                for (a in agents) {
                    db.compileStatement(insertSql).apply {
                        bindString(1, a.name)
                        bindString(2, a.role)
                        bindString(3, a.prompt)
                        bindDouble(4, a.temp)
                        bindLong(5, a.maxTokens)
                        bindString(6, a.folder)
                        bindLong(7, a.terminal)
                        bindString(8, a.whenToUse)
                        bindLong(9, now)
                        bindLong(10, now)
                        bindLong(11, a.sarcasm)
                        bindLong(12, a.creativity)
                        bindLong(13, a.formality)
                        bindLong(14, a.empathy)
                        bindLong(15, a.precision)
                        bindString(16, a.name) // for WHERE NOT EXISTS
                        executeInsert()
                        close()
                    }
                }
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workspaces ADD COLUMN externalStorageUri TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cortex_memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL,
                        category TEXT NOT NULL,
                        subcategory TEXT NOT NULL DEFAULT '',
                        importance INTEGER NOT NULL DEFAULT 5,
                        confidence REAL NOT NULL DEFAULT 1.0,
                        source TEXT NOT NULL DEFAULT '',
                        accessCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastAccessedAt INTEGER NOT NULL,
                        expiresAt INTEGER DEFAULT NULL
                    )
                """)
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS cortex_memory_fts USING fts4(content, content=`cortex_memories`)")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add code execution tools awareness to Programmer agent prompt
                val codeToolsSection = """

## Code Execution Tools
You have two powerful tools for running code and showing results to the user:
- **run_code**: Write and execute code in one step (Python, JavaScript/Node.js, Bash). Use it to run scripts, test logic, analyze data, etc. The output is shown directly in the chat.
- **preview_web**: Render HTML/CSS/JS in a visual WebView. Use it for UI previews, interactive demos, charts (Chart.js, D3), React components (via CDN), visualizations, etc. The HTML must be self-contained (inline styles/scripts or CDN links).
ALWAYS prefer run_code over execute_command for running scripts. Use preview_web whenever the user wants to SEE visual output."""
                db.execSQL(
                    "UPDATE agents SET systemPrompt = systemPrompt || ? WHERE name = 'Programmer'",
                    arrayOf(codeToolsSection)
                )
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Update Cortex prompt: English, compact, new delegation keywords
                val newPrompt = "You are Cortex, the central AI agent orchestration system. You coordinate specialized agents to execute complex tasks.\n\n" +
                    "## LANGUAGE RULE\nIMPORTANT: Always respond in the same language the user writes in. Detect the user's language from their message and match it exactly.\n\n" +
                    "## TIME AWARENESS\nCurrent date: {CURRENT_DATE}\n\n" +
                    "## DELEGATION PROTOCOL\n\n" +
                    "When a task needs a specialized agent, output exactly:\n\n" +
                    "DELEGATE: [Agent Name]\n\n" +
                    "Sequential (output chains): DELEGATE_SEQ: [Agent1] -> [Agent2]\n" +
                    "Parallel (same input): DELEGATE_PAR: [Agent1], [Agent2]\n\n" +
                    "Rules:\n" +
                    "- ALWAYS use brackets: DELEGATE: [Name]\n" +
                    "- You may write a brief message BEFORE the DELEGATE line\n" +
                    "- Match agent by TASK TYPE, not keywords\n" +
                    "- Greetings/general questions: respond directly, no delegation\n\n" +
                    "{agents_list}"
                db.compileStatement("UPDATE agents SET systemPrompt = ?, updatedAt = ? WHERE name = ?").apply {
                    bindString(1, newPrompt)
                    bindLong(2, System.currentTimeMillis())
                    bindString(3, "Cortex")
                    executeUpdateDelete()
                    close()
                }
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Update Health Advisor prompt to always use PubMed
                val pubmedSection = """

## PubMed - OBLIGATORIO
SIEMPRE usa la herramienta pubmed_search para buscar estudios científicos relevantes ANTES de dar cualquier consejo o recomendación de salud.
- Busca estudios recientes y revisiones sistemáticas relacionados con la consulta del usuario
- Cita los estudios encontrados (título, autores, año, PMID) en tu respuesta
- Si necesitas más detalle de un artículo específico, usa pubmed_fetch_article con el PMID
- Basa tus recomendaciones en la evidencia científica encontrada
- Si no encuentras estudios relevantes, menciónalo y aclara que tu consejo es basado en guías generales"""
                db.execSQL(
                    "UPDATE agents SET systemPrompt = systemPrompt || ? WHERE name = 'Health Advisor'",
                    arrayOf(pubmedSection)
                )
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate FTS table with backtick quoting that Room expects
                db.execSQL("DROP TABLE IF EXISTS cortex_memory_fts")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS cortex_memory_fts USING fts4(content, content=`cortex_memories`)")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cortex_memory_links (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceId INTEGER NOT NULL,
                        targetId INTEGER NOT NULL,
                        linkType TEXT NOT NULL,
                        strength REAL NOT NULL DEFAULT 1.0,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(sourceId) REFERENCES cortex_memories(id) ON DELETE CASCADE,
                        FOREIGN KEY(targetId) REFERENCES cortex_memories(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cortex_memory_links_sourceId ON cortex_memory_links(sourceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cortex_memory_links_targetId ON cortex_memory_links(targetId)")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_local_models (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        huggingFaceRepoId TEXT NOT NULL,
                        huggingFaceUrl TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        contextLength INTEGER NOT NULL DEFAULT 4096,
                        requiresLicense INTEGER NOT NULL DEFAULT 0,
                        requiresHFToken INTEGER NOT NULL DEFAULT 0,
                        addedAt INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agents ADD COLUMN isSystemAgent INTEGER NOT NULL DEFAULT 0")

                val now = System.currentTimeMillis()
                val prompt = "You are Agent Architect, a specialist in designing AI agents. You are a SYSTEM agent — the user does not interact with you directly. Cortex delegates to you when a user requests a new agent.\n\n" +
                    "## YOUR TASK\nWhen you receive a request to create an agent, you MUST:\n" +
                    "1. Design a complete agent with name, role, system prompt, personality, and configuration\n" +
                    "2. Call the `create_agent` tool with ALL parameters filled in\n" +
                    "3. Confirm to the user what was created\n\n" +
                    "## PROMPT DESIGN RULES\n" +
                    "- Start with: \"You are [role description] with [key expertise].\"\n" +
                    "- ALWAYS include this language rule: \"IMPORTANT: Always respond in the same language the user writes in.\"\n" +
                    "- Structure with ## headers: Capabilities, Your Approach, Guidelines\n" +
                    "- Be specific about what the agent CAN and CANNOT do\n" +
                    "- Add disclaimers for sensitive domains (legal, medical, financial)\n" +
                    "- Keep prompts between 150-300 words — concise but complete\n\n" +
                    "## PERSONALITY CALIBRATION (0-100)\n" +
                    "- sarcasmLevel: 0 for professional agents, 10-20 for casual ones\n" +
                    "- creativityLevel: high (70-90) for creative roles, low (20-40) for analytical\n" +
                    "- formalityLevel: high (70-80) for professional, low (30-40) for casual\n" +
                    "- empathyLevel: high (70-90) for support/health, medium (40-60) for technical\n" +
                    "- technicalPrecision: high (80-95) for technical, medium (50-70) for general\n\n" +
                    "## TEMPERATURE GUIDE\n" +
                    "- 0.1-0.3: Analytical, data, code, legal\n" +
                    "- 0.4-0.6: General purpose, tutoring, research\n" +
                    "- 0.7-0.9: Creative writing, brainstorming\n\n" +
                    "## whenToUse KEYWORDS\n" +
                    "CSV of Spanish AND English keywords that describe when to delegate to this agent. Example: \"legal,abogado,lawyer,contract,contrato,law,ley\"\n\n" +
                    "## IMPORTANT\n" +
                    "- NEVER create an agent that duplicates an existing one — check the name first\n" +
                    "- Agent names should be short (1-2 words)\n" +
                    "- enableTerminal should be false unless the agent needs shell access\n" +
                    "- Always respond in the user's language when confirming creation"

                db.compileStatement(
                    "INSERT INTO agents (name, role, systemPrompt, temperature, maxTokens, folderPath, enableTerminal, whenToUse, createdAt, updatedAt, sarcasmLevel, creativityLevel, formalityLevel, empathyLevel, technicalPrecision, useLocalRouting, enabledTools, isSystemAgent) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                ).apply {
                    bindString(1, "Agent Architect")
                    bindString(2, "Agent Designer")
                    bindString(3, prompt)
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
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add FTS sync triggers — without these, the FTS index is never updated
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cortex_memories_ai AFTER INSERT ON cortex_memories BEGIN
                        INSERT INTO cortex_memory_fts(rowid, content) VALUES(new.rowid, new.content);
                    END
                """)
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cortex_memories_bd BEFORE DELETE ON cortex_memories BEGIN
                        DELETE FROM cortex_memory_fts WHERE rowid = old.rowid;
                    END
                """)
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cortex_memories_bu BEFORE UPDATE ON cortex_memories BEGIN
                        DELETE FROM cortex_memory_fts WHERE rowid = old.rowid;
                    END
                """)
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cortex_memories_au AFTER UPDATE ON cortex_memories BEGIN
                        INSERT INTO cortex_memory_fts(rowid, content) VALUES(new.rowid, new.content);
                    END
                """)
                // Rebuild FTS index from all existing memories
                db.execSQL("INSERT INTO cortex_memory_fts(cortex_memory_fts) VALUES('rebuild')")

                // Clean up duplicate onboarding memories — keep only the most recent per key
                db.execSQL("""
                    DELETE FROM cortex_memories WHERE id NOT IN (
                        SELECT MAX(id) FROM cortex_memories
                        WHERE source = 'onboarding' AND subcategory = 'user_identity'
                        GROUP BY SUBSTR(content, 1, INSTR(content, ':') - 1)
                    ) AND source = 'onboarding' AND subcategory = 'user_identity'
                """)
            }
        }

        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Simplify Cortex base prompt — delegation rules are injected by AgentOrchestrator.buildPrompt()
                // This avoids duplicate/conflicting "Rules:" sections and reinforces exact agent name matching
                // Use role='Agent Orchestrator' to find Cortex regardless of user-renamed name
                val newPrompt = "You are Cortex, the central AI agent orchestration system. You coordinate specialized agents to execute complex tasks.\n\n" +
                    "## LANGUAGE RULE\nIMPORTANT: Always respond in the same language the user writes in. Detect the user's language from their message and match it exactly.\n\n" +
                    "## TIME AWARENESS\nCurrent date: {CURRENT_DATE}\n\n" +
                    "{agents_list}"
                db.compileStatement("UPDATE agents SET systemPrompt = ?, updatedAt = ? WHERE role = 'Agent Orchestrator'").apply {
                    bindString(1, newPrompt)
                    bindLong(2, System.currentTimeMillis())
                    executeUpdateDelete()
                    close()
                }
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS finance_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        amount REAL NOT NULL,
                        currency TEXT NOT NULL,
                        category TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        date INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add sub-conversation support columns to conversations table
                db.execSQL("ALTER TABLE conversations ADD COLUMN parentConversationId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE conversations ADD COLUMN delegationAgentName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE conversations ADD COLUMN delegationTask TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE conversations ADD COLUMN status TEXT NOT NULL DEFAULT 'active'")
                // Add sub-conversation link column to messages table
                db.execSQL("ALTER TABLE messages ADD COLUMN subConversationId INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN lastMemoryExtraction INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_lastMemoryExtraction ON conversations(lastMemoryExtraction)")
            }
        }

        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS todos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        conversationId INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'pending',
                        priority TEXT NOT NULL DEFAULT 'medium',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_conversationId ON todos(conversationId)")
            }
        }

        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS scheduled_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workspaceId INTEGER NOT NULL,
                        agentName TEXT,
                        prompt TEXT NOT NULL,
                        scheduleType TEXT NOT NULL,
                        scheduleValue TEXT NOT NULL,
                        label TEXT NOT NULL DEFAULT '',
                        enabled INTEGER NOT NULL DEFAULT 1,
                        lastRunAt INTEGER,
                        lastResult TEXT,
                        nextRunAt INTEGER NOT NULL,
                        runCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scheduled_tasks_nextRunAt ON scheduled_tasks(nextRunAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scheduled_tasks_enabled ON scheduled_tasks(enabled)")
            }
        }

        /** Restores the download state table represented by the already-exported v39 schema. */
        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS download_progress (
                        modelId TEXT NOT NULL PRIMARY KEY,
                        modelName TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        totalBytes INTEGER NOT NULL,
                        downloadedBytes INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        errorMessage TEXT,
                        workId TEXT,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS skills (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        slug TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        whenToUse TEXT NOT NULL,
                        instructions TEXT NOT NULL,
                        status TEXT NOT NULL,
                        origin TEXT NOT NULL,
                        isImmutable INTEGER NOT NULL,
                        version INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        activatedAt INTEGER,
                        archivedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_skills_slug ON skills(slug)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_skills_status ON skills(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_skills_origin ON skills(origin)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS skill_reviews (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        scopeHash TEXT NOT NULL,
                        messageCount INTEGER NOT NULL,
                        transcriptFingerprint TEXT NOT NULL,
                        redactedTranscript TEXT NOT NULL,
                        status TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        candidateSkillId INTEGER,
                        createdAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        FOREIGN KEY(candidateSkillId) REFERENCES skills(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_skill_reviews_status ON skill_reviews(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_skill_reviews_candidateSkillId ON skill_reviews(candidateSkillId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_skill_reviews_createdAt ON skill_reviews(createdAt)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_skill_reviews_transcriptFingerprint " +
                        "ON skill_reviews(transcriptFingerprint)"
                )
                ensureBuiltInSkills(db)
            }
        }

        val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS subagent_executions (
                        taskId TEXT NOT NULL PRIMARY KEY,
                        parentTaskId TEXT,
                        parentConversationId INTEGER NOT NULL,
                        subConversationId INTEGER,
                        workspaceId INTEGER NOT NULL,
                        agentId INTEGER NOT NULL,
                        agentName TEXT NOT NULL,
                        goal TEXT NOT NULL,
                        acceptanceCriteria TEXT NOT NULL,
                        status TEXT NOT NULL,
                        role TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        failurePolicy TEXT NOT NULL,
                        depth INTEGER NOT NULL,
                        modelKey TEXT NOT NULL,
                        allowedTools TEXT NOT NULL,
                        toolPermissions TEXT NOT NULL,
                        workspacePolicy TEXT NOT NULL,
                        maxIterations INTEGER NOT NULL,
                        timeoutMillis INTEGER,
                        maxResultChars INTEGER NOT NULL,
                        resultSummary TEXT NOT NULL,
                        filesModified TEXT NOT NULL,
                        testsRun TEXT NOT NULL,
                        exitReason TEXT,
                        errorCode TEXT,
                        errorMessage TEXT,
                        createdAt INTEGER NOT NULL,
                        startedAt INTEGER,
                        completedAt INTEGER,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_subagent_executions_parentConversationId " +
                        "ON subagent_executions(parentConversationId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_subagent_executions_parentTaskId " +
                        "ON subagent_executions(parentTaskId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_subagent_executions_workspaceId " +
                        "ON subagent_executions(workspaceId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_subagent_executions_status " +
                        "ON subagent_executions(status)"
                )
            }
        }

        /**
         * Repairs the external-content FTS4 triggers. The previous triggers used FTS5's special
         * 'delete' command, which makes every UPDATE or DELETE fail with "SQL logic error" on
         * FTS4 and can terminate callers that do not catch the database exception.
         */
        val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) {
                repairMemoryFtsTriggers(db)
            }
        }

        /**
         * Cortex now works directly and creates task-scoped workers on demand. Remove only
         * untouched generated agents; user-edited and user-created agents remain intact.
         */
        val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val generatedDefaults = """
                    (name = 'Programmer' AND folderPath = 'agents/programmer') OR
                    (name = 'Writer' AND folderPath = 'agents/writer') OR
                    (name = 'Researcher' AND folderPath = 'agents/researcher') OR
                    (name = 'Data Analyst' AND folderPath = 'agents/data_analyst') OR
                    (name = 'Tutor' AND folderPath = 'agents/tutor') OR
                    (name = 'Health Advisor' AND folderPath = 'agents/health')
                """.trimIndent()
                val removableAgents = "createdAt = updatedAt AND ($generatedDefaults)"

                db.execSQL(
                    """
                    UPDATE workspaces
                    SET activeAgentId = (
                        SELECT id FROM agents WHERE role = 'Agent Orchestrator' LIMIT 1
                    )
                    WHERE activeAgentId IN (SELECT id FROM agents WHERE $removableAgents)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE scheduled_tasks
                    SET agentName = COALESCE(
                        (SELECT name FROM agents WHERE role = 'Agent Orchestrator' LIMIT 1),
                        'Cortex'
                    )
                    WHERE agentName IN (SELECT name FROM agents WHERE $removableAgents)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE scheduled_tasks
                    SET agentName = COALESCE(
                        (SELECT name FROM agents WHERE role = 'Agent Orchestrator' LIMIT 1),
                        'Cortex'
                    )
                    WHERE agentName = 'Agent Architect'
                    """.trimIndent()
                )
                db.execSQL("DELETE FROM agents WHERE $removableAgents")
                db.execSQL(
                    "DELETE FROM agents WHERE name = 'Agent Architect' " +
                        "AND folderPath = 'agents/architect' AND isSystemAgent = 1"
                )

                db.execSQL(
                    """
                    UPDATE agents
                    SET systemPrompt = ?
                    WHERE role = 'Agent Orchestrator'
                      AND createdAt = updatedAt
                      AND (
                          systemPrompt LIKE 'Coordinate specialized agents to execute complex tasks,%'
                          OR systemPrompt LIKE '%{agents_list}%'
                          OR systemPrompt LIKE '%## DELEGATION PROTOCOL%'
                      )
                    """.trimIndent(),
                    arrayOf(
                        "Complete the user's request directly and use available tools when they improve accuracy."
                    )
                )
            }
        }

        val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN conversationId INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_scheduled_tasks_conversationId " +
                        "ON scheduled_tasks(conversationId)"
                )
            }
        }

        val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // finance_transactions is intentionally left in place during
                // schema migration. DatabaseModule copies it into the isolated
                // FinanceDatabase on open and only then removes the legacy table.
            }
        }

        /** Removes generated legacy identity text; the configured name is injected at runtime. */
        val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE agents
                    SET systemPrompt = ?
                    WHERE role = 'Agent Orchestrator'
                      AND (
                          systemPrompt LIKE 'You are Cortex,%'
                          OR systemPrompt LIKE 'Eres Cortex,%'
                      )
                    """.trimIndent(),
                    arrayOf(AgentOrchestrator.DEFAULT_ORCHESTRATOR_PROMPT)
                )
            }
        }

        fun repairMemoryFtsTriggers(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TRIGGER IF EXISTS cortex_memories_ai")
            db.execSQL("DROP TRIGGER IF EXISTS cortex_memories_bd")
            db.execSQL("DROP TRIGGER IF EXISTS cortex_memories_bu")
            db.execSQL("DROP TRIGGER IF EXISTS cortex_memories_au")
            db.execSQL(
                """
                CREATE TRIGGER cortex_memories_ai AFTER INSERT ON cortex_memories BEGIN
                    INSERT INTO cortex_memory_fts(rowid, content) VALUES(new.rowid, new.content);
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER cortex_memories_bd BEFORE DELETE ON cortex_memories BEGIN
                    DELETE FROM cortex_memory_fts WHERE rowid = old.rowid;
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER cortex_memories_bu BEFORE UPDATE ON cortex_memories BEGIN
                    DELETE FROM cortex_memory_fts WHERE rowid = old.rowid;
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER cortex_memories_au AFTER UPDATE ON cortex_memories BEGIN
                    INSERT INTO cortex_memory_fts(rowid, content) VALUES(new.rowid, new.content);
                END
                """.trimIndent()
            )
            db.execSQL("INSERT INTO cortex_memory_fts(cortex_memory_fts) VALUES('rebuild')")
        }

        fun ensureBuiltInSkills(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            db.compileStatement(
                """
                INSERT OR IGNORE INTO skills (
                    slug, name, description, whenToUse, instructions, status, origin,
                    isImmutable, version, createdAt, updatedAt, activatedAt, archivedAt
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'BUILTIN', 1, ?, ?, ?, ?, NULL)
                """.trimIndent()
            ).apply {
                bindString(1, SkillCreatorBuiltin.SLUG)
                bindString(2, SkillCreatorBuiltin.NAME)
                bindString(3, SkillCreatorBuiltin.DESCRIPTION)
                bindString(4, SkillCreatorBuiltin.WHEN_TO_USE)
                bindString(5, SkillCreatorBuiltin.instructions)
                bindLong(6, SkillCreatorBuiltin.VERSION.toLong())
                bindLong(7, now)
                bindLong(8, now)
                bindLong(9, now)
                executeInsert()
                close()
            }

            db.compileStatement(
                """
                INSERT OR IGNORE INTO skills (
                    slug, name, description, whenToUse, instructions, status, origin,
                    isImmutable, version, createdAt, updatedAt, activatedAt, archivedAt
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'BUILTIN', 1, ?, ?, ?, ?, NULL)
                """.trimIndent()
            ).apply {
                bindString(1, WeatherWidgetsBuiltin.SLUG)
                bindString(2, WeatherWidgetsBuiltin.NAME)
                bindString(3, WeatherWidgetsBuiltin.DESCRIPTION)
                bindString(4, WeatherWidgetsBuiltin.WHEN_TO_USE)
                bindString(5, WeatherWidgetsBuiltin.instructions)
                bindLong(6, WeatherWidgetsBuiltin.VERSION.toLong())
                bindLong(7, now)
                bindLong(8, now)
                bindLong(9, now)
                executeInsert()
                close()
            }

            db.compileStatement(
                """
                INSERT OR IGNORE INTO skills (
                    slug, name, description, whenToUse, instructions, status, origin,
                    isImmutable, version, createdAt, updatedAt, activatedAt, archivedAt
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'BUILTIN', 1, ?, ?, ?, ?, NULL)
                """.trimIndent()
            ).apply {
                bindString(1, AndroidAppControlBuiltin.SLUG)
                bindString(2, AndroidAppControlBuiltin.NAME)
                bindString(3, AndroidAppControlBuiltin.DESCRIPTION)
                bindString(4, AndroidAppControlBuiltin.WHEN_TO_USE)
                bindString(5, AndroidAppControlBuiltin.instructions)
                bindLong(6, AndroidAppControlBuiltin.VERSION.toLong())
                bindLong(7, now)
                bindLong(8, now)
                bindLong(9, now)
                executeInsert()
                close()
            }

            db.compileStatement(
                """
                UPDATE skills SET
                    name = ?, description = ?, whenToUse = ?, instructions = ?,
                    status = 'ACTIVE', origin = 'BUILTIN', isImmutable = 1,
                    version = ?, updatedAt = ?, activatedAt = ?, archivedAt = NULL
                WHERE slug = ? AND (version != ? OR origin != 'BUILTIN' OR isImmutable = 0)
                """.trimIndent()
            ).apply {
                bindString(1, AndroidAppControlBuiltin.NAME)
                bindString(2, AndroidAppControlBuiltin.DESCRIPTION)
                bindString(3, AndroidAppControlBuiltin.WHEN_TO_USE)
                bindString(4, AndroidAppControlBuiltin.instructions)
                bindLong(5, AndroidAppControlBuiltin.VERSION.toLong())
                bindLong(6, now)
                bindLong(7, now)
                bindString(8, AndroidAppControlBuiltin.SLUG)
                bindLong(9, AndroidAppControlBuiltin.VERSION.toLong())
                executeUpdateDelete()
                close()
            }

            db.compileStatement(
                """
                UPDATE skills SET
                    name = ?, description = ?, whenToUse = ?, instructions = ?,
                    status = 'ACTIVE', origin = 'BUILTIN', isImmutable = 1,
                    version = ?, updatedAt = ?, activatedAt = ?, archivedAt = NULL
                WHERE slug = ? AND (version != ? OR origin != 'BUILTIN' OR isImmutable = 0)
                """.trimIndent()
            ).apply {
                bindString(1, WeatherWidgetsBuiltin.NAME)
                bindString(2, WeatherWidgetsBuiltin.DESCRIPTION)
                bindString(3, WeatherWidgetsBuiltin.WHEN_TO_USE)
                bindString(4, WeatherWidgetsBuiltin.instructions)
                bindLong(5, WeatherWidgetsBuiltin.VERSION.toLong())
                bindLong(6, now)
                bindLong(7, now)
                bindString(8, WeatherWidgetsBuiltin.SLUG)
                bindLong(9, WeatherWidgetsBuiltin.VERSION.toLong())
                executeUpdateDelete()
                close()
            }

            db.compileStatement(
                """
                UPDATE skills SET
                    name = ?, description = ?, whenToUse = ?, instructions = ?,
                    status = 'ACTIVE', origin = 'BUILTIN', isImmutable = 1,
                    version = ?, updatedAt = ?, activatedAt = ?, archivedAt = NULL
                WHERE slug = ? AND (version != ? OR origin != 'BUILTIN' OR isImmutable = 0)
                """.trimIndent()
            ).apply {
                bindString(1, SkillCreatorBuiltin.NAME)
                bindString(2, SkillCreatorBuiltin.DESCRIPTION)
                bindString(3, SkillCreatorBuiltin.WHEN_TO_USE)
                bindString(4, SkillCreatorBuiltin.instructions)
                bindLong(5, SkillCreatorBuiltin.VERSION.toLong())
                bindLong(6, now)
                bindLong(7, now)
                bindString(8, SkillCreatorBuiltin.SLUG)
                bindLong(9, SkillCreatorBuiltin.VERSION.toLong())
                executeUpdateDelete()
                close()
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()

                // 1. Create conversations table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS conversations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workspaceId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(workspaceId) REFERENCES workspaces(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_workspaceId ON conversations(workspaceId)")

                // 2. For each workspace that has messages, create a default conversation
                db.execSQL("""
                    INSERT INTO conversations (workspaceId, title, createdAt, updatedAt)
                    SELECT DISTINCT workspaceId, 'Chat inicial', $now, $now
                    FROM messages
                """)

                // 3. Recreate messages table with conversationId column
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS messages_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workspaceId INTEGER NOT NULL,
                        agentId INTEGER,
                        conversationId INTEGER,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        attachedFiles TEXT NOT NULL DEFAULT '',
                        toolCallsJson TEXT NOT NULL DEFAULT '',
                        toolResultsJson TEXT NOT NULL DEFAULT '',
                        reasoning TEXT,
                        FOREIGN KEY(workspaceId) REFERENCES workspaces(id) ON DELETE CASCADE,
                        FOREIGN KEY(agentId) REFERENCES agents(id) ON DELETE SET NULL,
                        FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_new_workspaceId ON messages_new(workspaceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_new_agentId ON messages_new(agentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_new_conversationId ON messages_new(conversationId)")

                // 4. Copy data, assigning conversationId from the default conversation created above
                db.execSQL("""
                    INSERT INTO messages_new (id, workspaceId, agentId, conversationId, role, content, timestamp, attachedFiles, toolCallsJson, toolResultsJson, reasoning)
                    SELECT m.id, m.workspaceId, m.agentId, c.id, m.role, m.content, m.timestamp, m.attachedFiles, m.toolCallsJson, m.toolResultsJson, m.reasoning
                    FROM messages m
                    LEFT JOIN conversations c ON c.workspaceId = m.workspaceId
                """)

                // 5. Swap tables
                db.execSQL("DROP TABLE messages")
                db.execSQL("ALTER TABLE messages_new RENAME TO messages")
            }
        }
    }
}
