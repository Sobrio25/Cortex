package com.aiagents.app.presentation.settings

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagents.app.data.diagnostics.AppErrorReporter
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

sealed interface DeleteAccountState {
    data object Idle : DeleteAccountState
    data object Deleting : DeleteAccountState
    data object Deleted : DeleteAccountState
    data class Error(val message: String) : DeleteAccountState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val errorReporter: AppErrorReporter,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {
    private val _globalUsageAnalyticsEnabled = mutableStateOf(
        securePreferences.isGlobalUsageAnalyticsEnabled()
    )
    val globalUsageAnalyticsEnabled: State<Boolean> = _globalUsageAnalyticsEnabled
    private val _errorReportingEnabled = mutableStateOf(
        securePreferences.isErrorReportingEnabled()
    )
    val errorReportingEnabled: State<Boolean> = _errorReportingEnabled
    private val _deleteAccountState = mutableStateOf<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteAccountState: State<DeleteAccountState> = _deleteAccountState

    fun setGlobalUsageAnalyticsEnabled(enabled: Boolean) {
        securePreferences.setGlobalUsageAnalyticsEnabled(enabled)
        _globalUsageAnalyticsEnabled.value = enabled
    }

    fun setErrorReportingEnabled(enabled: Boolean) {
        securePreferences.setErrorReportingEnabled(enabled)
        errorReporter.setCollectionEnabled(enabled)
        _errorReportingEnabled.value = enabled
    }

    fun deleteAccount() {
        if (_deleteAccountState.value == DeleteAccountState.Deleting) return
        _deleteAccountState.value = DeleteAccountState.Deleting
        viewModelScope.launch {
            subscriptionRepository.deleteAccount().fold(
                onSuccess = { _deleteAccountState.value = DeleteAccountState.Deleted },
                onFailure = { error ->
                    _deleteAccountState.value = DeleteAccountState.Error(
                        error.message ?: "No se pudo eliminar la cuenta"
                    )
                }
            )
        }
    }

    fun resetDeleteAccountState() {
        _deleteAccountState.value = DeleteAccountState.Idle
    }
}
