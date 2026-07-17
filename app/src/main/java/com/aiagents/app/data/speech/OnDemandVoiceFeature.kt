package com.aiagents.app.data.speech

import android.content.Context
import android.util.Log
import com.aiagents.app.domain.service.STTService

/**
 * Contract implemented by the optional :voice dynamic feature. The base APK deliberately keeps
 * this contract free of native STT types so it can start without downloading the offline engine.
 */
interface OnDemandVoiceFeature {
    fun createOfflineService(context: Context, language: String): STTService?
    fun isAssetReady(context: Context, assetId: String): Boolean
    suspend fun downloadAsset(
        context: Context,
        assetId: String,
        onProgress: (Float) -> Unit
    ): Result<Unit>
    fun deleteAsset(context: Context, assetId: String): Result<Unit>
    suspend fun synthesize(
        context: Context,
        assetId: String,
        text: String,
        speed: Float
    ): Result<OfflineTtsAudio>
}

/** Loads the optional feature only after Play Feature Delivery has installed it. */
object OnDemandVoiceFeatureLoader {
    const val MODULE_NAME = "voice"
    private const val FACTORY_CLASS = "com.aiagents.app.voice.SherpaVoiceFeature"
    private const val TAG = "OnDemandVoiceFeature"

    @Volatile
    private var cachedFeature: OnDemandVoiceFeature? = null

    private fun featureOrNull(): OnDemandVoiceFeature? = cachedFeature ?: runCatching {
        Class.forName(FACTORY_CLASS)
            .getDeclaredConstructor()
            .newInstance() as OnDemandVoiceFeature
    }.onFailure {
        Log.d(TAG, "Offline voice feature is not installed yet")
    }.getOrNull()?.also { cachedFeature = it }

    fun isInstalledAndReady(context: Context): Boolean =
        isAssetReady(context, VoiceCatalog.WHISPER_TINY_ID)

    fun isAssetReady(context: Context, assetId: String): Boolean =
        featureOrNull()?.isAssetReady(context, assetId) == true

    fun isFeatureAvailable(): Boolean = featureOrNull() != null

    fun createOfflineService(context: Context, language: String): STTService? =
        featureOrNull()?.createOfflineService(context, language)

    suspend fun downloadDefaultModel(
        context: Context,
        onProgress: (Float) -> Unit
    ): Result<Unit> = downloadAsset(context, VoiceCatalog.WHISPER_TINY_ID, onProgress)

    suspend fun downloadAsset(
        context: Context,
        assetId: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = featureOrNull()?.downloadAsset(context, assetId, onProgress)
        ?: Result.failure(
            IllegalStateException("El motor de voz sin conexión todavía no está instalado")
        )

    fun deleteAsset(context: Context, assetId: String): Result<Unit> =
        featureOrNull()?.deleteAsset(context, assetId)
            ?: Result.failure(
                IllegalStateException("El motor de voz sin conexión todavía no está instalado")
            )

    suspend fun synthesize(
        context: Context,
        assetId: String,
        text: String,
        speed: Float = 1f
    ): Result<OfflineTtsAudio> = featureOrNull()?.synthesize(context, assetId, text, speed)
        ?: Result.failure(
            IllegalStateException("El motor de voz sin conexión todavía no está instalado")
        )
}
