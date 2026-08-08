package com.aiagents.app.data.repository

import com.aiagents.app.data.local.SkillDao
import com.aiagents.app.data.local.SkillReviewDao
import com.aiagents.app.data.capabilities.CapabilityCatalog
import com.aiagents.app.data.memory.CortexMemoryPolicy
import com.aiagents.app.data.model.SkillEntity
import com.aiagents.app.data.model.SkillReviewEntity
import com.aiagents.app.domain.model.Skill
import com.aiagents.app.domain.model.CapabilityCategory
import com.aiagents.app.domain.model.SkillDraftInput
import com.aiagents.app.domain.model.SkillOrigin
import com.aiagents.app.domain.model.SkillReview
import com.aiagents.app.domain.model.SkillReviewStatus
import com.aiagents.app.domain.model.SkillStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillRepository @Inject constructor(
    private val skillDao: SkillDao,
    private val reviewDao: SkillReviewDao
) {
    fun observeSkills(): Flow<List<Skill>> = skillDao.observeAll().map { rows ->
        rows.map(SkillEntity::toDomain)
    }

    fun observeActiveSkills(): Flow<List<Skill>> = skillDao.observeActive().map { rows ->
        rows.map(SkillEntity::toDomain)
    }

    fun observeRecentReviews(limit: Int = 20): Flow<List<SkillReview>> =
        reviewDao.observeRecent(limit).map { rows -> rows.map(SkillReviewEntity::toDomain) }

    suspend fun getSkill(id: Long): Skill? = skillDao.getById(id)?.toDomain()

    suspend fun getSkillBySlug(slug: String): Skill? =
        skillDao.getBySlug(slug.trim().lowercase(Locale.ROOT))?.toDomain()

    suspend fun getSkillsOnce(): List<Skill> = skillDao.getAllOnce().map(SkillEntity::toDomain)

    suspend fun getActiveSkillsOnce(): List<Skill> = skillDao.getActiveOnce().map(SkillEntity::toDomain)

    suspend fun saveUserSkill(id: Long?, input: SkillDraftInput): Result<Long> = runCatching {
        val normalized = validate(input)
        val now = System.currentTimeMillis()

        if (id == null) {
            skillDao.insert(
                SkillEntity(
                    slug = nextAvailableSlug(normalized.name),
                    name = normalized.name,
                    description = normalized.description,
                    whenToUse = normalized.whenToUse,
                    instructions = normalized.instructions,
                    category = normalized.category.name,
                    requiredTools = normalized.requiredTools.sorted().joinToString(","),
                    status = SkillStatus.DRAFT.name,
                    origin = SkillOrigin.USER.name,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            val existing = skillDao.getById(id) ?: error("La skill ya no existe")
            check(!existing.isImmutable) { "Las skills integradas no se pueden modificar" }
            check(
                skillDao.updateMutable(
                    id = id,
                    name = normalized.name,
                    description = normalized.description,
                    whenToUse = normalized.whenToUse,
                    instructions = normalized.instructions,
                    category = normalized.category.name,
                    requiredTools = normalized.requiredTools.sorted().joinToString(","),
                    updatedAt = now
                ) == 1
            ) { "No se pudo actualizar la skill" }
            id
        }
    }

    suspend fun createAutomaticDraft(input: SkillDraftInput): Result<Long> = runCatching {
        val normalized = validate(input)
        val baseSlug = slugify(normalized.name)
        val existing = skillDao.getAutomaticByName(normalized.name) ?: skillDao.getBySlug(baseSlug)
        if (existing != null && existing.origin == SkillOrigin.AUTO.name) {
            return@runCatching existing.id
        }

        val now = System.currentTimeMillis()
        skillDao.insert(
            SkillEntity(
                slug = nextAvailableSlug(normalized.name),
                name = normalized.name,
                description = normalized.description,
                whenToUse = normalized.whenToUse,
                instructions = normalized.instructions,
                category = normalized.category.name,
                requiredTools = normalized.requiredTools.sorted().joinToString(","),
                status = SkillStatus.DRAFT.name,
                origin = SkillOrigin.AUTO.name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /** Creates an immediately usable skill, matching Hermes' background skill manager. */
    suspend fun createAutomaticActive(input: SkillDraftInput): Result<Long> = runCatching {
        val normalized = validateAutomatic(input)
        val baseSlug = slugify(normalized.name)
        val existing = skillDao.getAutomaticByName(normalized.name) ?: skillDao.getBySlug(baseSlug)
        if (existing != null) {
            check(existing.origin == SkillOrigin.AUTO.name) {
                "Ya existe una skill no automática con ese nombre"
            }
            check(existing.status != SkillStatus.ARCHIVED.name) {
                "La skill automática equivalente está archivada"
            }
            val now = System.currentTimeMillis()
            check(
                skillDao.updateAutomatic(
                    id = existing.id,
                    name = normalized.name,
                    description = normalized.description,
                    whenToUse = normalized.whenToUse,
                    instructions = normalized.instructions,
                    category = normalized.category.name,
                    requiredTools = normalized.requiredTools.sorted().joinToString(","),
                    updatedAt = now
                ) == 1
            ) { "No se pudo activar la skill automática" }
            return@runCatching existing.id
        }

        val now = System.currentTimeMillis()
        skillDao.insert(
            SkillEntity(
                slug = nextAvailableSlug(normalized.name),
                name = normalized.name,
                description = normalized.description,
                whenToUse = normalized.whenToUse,
                instructions = normalized.instructions,
                category = normalized.category.name,
                requiredTools = normalized.requiredTools.sorted().joinToString(","),
                status = SkillStatus.ACTIVE.name,
                origin = SkillOrigin.AUTO.name,
                createdAt = now,
                updatedAt = now,
                activatedAt = now
            )
        )
    }

    /** Background learning may only patch mutable, non-archived AUTO skills. */
    suspend fun updateAutomatic(id: Long, input: SkillDraftInput): Result<Long> = runCatching {
        val normalized = validateAutomatic(input)
        val existing = skillDao.getById(id) ?: error("La skill ya no existe")
        check(existing.origin == SkillOrigin.AUTO.name && !existing.isImmutable) {
            "El aprendizaje automático no puede modificar esta skill"
        }
        check(existing.status != SkillStatus.ARCHIVED.name) {
            "Una skill archivada no puede reactivarse automáticamente"
        }
        check(
            skillDao.updateAutomatic(
                id = id,
                name = normalized.name,
                description = normalized.description,
                whenToUse = normalized.whenToUse,
                instructions = normalized.instructions,
                category = normalized.category.name,
                requiredTools = normalized.requiredTools.sorted().joinToString(","),
                updatedAt = System.currentTimeMillis()
            ) == 1
        ) { "No se pudo actualizar la skill automática" }
        id
    }

    /** Records that the agent loaded and applied a skill. Feeds the learning loop. */
    suspend fun recordSkillUsage(id: Long): Result<Unit> = runCatching {
        skillDao.recordUsage(id, System.currentTimeMillis())
    }

    suspend fun activate(id: Long): Result<Unit> = updateStatus(id, SkillStatus.ACTIVE)

    suspend fun deactivate(id: Long): Result<Unit> = updateStatus(id, SkillStatus.INACTIVE)

    suspend fun setEnabled(id: Long, enabled: Boolean): Result<Unit> =
        updateStatus(id, if (enabled) SkillStatus.ACTIVE else SkillStatus.INACTIVE)

    suspend fun archive(id: Long): Result<Unit> = updateStatus(id, SkillStatus.ARCHIVED)

    private suspend fun updateStatus(id: Long, status: SkillStatus): Result<Unit> = runCatching {
        val existing = skillDao.getById(id) ?: error("La skill ya no existe")
        if (status == SkillStatus.ARCHIVED) {
            check(!existing.isImmutable) { "Las skills integradas se desactivan, no se archivan" }
        }
        val now = System.currentTimeMillis()
        val activatedAt = when (status) {
            SkillStatus.ACTIVE -> now
            else -> existing.activatedAt
        }
        val archivedAt = when (status) {
            SkillStatus.ARCHIVED -> now
            else -> null
        }
        check(
            skillDao.updateStatus(
                id = id,
                status = status.name,
                updatedAt = now,
                activatedAt = activatedAt,
                archivedAt = archivedAt
            ) == 1
        ) { "No se pudo cambiar el estado de la skill" }
    }

    private suspend fun nextAvailableSlug(name: String): String {
        val base = slugify(name)
        var candidate = base
        var suffix = 2
        while (skillDao.getBySlug(candidate) != null) {
            candidate = "$base-$suffix"
            suffix += 1
        }
        return candidate
    }

    companion object {
        fun validate(input: SkillDraftInput): SkillDraftInput {
            val normalized = SkillDraftInput(
                name = input.name.trim(),
                description = input.description.trim(),
                whenToUse = input.whenToUse.trim(),
                instructions = input.instructions.trim(),
                category = input.category,
                requiredTools = input.requiredTools
            )
            require(normalized.name.length in 3..80) {
                "El nombre debe tener entre 3 y 80 caracteres"
            }
            require(normalized.description.length in 10..500) {
                "La descripción debe tener entre 10 y 500 caracteres"
            }
            require(normalized.whenToUse.length in 5..500) {
                "Indica cuándo debe usarse la skill"
            }
            require(normalized.instructions.length in 30..20_000) {
                "Las instrucciones deben tener entre 30 y 20,000 caracteres"
            }
            val detectedTools = CapabilityCatalog.detectRequiredTools(
                listOf(normalized.description, normalized.whenToUse, normalized.instructions)
                    .joinToString("\n")
            )
            val requiredTools = (normalized.requiredTools + detectedTools)
                .filterTo(linkedSetOf()) { it in CapabilityCatalog.knownToolNames }
            val category = normalized.category.takeUnless { it == CapabilityCategory.CUSTOM }
                ?: CapabilityCatalog.inferCategory(requiredTools)
            return normalized.copy(category = category, requiredTools = requiredTools)
        }

        fun validateAutomatic(input: SkillDraftInput): SkillDraftInput {
            val normalized = validate(input)
            val combined = listOf(
                normalized.name,
                normalized.description,
                normalized.whenToUse,
                normalized.instructions
            ).joinToString("\n")
            CortexMemoryPolicy.securityIssue(combined)?.let { issue ->
                throw IllegalArgumentException("La skill automática fue rechazada: $issue")
            }
            return normalized
        }

        fun slugify(value: String): String {
            val ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase(Locale.ROOT)
            return ascii
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(64)
                .ifBlank { "skill" }
        }
    }
}

private fun SkillEntity.toDomain(): Skill = Skill(
    id = id,
    slug = slug,
    name = name,
    description = description,
    whenToUse = whenToUse,
    instructions = instructions,
    category = enumValueOrDefault(category, CapabilityCategory.CUSTOM),
    requiredTools = requiredTools.split(',').map(String::trim).filter(String::isNotBlank).toSet(),
    status = enumValueOrDefault(status, SkillStatus.DRAFT),
    origin = enumValueOrDefault(origin, SkillOrigin.IMPORTED),
    isImmutable = isImmutable,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt,
    activatedAt = activatedAt,
    archivedAt = archivedAt,
    usageCount = usageCount,
    lastUsedAt = lastUsedAt
)

private fun SkillReviewEntity.toDomain(): SkillReview = SkillReview(
    id = id,
    status = enumValueOrDefault(status, SkillReviewStatus.FAILED),
    summary = summary,
    candidateSkillId = candidateSkillId,
    messageCount = messageCount,
    createdAt = createdAt,
    completedAt = completedAt
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(default)
