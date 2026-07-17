package com.aiagents.app.presentation.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
import com.aiagents.app.data.repository.SkillRepository
import com.aiagents.app.data.skills.SkillReviewScheduler
import com.aiagents.app.domain.model.SkillDraftInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val repository: SkillRepository,
    private val reviewScheduler: SkillReviewScheduler,
    private val errorReporter: AppErrorReporter
) : ViewModel() {
    val skills = repository.observeSkills().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val recentReviews = repository.observeRecentReviews().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val reviewSettings = reviewScheduler.settings

    private val _selectedSkillId = MutableStateFlow<Long?>(null)
    val selectedSkillId: StateFlow<Long?> = _selectedSkillId.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    fun selectSkill(id: Long?) {
        _selectedSkillId.value = id
    }

    fun saveSkill(id: Long?, input: SkillDraftInput, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveUserSkill(id, input)
                .onSuccess { savedId ->
                    _selectedSkillId.value = savedId
                    _events.emit(if (id == null) "Borrador creado" else "Skill actualizada")
                    onSaved()
                }
                .onFailure { error ->
                    _events.emit(skillError(error, "skill_save"))
                }
        }
    }

    fun activate(id: Long) {
        viewModelScope.launch {
            repository.activate(id)
                .onSuccess { _events.emit("Skill activada") }
                .onFailure { _events.emit(skillError(it, "skill_activate")) }
        }
    }

    fun archive(id: Long) {
        viewModelScope.launch {
            repository.archive(id)
                .onSuccess {
                    _selectedSkillId.value = null
                    _events.emit("Skill archivada")
                }
                .onFailure { _events.emit(skillError(it, "skill_archive")) }
        }
    }

    fun setAutomaticReviewEnabled(enabled: Boolean) = reviewScheduler.setEnabled(enabled)

    fun setReviewInterval(interval: Int) = reviewScheduler.setMessageInterval(interval)

    private fun skillError(error: Throwable, operation: String): String =
        errorReporter.present(
            error,
            ErrorReportContext(component = "skills", operation = operation)
        ).displayMessage
}
