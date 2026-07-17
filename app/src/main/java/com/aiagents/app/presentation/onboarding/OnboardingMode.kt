package com.aiagents.app.presentation.onboarding

/** The inference setup the user deliberately chooses during first run. */
enum class OnboardingMode {
    MANAGED_CLOUD,
    BRING_YOUR_OWN_KEY,
    LOCAL
}

/** Keeps the first-run gate independent from Compose and therefore easy to verify. */
object OnboardingModePolicy {
    fun canContinue(
        mode: OnboardingMode,
        managedPrivacyAccepted: Boolean,
        googleSignedIn: Boolean,
        googleSignInLoading: Boolean
    ): Boolean = when (mode) {
        OnboardingMode.MANAGED_CLOUD ->
            managedPrivacyAccepted && googleSignedIn && !googleSignInLoading

        OnboardingMode.BRING_YOUR_OWN_KEY,
        OnboardingMode.LOCAL -> true
    }
}
