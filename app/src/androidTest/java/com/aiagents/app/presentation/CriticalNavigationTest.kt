package com.aiagents.app.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the production route contract without starting Hilt screens or external accounts.
 * This catches broken route names and nested back-stack behavior in a deterministic test.
 */
@RunWith(AndroidJUnit4::class)
class CriticalNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chatToSettingsToProvidersAndBackKeepsExpectedBackStack() {
        composeRule.setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(navController, startDestination = Screen.Chat.route) {
                    composable(Screen.Chat.route) {
                        Button(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Text(CHAT_TO_SETTINGS)
                        }
                    }
                    composable(Screen.Settings.route) {
                        Column {
                            Text(SETTINGS_DESTINATION)
                            Button(onClick = { navController.navigate(Screen.Providers.route) }) {
                                Text(SETTINGS_TO_PROVIDERS)
                            }
                        }
                    }
                    composable(Screen.Providers.route) {
                        Column {
                            Text(PROVIDERS_DESTINATION)
                            Button(onClick = { navController.popBackStack() }) {
                                Text(BACK_TO_SETTINGS)
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText(CHAT_TO_SETTINGS).performClick()
        composeRule.onNodeWithText(SETTINGS_DESTINATION).assertIsDisplayed()
        composeRule.onNodeWithText(SETTINGS_TO_PROVIDERS).performClick()
        composeRule.onNodeWithText(PROVIDERS_DESTINATION).assertIsDisplayed()
        composeRule.onNodeWithText(BACK_TO_SETTINGS).performClick()
        composeRule.onNodeWithText(SETTINGS_DESTINATION).assertIsDisplayed()
    }

    private companion object {
        const val CHAT_TO_SETTINGS = "Abrir ajustes"
        const val SETTINGS_DESTINATION = "Destino ajustes"
        const val SETTINGS_TO_PROVIDERS = "Abrir proveedores"
        const val PROVIDERS_DESTINATION = "Destino proveedores"
        const val BACK_TO_SETTINGS = "Volver a ajustes"
    }
}
