package com.aiagents.app.data.orchestration

import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.domain.model.Agent
import com.aiagents.app.domain.model.isOrchestrator
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates fine-tuning datasets dynamically based on the user's current agents.
 * Each time agents change, the user can re-export and re-train.
 */
@Singleton
class DelegationDatasetGenerator @Inject constructor(
    private val repository: AgentRepository
) {
    /**
     * Templates for generating varied training queries per keyword category.
     * Each template has a {keyword} placeholder that gets replaced.
     */
    private val queryTemplates = listOf(
        "Ayudame con {keyword}",
        "Necesito ayuda con {keyword}",
        "Tengo una pregunta sobre {keyword}",
        "Puedes explicarme sobre {keyword}?",
        "Quiero saber mas de {keyword}",
        "Dame informacion sobre {keyword}",
        "Como funciona {keyword}?",
        "Necesito que me asesores en {keyword}",
        "Tengo un problema con {keyword}",
        "Haz algo relacionado con {keyword}",
    )

    /**
     * Queries that should NOT be delegated to any agent.
     */
    private val noAgentQueries = listOf(
        "Hola, como estas?",
        "Buenos dias!",
        "Que tal tu dia?",
        "Gracias por tu ayuda",
        "Quien eres?",
        "Que puedes hacer?",
        "Cuentame un chiste",
        "Que opinas sobre la inteligencia artificial?",
        "Cual es el sentido de la vida?",
        "Recomiendame una pelicula buena",
        "Que hora es?",
        "Adios, hasta luego",
        "Estoy aburrido",
        "Cual es tu color favorito?",
        "Que pais es mas grande, Brasil o Argentina?",
        "Dame una receta de pasta",
        "Traduceme esto al ingles: buenos dias",
        "Resume este texto en 3 lineas",
        "Escribe un poema sobre el mar",
        "Cuantos habitantes tiene Mexico?",
        "Que dia es hoy?",
        "Hola!",
        "Gracias!",
        "Me puedes ayudar?",
        "Que sabes hacer?",
    )

    private val noAgentReasons = listOf(
        "saludo general",
        "conversacion casual",
        "agradecimiento",
        "pregunta sobre identidad del asistente",
        "pregunta sobre capacidades",
        "entretenimiento general",
        "opinion general sin agente especializado",
        "pregunta filosofica general",
        "recomendacion de entretenimiento",
        "pregunta de informacion basica",
        "despedida",
        "conversacion casual",
        "pregunta personal al asistente",
        "pregunta de cultura general",
        "receta de cocina sin agente especializado",
        "traduccion general",
        "tarea de resumen general",
        "escritura creativa general",
        "pregunta de cultura general",
        "pregunta de fecha",
        "saludo general",
        "agradecimiento",
        "pregunta general",
        "pregunta sobre capacidades",
    )

    /**
     * Generates a JSONL dataset string based on the current agents in the database.
     * Distribution: ~60% clear delegation, ~20% ambiguous, ~20% no delegation.
     */
    suspend fun generateDataset(): String {
        val agents = repository.getAllAgentsOnce()
            .filter { !it.isOrchestrator && it.whenToUse.isNotEmpty() }

        val lines = mutableListOf<String>()

        // --- Clear delegation examples (~60%) ---
        for (agent in agents) {
            val keywords = agent.whenToUse.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            for (keyword in keywords) {
                for (template in queryTemplates) {
                    val query = template.replace("{keyword}", keyword)
                    lines.add(buildJsonLine(query, delegateTo = agent.name))
                }
            }
        }

        // --- Ambiguous examples (~20%): queries that mention multiple agent domains ---
        if (agents.size >= 2) {
            val ambiguousLines = generateAmbiguousExamples(agents)
            lines.addAll(ambiguousLines)
        }

        // --- No delegation examples (~20%) ---
        val noAgentLines = noAgentQueries.zip(noAgentReasons).map { (query, reason) ->
            buildJsonLineNoAgent(query, reason)
        }
        lines.addAll(noAgentLines)

        lines.shuffle()
        return lines.joinToString("\n")
    }

    /**
     * Generates ambiguous examples where the query could go to multiple agents.
     * We pick the "primary" agent based on the strongest keyword match.
     */
    private fun generateAmbiguousExamples(agents: List<Agent>): List<String> {
        val lines = mutableListOf<String>()
        val ambiguousTemplates = listOf(
            "Necesito algo que combine {kw1} y {kw2}",
            "Tengo un proyecto que involucra {kw1} pero tambien {kw2}",
            "Quiero {kw1}, y ademas necesito considerar {kw2}",
        )

        // Generate pairs of agents
        for (i in agents.indices) {
            for (j in i + 1 until agents.size) {
                val kw1List = agents[i].whenToUse.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val kw2List = agents[j].whenToUse.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (kw1List.isEmpty() || kw2List.isEmpty()) continue

                for (template in ambiguousTemplates) {
                    val kw1 = kw1List.random()
                    val kw2 = kw2List.random()
                    // Primary agent is the first one mentioned
                    val query = template.replace("{kw1}", kw1).replace("{kw2}", kw2)
                    lines.add(buildJsonLine(query, delegateTo = agents[i].name))
                }
            }
        }
        return lines
    }

    private fun buildJsonLine(input: String, delegateTo: String): String {
        val obj = JSONObject().apply {
            put("input", input)
            put("output", "TOOL_CALL: {\"name\": \"delegate\", \"arguments\": {\"agent\": \"$delegateTo\"}}")
        }
        return obj.toString()
    }

    private fun buildJsonLineNoAgent(input: String, reason: String): String {
        val obj = JSONObject().apply {
            put("input", input)
            put("output", "TOOL_CALL: {\"name\": \"no_agent\", \"arguments\": {\"reason\": \"$reason\"}}")
        }
        return obj.toString()
    }

    /**
     * Returns a human-readable summary of what the dataset would contain.
     */
    suspend fun getDatasetSummary(): String {
        val agents = repository.getAllAgentsOnce()
            .filter { !it.isOrchestrator && it.whenToUse.isNotEmpty() }

        if (agents.isEmpty()) {
            return "No hay agentes especializados configurados. Agrega agentes con keywords en 'Cuando usar' para generar un dataset."
        }

        val perAgent = agents.map { agent ->
            val keywords = agent.whenToUse.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val examples = keywords.size * queryTemplates.size
            "${agent.name}: ${keywords.size} keywords, ~$examples ejemplos"
        }

        val totalDelegation = agents.sumOf { agent ->
            agent.whenToUse.split(",").map { it.trim() }.filter { it.isNotEmpty() }.size * queryTemplates.size
        }
        val totalNoAgent = noAgentQueries.size
        val totalAmbiguous = if (agents.size >= 2) {
            (agents.size * (agents.size - 1) / 2) * 3 // 3 templates per pair
        } else 0

        return buildString {
            appendLine("Dataset de entrenamiento para ${agents.size} agentes:")
            appendLine()
            perAgent.forEach { appendLine("  $it") }
            appendLine()
            appendLine("Total estimado:")
            appendLine("  Delegacion clara: ~$totalDelegation ejemplos")
            appendLine("  Ambiguos: ~$totalAmbiguous ejemplos")
            appendLine("  Sin delegacion: ~$totalNoAgent ejemplos")
            appendLine("  TOTAL: ~${totalDelegation + totalAmbiguous + totalNoAgent} ejemplos")
        }
    }
}
