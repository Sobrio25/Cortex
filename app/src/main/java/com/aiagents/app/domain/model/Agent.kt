package com.aiagents.app.domain.model

data class Agent(
    val id: Long = 0,
    val name: String,
    val role: String,
    val systemPrompt: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val folderPath: String,
    val enableTerminal: Boolean = false,
    val whenToUse: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Personalización de personalidad (principalmente para Cortex)
    val sarcasmLevel: Int = 0,        // 0-100, default 0
    val creativityLevel: Int = 50,    // 0-100, default 50
    val formalityLevel: Int = 50,     // 0-100, default 50
    val empathyLevel: Int = 50,       // 0-100, default 50
    val technicalPrecision: Int = 70,  // 0-100, default 70
    // Si true, Cortex usa modelo local para decidir delegación antes de cloud
    val useLocalRouting: Boolean = false,
    // CSV de herramientas MCP habilitadas: "brave_search,serpapi,google_maps" (vacío = todas habilitadas)
    val enabledTools: String = "",
    // Agentes del sistema (ocultos en la UI, solo usados internamente por Cortex)
    val isSystemAgent: Boolean = false
)
