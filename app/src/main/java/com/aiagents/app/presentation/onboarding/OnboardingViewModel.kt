package com.aiagents.app.presentation.onboarding

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.auth.FirebaseAuthManager
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.diagnostics.ErrorReportContext
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.memory.CortexProfileStore
import com.aiagents.app.data.repository.AgentRepository
import com.aiagents.app.data.repository.SubscriptionRepository
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
    private val agentRepository: AgentRepository,
    private val cortexProfileStore: CortexProfileStore,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val subscriptionRepository: SubscriptionRepository,
    private val errorReporter: AppErrorReporter
) : ViewModel() {

    val currentStep = savedStateHandle.getStateFlow("currentStep", 0)

    val userName = savedStateHandle.getStateFlow("userName", "")

    val userNickname = savedStateHandle.getStateFlow("userNickname", "")

    private val deviceLanguage = if (Locale.getDefault().language == "es") "es" else "en"
    val selectedLanguage = savedStateHandle.getStateFlow("selectedLanguage", deviceLanguage)

    // Assistant identity and personality configured explicitly by the user.
    val assistantName = savedStateHandle.getStateFlow("assistantName", "")
    val sarcasmLevel = savedStateHandle.getStateFlow("sarcasm", 0)
    val creativityLevel = savedStateHandle.getStateFlow("creativity", 50)
    val formalityLevel = savedStateHandle.getStateFlow("formality", 50)
    val empathyLevel = savedStateHandle.getStateFlow("empathy", 50)
    val technicalPrecision = savedStateHandle.getStateFlow("technical", 70)
    val onboardingMode = savedStateHandle.getStateFlow(
        "onboardingMode",
        securePreferences.getOnboardingMode()
            ?.let { stored -> runCatching { OnboardingMode.valueOf(stored) }.getOrNull() }
            ?: OnboardingMode.MANAGED_CLOUD
    )
    val managedPrivacyAccepted = savedStateHandle.getStateFlow(
        "managedPrivacyAccepted",
        securePreferences.isManagedPrivacyAccepted()
    )
    private val _googleSignedIn = MutableStateFlow(firebaseAuthManager.isGoogleSignedIn)
    val googleSignedIn: StateFlow<Boolean> = _googleSignedIn.asStateFlow()
    private val _googleSignInLoading = MutableStateFlow(false)
    val googleSignInLoading: StateFlow<Boolean> = _googleSignInLoading.asStateFlow()
    private val _googleSignInError = MutableStateFlow<String?>(null)
    val googleSignInError: StateFlow<String?> = _googleSignInError.asStateFlow()

    val isOnboardingDone: Boolean
        get() = securePreferences.isOnboardingCompleted()

    private val _onboardingCompleted = MutableStateFlow(securePreferences.isOnboardingCompleted())
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    init {
        if (firebaseAuthManager.isGoogleSignedIn) {
            viewModelScope.launch {
                runCatching { syncAccountConsent() }
                    .onFailure { _googleSignInError.value = onboardingError(it, "consent_sync") }
            }
        }
    }

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

    fun setAssistantName(name: String) { savedStateHandle["assistantName"] = name }
    fun setSarcasm(value: Int) { savedStateHandle["sarcasm"] = value }
    fun setCreativity(value: Int) { savedStateHandle["creativity"] = value }
    fun setFormality(value: Int) { savedStateHandle["formality"] = value }
    fun setEmpathy(value: Int) { savedStateHandle["empathy"] = value }
    fun setTechnicalPrecision(value: Int) { savedStateHandle["technical"] = value }
    fun setOnboardingMode(mode: OnboardingMode) {
        savedStateHandle["onboardingMode"] = mode
        securePreferences.setOnboardingMode(mode.name)
        _googleSignInError.value = null
    }

    fun setManagedPrivacyAccepted(accepted: Boolean) {
        savedStateHandle["managedPrivacyAccepted"] = accepted
        _googleSignInError.value = null
    }

    fun signInWithGoogle(activity: Activity) {
        if (_googleSignInLoading.value) return
        viewModelScope.launch {
            _googleSignInLoading.value = true
            _googleSignInError.value = null
            runCatching {
                firebaseAuthManager.signInWithGoogle(activity)
                _googleSignedIn.value = firebaseAuthManager.isGoogleSignedIn
                syncAccountConsent()
            }
                .onFailure {
                    _googleSignInError.value = onboardingError(it, "google_sign_in")
                }
            _googleSignInLoading.value = false
        }
    }

    fun acceptFreeDataDisclosure(onAccepted: () -> Unit) {
        if (onboardingMode.value != OnboardingMode.MANAGED_CLOUD) {
            onAccepted()
            return
        }
        if (
            _googleSignInLoading.value ||
            !managedPrivacyAccepted.value ||
            !_googleSignedIn.value
        ) return
        viewModelScope.launch {
            _googleSignInLoading.value = true
            _googleSignInError.value = null
            subscriptionRepository.acceptFreeDataConsent()
                .onSuccess {
                    securePreferences.enableManagedFreePlan()
                    savedStateHandle["managedPrivacyAccepted"] = true
                    onAccepted()
                }
                .onFailure {
                    _googleSignInError.value = onboardingError(it, "consent_acceptance")
                }
            _googleSignInLoading.value = false
        }
    }

    private suspend fun syncAccountConsent() {
        subscriptionRepository.refresh().getOrThrow()
        if (subscriptionRepository.usage.value.hasCurrentFreeDataConsent) {
            securePreferences.enableManagedFreePlan()
            savedStateHandle["managedPrivacyAccepted"] = true
        }
    }

    private fun onboardingError(error: Throwable, operation: String): String =
        errorReporter.present(
            error,
            ErrorReportContext(component = "onboarding", operation = operation)
        ).displayMessage

    fun nextStep() {
        savedStateHandle["currentStep"] = (currentStep.value + 1).coerceAtMost(TOTAL_ONBOARDING_STEPS - 1)
    }

    fun previousStep() {
        savedStateHandle["currentStep"] = (currentStep.value - 1).coerceAtLeast(0)
    }

    private var completing = false

    fun completeOnboarding() {
        val chosenAssistantName = assistantName.value.trim()
        if (
            completing ||
            chosenAssistantName.isBlank() ||
            !OnboardingModePolicy.canContinue(
                mode = onboardingMode.value,
                managedPrivacyAccepted = managedPrivacyAccepted.value,
                googleSignedIn = _googleSignedIn.value,
                googleSignInLoading = _googleSignInLoading.value
            )
        ) return
        completing = true
        viewModelScope.launch {
            val name = userName.value.trim()
            val nickname = userNickname.value.trim()
            val now = System.currentTimeMillis()

            securePreferences.saveUserIdentity(name, nickname)
            securePreferences.setOnboardingMode(onboardingMode.value.name)
            if (onboardingMode.value == OnboardingMode.MANAGED_CLOUD) {
                securePreferences.enableManagedFreePlan()
            } else {
                securePreferences.disableManagedFreePlanSelection()
            }

            cortexProfileStore.seedFromOnboarding(
                agentName = chosenAssistantName,
                userName = name,
                preferredName = nickname
            )
            val orchestrator = agentRepository.getOrchestratorAgent()
            if (orchestrator != null) {
                agentRepository.updateAgent(
                    orchestrator.copy(
                        name = chosenAssistantName,
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
