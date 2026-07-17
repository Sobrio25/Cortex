package com.aiagents.app.voice

import android.content.Context
import com.aiagents.app.data.speech.OfflineTtsAudio
import com.aiagents.app.data.speech.OnDemandVoiceFeature
import com.aiagents.app.data.speech.VoiceCatalog
import com.aiagents.app.domain.service.STTService

/** Entry point loaded reflectively by the base APK once this dynamic feature is installed. */
class SherpaVoiceFeature : OnDemandVoiceFeature {
    override fun createOfflineService(context: Context, language: String): STTService? {
        if (!isAssetReady(context, VoiceCatalog.WHISPER_TINY_ID)) return null
        return SherpaOnnxSTTService(
            context = context,
            modelDir = VoiceAssetStore.assetDirectory(
                context,
                VoiceCatalog.WHISPER_TINY_ID
            ).absolutePath,
            language = language
        )
    }

    override fun isAssetReady(context: Context, assetId: String): Boolean =
        VoiceAssetStore.isReady(context, assetId)

    override suspend fun downloadAsset(
        context: Context,
        assetId: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = VoiceAssetStore.download(context, assetId, onProgress)

    override fun deleteAsset(context: Context, assetId: String): Result<Unit> {
        PiperTtsSynthesizer.releaseAsset(assetId)
        return VoiceAssetStore.delete(context, assetId)
    }

    override suspend fun synthesize(
        context: Context,
        assetId: String,
        text: String,
        speed: Float
    ): Result<OfflineTtsAudio> = PiperTtsSynthesizer.synthesize(
        context = context,
        assetId = assetId,
        text = text,
        speed = speed
    )
}
