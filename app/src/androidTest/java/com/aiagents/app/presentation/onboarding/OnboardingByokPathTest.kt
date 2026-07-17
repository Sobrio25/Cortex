package com.aiagents.app.presentation.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiagents.app.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingByokPathTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun byokPathCanOpenProviderSetupWithoutGoogleOrManagedConsent() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var providerSetupOpened = false

        composeRule.setContent {
            var mode by remember { mutableStateOf(OnboardingMode.MANAGED_CLOUD) }
            MaterialTheme {
                InferenceSetupStep(
                    selectedMode = mode,
                    privacyAccepted = false,
                    googleSignedIn = false,
                    signInLoading = false,
                    signInError = null,
                    onModeSelected = { mode = it },
                    onPrivacyAcceptedChange = {},
                    onGoogleSignIn = {},
                    onConfigureProviders = { providerSetupOpened = true },
                    onConfigureLocalModels = {}
                )
            }
        }

        composeRule.onNodeWithText(resources.getString(R.string.onboarding_mode_byok_title))
            .performClick()
        composeRule.onNodeWithText(resources.getString(R.string.onboarding_byok_setup_title))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(resources.getString(R.string.google_sign_in_button))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText(resources.getString(R.string.managed_privacy_title))
            .assertCountEquals(0)
        composeRule.onNodeWithText(resources.getString(R.string.onboarding_configure_providers))
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle { assertTrue(providerSetupOpened) }
    }

    @Test
    fun switchingAwayFromManagedCloudRemovesItsErrorAndConsentUi() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val managedError = "No se pudo iniciar sesión"

        composeRule.setContent {
            var mode by remember { mutableStateOf(OnboardingMode.MANAGED_CLOUD) }
            MaterialTheme {
                InferenceSetupStep(
                    selectedMode = mode,
                    privacyAccepted = false,
                    googleSignedIn = false,
                    signInLoading = false,
                    signInError = managedError,
                    onModeSelected = { mode = it },
                    onPrivacyAcceptedChange = {},
                    onGoogleSignIn = {},
                    onConfigureProviders = {},
                    onConfigureLocalModels = {}
                )
            }
        }

        composeRule.onNodeWithText(managedError).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(resources.getString(R.string.onboarding_mode_byok_title))
            .performScrollTo()
            .performClick()

        composeRule.onAllNodesWithText(managedError).assertCountEquals(0)
        composeRule.onAllNodesWithText(resources.getString(R.string.managed_privacy_title))
            .assertCountEquals(0)
    }
}
