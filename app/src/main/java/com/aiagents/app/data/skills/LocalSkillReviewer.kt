package com.aiagents.app.data.skills

import com.aiagents.app.domain.model.SkillDraftInput
import java.text.Normalizer
import java.util.Locale

data class LocalSkillCandidate(
    val draft: SkillDraftInput,
    val reason: String
)

/**
 * Conservative, deterministic reviewer. It performs no network or tool calls and
 * only proposes a draft when at least two user requests share a strong intent.
 */
object LocalSkillReviewer {
    private const val MIN_SIMILARITY = 0.40

    private val stopWords = setOf(
        "a", "al", "algo", "and", "ayuda", "ayudame", "con", "crear", "crea", "de", "del",
        "el", "en", "for", "hacer", "haz", "la", "las", "lo", "los", "me", "mi", "necesito",
        "of", "para", "please", "por", "que", "quiero", "the", "to", "un", "una", "y"
    )

    fun review(redactedTranscript: String): LocalSkillCandidate? {
        val requests = redactedTranscript.lineSequence()
            .filter { it.startsWith("USUARIO: ") }
            .map { it.removePrefix("USUARIO: ").trim() }
            .filter { it.length >= 12 }
            .map { Request(meaningfulTokens(it)) }
            .filter { it.tokens.size >= 3 }
            .toList()

        if (requests.size < 2) return null

        var bestCluster: List<Request> = emptyList()
        var bestScore = 0.0
        requests.forEachIndexed { index, seed ->
            val cluster = buildList {
                add(seed)
                requests.drop(index + 1).forEach { candidate ->
                    if (similarity(seed.tokens, candidate.tokens) >= MIN_SIMILARITY) add(candidate)
                }
            }
            val score = cluster.drop(1).sumOf { similarity(seed.tokens, it.tokens) }
            if (cluster.size > bestCluster.size || (cluster.size == bestCluster.size && score > bestScore)) {
                bestCluster = cluster
                bestScore = score
            }
        }

        if (bestCluster.size < 2) return null

        val tokenFrequency = linkedMapOf<String, Int>()
        bestCluster.forEach { request ->
            request.tokens.forEach { token -> tokenFrequency[token] = (tokenFrequency[token] ?: 0) + 1 }
        }
        val sharedTerms = tokenFrequency.entries
            .filter { it.value >= 2 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(5)

        if (sharedTerms.size < 2) return null

        val titleTerms = sharedTerms.take(3).joinToString(" ") { token ->
            token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        val topic = sharedTerms.joinToString(", ")
        val name = "Flujo: $titleTerms".take(80)

        return LocalSkillCandidate(
            draft = SkillDraftInput(
                name = name,
                description = "Ayuda con solicitudes recurrentes relacionadas con $topic.",
                whenToUse = sharedTerms.joinToString(","),
                instructions = """
                    # Objetivo
                    Resolver de forma consistente solicitudes sobre $topic.

                    # Proceso
                    1. Confirma el resultado esperado y los datos de entrada indispensables.
                    2. Ejecuta el flujo usando solo las capacidades disponibles en el dispositivo.
                    3. Verifica el resultado y comunica cualquier limitación o dato faltante.

                    # Seguridad
                    - No guardes credenciales ni datos personales en la skill.
                    - Solicita confirmación antes de una acción externa o irreversible.
                    - Si falta una tool necesaria, explica la limitación en lugar de inventar acceso.
                """.trimIndent()
            ),
            reason = "Se detectaron ${bestCluster.size} solicitudes similares sobre $topic."
        )
    }

    private fun meaningfulTokens(value: String): Set<String> = normalize(value)
        .split(Regex("[^a-z0-9]+"))
        .asSequence()
        .filter { it.length >= 3 && it !in stopWords && !it.contains("redactado") }
        .take(24)
        .toSet()

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)

    private fun similarity(first: Set<String>, second: Set<String>): Double {
        val intersection = first.intersect(second).size
        if (intersection < 2) return 0.0
        return intersection.toDouble() / first.union(second).size.toDouble()
    }

    private data class Request(val tokens: Set<String>)
}
