package com.aiagents.app.presentation.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.aiagents.app.R
import org.junit.Rule
import org.junit.Test

class OnboardingModeStepTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun localPathDoesNotRequireGoogleOrManagedConsent() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

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
                    onConfigureProviders = {},
                    onConfigureLocalModels = {}
                )
            }
        }

        composeRule.onNodeWithText(resources.getString(R.string.onboarding_mode_local_title))
            .performClick()
        composeRule.onNodeWithText(resources.getString(R.string.onboarding_local_setup_title))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(resources.getString(R.string.google_sign_in_button))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText(resources.getString(R.string.managed_privacy_title))
            .assertCountEquals(0)
    }
}
