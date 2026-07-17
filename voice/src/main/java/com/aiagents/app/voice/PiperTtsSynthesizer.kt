package com.aiagents.app.voice

import android.content.Context
import com.aiagents.app.data.speech.OfflineTtsAudio
import com.aiagents.app.data.speech.VoiceCatalog
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PiperTtsSynthesizer {
    private val lock = Any()
    private var activeAssetId: String? = null
    private var engine: OfflineTts? = null

    suspend fun synthesize(
        context: Context,
        assetId: String,
        text: String,
        speed: Float
    ): Result<OfflineTtsAudio> = withContext(Dispatchers.IO) {
        runCatching {
            require(assetId == VoiceCatalog.PIPER_ALD_ID || assetId == VoiceCatalog.PIPER_CLAUDE_ID) {
                "La voz Piper seleccionada no existe"
            }
            require(text.isNotBlank()) { "No hay texto para reproducir" }
            check(VoiceAssetStore.isReady(context, assetId)) { "Descarga la voz Piper antes de usarla" }

            synchronized(lock) {
                val tts = engineFor(context, assetId)
                val generated = tts.generate(
                    text = text,
                    sid = 0,
                    speed = speed.coerceIn(0.65f, 1.5f)
                )
                check(generated.samples.isNotEmpty()) { "Piper no genero audio" }
                OfflineTtsAudio(generated.samples, generated.sampleRate)
            }
        }
    }

    fun releaseAsset(assetId: String? = null) {
        synchronized(lock) {
            if (assetId == null || activeAssetId == assetId) {
                engine?.release()
                engine = null
                activeAssetId = null
            }
        }
    }

    private fun engineFor(context: Context, assetId: String): OfflineTts {
        if (engine != null && activeAssetId == assetId) return checkNotNull(engine)
        releaseAsset()

        val directory = VoiceAssetStore.assetDirectory(context, assetId)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(directory, "model.onnx").absolutePath,
                    tokens = File(directory, "tokens.txt").absolutePath,
                    dataDir = File(directory, "espeak-ng-data").absolutePath,
                    lengthScale = 1f
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
        )
        return OfflineTts(config = config).also {
            engine = it
            activeAssetId = assetId
        }
    }
}
