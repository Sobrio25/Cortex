package com.aiagents.app.presentation.settings

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.local.SecurePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val errorReporter: AppErrorReporter
) : ViewModel() {
    private val _globalUsageAnalyticsEnabled = mutableStateOf(
        securePreferences.isGlobalUsageAnalyticsEnabled()
    )
    val globalUsageAnalyticsEnabled: State<Boolean> = _globalUsageAnalyticsEnabled
    private val _errorReportingEnabled = mutableStateOf(
        securePreferences.isErrorReportingEnabled()
    )
    val errorReportingEnabled: State<Boolean> = _errorReportingEnabled

    fun setGlobalUsageAnalyticsEnabled(enabled: Boolean) {
        securePreferences.setGlobalUsageAnalyticsEnabled(enabled)
        _globalUsageAnalyticsEnabled.value = enabled
    }

    fun setErrorReportingEnabled(enabled: Boolean) {
        securePreferences.setErrorReportingEnabled(enabled)
        errorReporter.setCollectionEnabled(enabled)
        _errorReportingEnabled.value = enabled
    }
}
