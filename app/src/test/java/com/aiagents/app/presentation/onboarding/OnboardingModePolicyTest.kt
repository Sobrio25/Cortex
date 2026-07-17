package com.aiagents.app.presentation.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingModePolicyTest {
    @Test
    fun `managed cloud requires consent and Google account`() {
        assertFalse(
            OnboardingModePolicy.canContinue(
                mode = OnboardingMode.MANAGED_CLOUD,
                managedPrivacyAccepted = true,
                googleSignedIn = false,
                googleSignInLoading = false
            )
        )
        assertTrue(
            OnboardingModePolicy.canContinue(
                mode = OnboardingMode.MANAGED_CLOUD,
                managedPrivacyAccepted = true,
                googleSignedIn = true,
                googleSignInLoading = false
            )
        )
    }

    @Test
    fun `managed cloud cannot continue while sign in is running`() {
        assertFalse(
            OnboardingModePolicy.canContinue(
                mode = OnboardingMode.MANAGED_CLOUD,
                managedPrivacyAccepted = true,
                googleSignedIn = true,
                googleSignInLoading = true
            )
        )
    }

    @Test
    fun `BYOK and local never require Google or managed consent`() {
        listOf(OnboardingMode.BRING_YOUR_OWN_KEY, OnboardingMode.LOCAL).forEach { mode ->
            assertTrue(
                OnboardingModePolicy.canContinue(
                    mode = mode,
                    managedPrivacyAccepted = false,
                    googleSignedIn = false,
                    googleSignInLoading = false
                )
            )
        }
    }
}
