package com.aiagents.app.presentation.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.local.MemoryDao
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.model.MemoryEntity
import com.aiagents.app.data.repository.AgentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

const val TOTAL_ONBOARDING_STEPS = 6

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val securePreferences: SecurePreferences,
    private val memoryDao: MemoryDao,
    private val agentRepository: AgentRepository
) : ViewModel() {

    val currentStep = savedStateHandle.getStateFlow("currentStep", 0)

    val userName = savedStateHandle.getStateFlow("userName", "")

    val userNickname = savedStateHandle.getStateFlow("userNickname", "")

    private val deviceLanguage = if (Locale.getDefault().language == "es") "es" else "en"
    val selectedLanguage = savedStateHandle.getStateFlow("selectedLanguage", deviceLanguage)

    // Cortex config
    val cortexName = savedStateHandle.getStateFlow("cortexName", "Cortex")
    val sarcasmLevel = savedStateHandle.getStateFlow("sarcasm", 0)
    val creativityLevel = savedStateHandle.getStateFlow("creativity", 50)
    val formalityLevel = savedStateHandle.getStateFlow("formality", 50)
    val empathyLevel = savedStateHandle.getStateFlow("empathy", 50)
    val technicalPrecision = savedStateHandle.getStateFlow("technical", 70)

    val isOnboardingDone: Boolean
        get() = securePreferences.isOnboardingCompleted()

    private val _onboardingCompleted = MutableStateFlow(securePreferences.isOnboardingCompleted())
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    fun setLanguage(language: String) {
        savedStateHandle["selectedLanguage"] = language
        securePreferences.setAppLanguage(language)
    }

    fun setUserName(name: String) {
        savedStateHandle["userName"] = name
    }

    fun setUserNickname(nickname: String) {
        savedStateHandle["userNickname"] = nickname
    }

    fun setCortexName(name: String) { savedStateHandle["cortexName"] = name }
    fun setSarcasm(value: Int) { savedStateHandle["sarcasm"] = value }
    fun setCreativity(value: Int) { savedStateHandle["creativity"] = value }
    fun setFormality(value: Int) { savedStateHandle["formality"] = value }
    fun setEmpathy(value: Int) { savedStateHandle["empathy"] = value }
    fun setTechnicalPrecision(value: Int) { savedStateHandle["technical"] = value }

    fun nextStep() {
        savedStateHandle["currentStep"] = (currentStep.value + 1).coerceAtMost(TOTAL_ONBOARDING_STEPS - 1)
    }

    fun previousStep() {
        savedStateHandle["currentStep"] = (currentStep.value - 1).coerceAtLeast(0)
    }

    private var completing = false

    fun completeOnboarding() {
        if (completing) return
        completing = true
        viewModelScope.launch {
            val name = userName.value.trim()
            val nickname = userNickname.value.trim()
            val now = System.currentTimeMillis()

            // Delete any previous onboarding identity memories, then insert fresh
            memoryDao.deleteByCategorySubcategorySource("fact", "user_identity", "onboarding")

            if (name.isNotBlank()) {
                memoryDao.insert(MemoryEntity(
                    content = "name: $name",
                    category = "fact", subcategory = "user_identity",
                    importance = 10, confidence = 1.0f, source = "onboarding",
                    createdAt = now, updatedAt = now, lastAccessedAt = now
                ))
            }

            if (nickname.isNotBlank() && nickname != name) {
                memoryDao.insert(MemoryEntity(
                    content = "preferred_name: $nickname",
                    category = "fact", subcategory = "user_identity",
                    importance = 10, confidence = 1.0f, source = "onboarding",
                    createdAt = now, updatedAt = now, lastAccessedAt = now
                ))
            }

            // Update Cortex name, personality and prompt
            val chosenCortexName = cortexName.value.trim().ifBlank { "Cortex" }
            val cortex = agentRepository.getOrchestratorAgent()
            if (cortex != null) {
                val updatedPrompt = cortex.systemPrompt.replace(
                    "You are Cortex,",
                    "You are $chosenCortexName,"
                )

                agentRepository.updateAgent(
                    cortex.copy(
                        name = chosenCortexName,
                        systemPrompt = updatedPrompt,
                        sarcasmLevel = sarcasmLevel.value,
                        creativityLevel = creativityLevel.value,
                        formalityLevel = formalityLevel.value,
                        empathyLevel = empathyLevel.value,
                        technicalPrecision = technicalPrecision.value,
                        updatedAt = now
                    )
                )
            }

            // Apply locale globally now that onboarding is done
            val language = selectedLanguage.value
            securePreferences.setAppLanguage(language)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))

            securePreferences.setOnboardingCompleted(true)
            _onboardingCompleted.value = true
        }
    }

}
