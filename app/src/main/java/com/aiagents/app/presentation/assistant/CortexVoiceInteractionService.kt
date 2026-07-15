package com.aiagents.app.presentation.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Lightweight process kept by Android while Cortex is the selected system assistant.
 * The actual UI and application work live in [CortexVoiceInteractionSessionService].
 */
class CortexVoiceInteractionService : VoiceInteractionService()

/** Creates the system-owned interaction session for each assistant invocation. */
class CortexVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        CortexVoiceInteractionSession(this)
}

/**
 * Uses Android's assistant-activity layer so Cortex appears above the current app without
 * requesting overlay permission. The session itself has no window; Compose owns the glass UI.
 */
private class CortexVoiceInteractionSession(context: Context) :
    VoiceInteractionSession(context) {

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        setUiEnabled(false)
        super.onPrepareShow(args, showFlags)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        startAssistantActivity(
            Intent(context, CortexAssistantActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }
}

/**
 * Android requires a recognition-service component in assistant metadata on older releases.
 * Cortex performs recognition inside its assistant UI, so this compatibility service never
 * captures audio and is deliberately not selectable as the device's general recognizer.
 */
class CortexRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        listener.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: Callback) = Unit

    override fun onCancel(listener: Callback) = Unit
}
