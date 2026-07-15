package com.aiagents.app.data.skills

data class SelfImprovementCadenceState(
    val memoryTurns: Int = 0,
    val skillIterations: Int = 0
)

data class SelfImprovementCadenceResult(
    val state: SelfImprovementCadenceState,
    val reviewMemory: Boolean,
    val reviewSkills: Boolean
)

/** Pure cadence used by the background reviewer and covered by JVM tests. */
object SelfImprovementCadence {
    fun advance(
        current: SelfImprovementCadenceState,
        completedTurnIterations: Int,
        interval: Int
    ): SelfImprovementCadenceResult {
        require(interval > 0) { "interval must be positive" }
        require(completedTurnIterations > 0) { "a completed turn needs at least one model iteration" }

        val nextMemoryTurns = current.memoryTurns + 1
        val nextSkillIterations = current.skillIterations + completedTurnIterations
        val reviewMemory = nextMemoryTurns >= interval
        val reviewSkills = nextSkillIterations >= interval
        return SelfImprovementCadenceResult(
            state = SelfImprovementCadenceState(
                memoryTurns = if (reviewMemory) 0 else nextMemoryTurns,
                skillIterations = if (reviewSkills) 0 else nextSkillIterations
            ),
            reviewMemory = reviewMemory,
            reviewSkills = reviewSkills
        )
    }
}
