package com.aiagents.app.data.speech

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.aiagents.app.BuildConfig
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class VoiceFeatureInstallState(
    val installed: Boolean,
    val installing: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val requiresConfirmation: Boolean = false,
    val requiresInstallPermission: Boolean = false,
    val sessionId: Int? = null
)

/** Delivers Cortex Voice Pack through Play or the signed sideload channel. */
@Singleton
class VoiceFeatureInstaller @Inject constructor(
    @ApplicationContext context: Context,
    private val externalInstaller: ExternalVoicePackInstaller
) {
    private val splitInstallManager: SplitInstallManager = SplitInstallManagerFactory.create(context)
    private val _state = MutableStateFlow(
        VoiceFeatureInstallState(
            installed = OnDemandVoiceFeatureLoader.MODULE_NAME in splitInstallManager.installedModules
        )
    )
    val state: StateFlow<VoiceFeatureInstallState> = if (BuildConfig.EXTERNAL_VOICE_PACK) {
        externalInstaller.state
    } else {
        _state.asStateFlow()
    }

    private val listener = SplitInstallStateUpdatedListener(::onStateChanged)
    private var pendingConfirmation: SplitInstallSessionState? = null

    init {
        if (!BuildConfig.EXTERNAL_VOICE_PACK) {
            splitInstallManager.registerListener(listener)
        }
    }

    fun requestInstall() {
        if (BuildConfig.EXTERNAL_VOICE_PACK) {
            externalInstaller.requestInstall()
            return
        }
        if (state.value.installed) return
        if (state.value.installing && !state.value.requiresConfirmation) return
        _state.value = _state.value.copy(installing = true, progress = 0f, error = null)
        val request = SplitInstallRequest.newBuilder()
            .addModule(OnDemandVoiceFeatureLoader.MODULE_NAME)
            .build()
        splitInstallManager.startInstall(request)
            .addOnSuccessListener { sessionId ->
                _state.value = _state.value.copy(sessionId = sessionId)
            }
            .addOnFailureListener { error ->
                _state.value = VoiceFeatureInstallState(
                    installed = false,
                    error = installErrorMessage(error)
                )
            }
    }

    fun requestConfirmation(
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean {
        if (BuildConfig.EXTERNAL_VOICE_PACK) return false
        val session = pendingConfirmation ?: return false
        return splitInstallManager.startConfirmationDialogForResult(session, launcher)
    }

    fun externalInstallPermissionIntent(): Intent? =
        if (BuildConfig.EXTERNAL_VOICE_PACK) externalInstaller.permissionIntent() else null

    fun resumeExternalInstall() {
        if (BuildConfig.EXTERNAL_VOICE_PACK) externalInstaller.resumeAfterPermission()
    }

    private fun onStateChanged(session: SplitInstallSessionState) {
        if (OnDemandVoiceFeatureLoader.MODULE_NAME !in session.moduleNames()) return
        val total = session.totalBytesToDownload().takeIf { it > 0L } ?: 1L
        val progress = (session.bytesDownloaded().toFloat() / total).coerceIn(0f, 1f)
        _state.value = when (session.status()) {
            SplitInstallSessionStatus.INSTALLED -> {
                pendingConfirmation = null
                VoiceFeatureInstallState(installed = true, progress = 1f, sessionId = session.sessionId())
            }
            SplitInstallSessionStatus.FAILED,
            SplitInstallSessionStatus.CANCELED -> {
                pendingConfirmation = null
                VoiceFeatureInstallState(
                    installed = false,
                    error = installErrorMessage(session.errorCode())
                )
            }
            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                pendingConfirmation = session
                VoiceFeatureInstallState(
                    installed = false,
                    installing = true,
                    progress = progress,
                    requiresConfirmation = true,
                    sessionId = session.sessionId()
                )
            }
            SplitInstallSessionStatus.DOWNLOADING,
            SplitInstallSessionStatus.PENDING,
            SplitInstallSessionStatus.DOWNLOADED,
            SplitInstallSessionStatus.INSTALLING -> VoiceFeatureInstallState(
                installed = false,
                installing = true,
                progress = progress,
                sessionId = session.sessionId()
            )
            else -> _state.value
        }
    }

    private fun installErrorMessage(error: Throwable): String {
        val code = (error as? SplitInstallException)?.errorCode
        return code?.let(::installErrorMessage)
            ?: error.message
            ?: "No se pudo iniciar la descarga del motor de voz"
    }

    private fun installErrorMessage(code: Int): String = when (code) {
        SplitInstallErrorCode.APP_NOT_OWNED ->
            "Esta copia no fue instalada por Google Play. Usa la version de Play o una build local de prueba para descargar modulos."
        SplitInstallErrorCode.NETWORK_ERROR ->
            "No se pudo descargar el motor de voz. Revisa tu conexion e intenta de nuevo."
        SplitInstallErrorCode.INSUFFICIENT_STORAGE ->
            "No hay espacio suficiente para instalar el motor de voz."
        SplitInstallErrorCode.MODULE_UNAVAILABLE ->
            "El modulo de voz no esta disponible para esta version de Cortex."
        SplitInstallErrorCode.PLAY_STORE_NOT_FOUND ->
            "Google Play es necesario para descargar el motor de voz opcional."
        else -> "No se pudo descargar el motor de voz (codigo $code)"
    }
}
