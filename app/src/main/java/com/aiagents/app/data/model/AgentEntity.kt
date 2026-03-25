package com.aiagents.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aiagents.app.domain.model.Agent

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey(autoGenerate = true)
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
    // Personalización de personalidad
    val sarcasmLevel: Int = 0,
    val creativityLevel: Int = 50,
    val formalityLevel: Int = 50,
    val empathyLevel: Int = 50,
    val technicalPrecision: Int = 70,
    val useLocalRouting: Boolean = false,
    val enabledTools: String = "",
    val isSystemAgent: Boolean = false
) {
    fun toDomain(): Agent = Agent(
        id = id,
        name = name,
        role = role,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens,
        folderPath = folderPath,
        enableTerminal = enableTerminal,
        whenToUse = whenToUse,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sarcasmLevel = sarcasmLevel,
        creativityLevel = creativityLevel,
        formalityLevel = formalityLevel,
        empathyLevel = empathyLevel,
        technicalPrecision = technicalPrecision,
        useLocalRouting = useLocalRouting,
        enabledTools = enabledTools,
        isSystemAgent = isSystemAgent
    )

    companion object {
        fun fromDomain(agent: Agent): AgentEntity = AgentEntity(
            id = agent.id,
            name = agent.name,
            role = agent.role,
            systemPrompt = agent.systemPrompt,
            temperature = agent.temperature,
            maxTokens = agent.maxTokens,
            folderPath = agent.folderPath,
            enableTerminal = agent.enableTerminal,
            whenToUse = agent.whenToUse,
            createdAt = agent.createdAt,
            updatedAt = agent.updatedAt,
            sarcasmLevel = agent.sarcasmLevel,
            creativityLevel = agent.creativityLevel,
            formalityLevel = agent.formalityLevel,
            empathyLevel = agent.empathyLevel,
            technicalPrecision = agent.technicalPrecision,
            useLocalRouting = agent.useLocalRouting,
            enabledTools = agent.enabledTools,
            isSystemAgent = agent.isSystemAgent
        )
    }
}
