package com.aiagents.app.data.speech

import android.content.Context
import android.util.Log
import com.aiagents.app.domain.service.STTConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class WhisperCloudSTTService(
    context: Context,
    private val apiKey: String,
    private val provider: STTConfig.CloudSTTProvider,
    private val remoteEndpointUrl: String = "",
    private val remoteModel: String = "whisper-1",
    private val selfHostedVoiceApi: SelfHostedVoiceApi? = null
) : BaseSTTService(context) {

    private var requestedLanguage: String = "auto"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    override suspend fun startListening(language: String) {
        requestedLanguage = language
        _transcription.value = ""
        startRecordingSession { audioData ->
            val result = transcribeAudio(audioData)
            _transcription.value = result.fold(
                onSuccess = { it.trim() },
                onFailure = { error ->
                    "Error: ${error.message ?: "No se pudo transcribir el audio"}"
                }
            )
        }
    }
    
    override suspend fun transcribeAudio(audioData: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        // Disk write failures (full storage, etc.) must surface as a transcription error instead of
        // crashing the recording coroutine.
        runCatching { saveTempWavFile(audioData) }.fold(
            onSuccess = { tempFile ->
                try {
                    when (provider) {
                        STTConfig.CloudSTTProvider.WHISPER_API -> transcribeWithOpenAI(tempFile)
                        STTConfig.CloudSTTProvider.SELF_HOSTED -> {
                            selfHostedVoiceApi?.transcribe(
                                audioFile = tempFile,
                                config = RemoteSttConfig(
                                    endpointUrl = remoteEndpointUrl,
                                    model = remoteModel,
                                    apiKey = apiKey
                                ),
                                language = requestedLanguage
                            ) ?: Result.failure(
                                IllegalStateException("Cliente de voz autohosted no disponible")
                            )
                        }
                        STTConfig.CloudSTTProvider.ASSEMBLY_AI -> transcribeWithAssemblyAI(tempFile)
                        STTConfig.CloudSTTProvider.DEEPGRAM -> transcribeWithDeepgram(tempFile)
                        STTConfig.CloudSTTProvider.GOOGLE_SPEECH -> transcribeWithGoogle(tempFile)
                        else -> Result.failure(IllegalArgumentException("Proveedor no soportado: $provider"))
                    }
                } finally {
                    tempFile.delete()
                }
            },
            onFailure = { error ->
                Log.e("WhisperCloudSTT", "No se pudo guardar el audio temporal", error)
                Result.failure(error)
            }
        )
    }
    
    /**
     * OpenAI Whisper API
     * Gratis: $0 (requiere API key con créditos)
     * Precio: $0.006/minuto
     * Free tier: No tiene tier gratuito oficial, pero ofrece $5 iniciales
     */
    private fun transcribeWithOpenAI(audioFile: File): Result<String> {
        return try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("model", "whisper-1")
                .apply {
                    val languageCode = requestedLanguage.substringBefore('-')
                    if (languageCode.isNotBlank() && languageCode != "auto") {
                        addFormDataPart("language", languageCode)
                    }
                }
                .build()
            
            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val text = json.getString("text")
                Result.success(text)
            } else {
                Result.failure(Exception("API Error: ${response.code} - $responseBody"))
            }
        } catch (e: Exception) {
            Log.e("WhisperCloudSTT", "Error OpenAI", e)
            Result.failure(e)
        }
    }
    
    /**
     * AssemblyAI
     * Free tier: 100 horas/mes gratuitas
     * Web: https://www.assemblyai.com/
     * No requiere tarjeta para tier gratuito
     */
    private fun transcribeWithAssemblyAI(audioFile: File): Result<String> {
        return try {
            // Paso 1: Subir archivo
            val uploadRequest = Request.Builder()
                .url("https://api.assemblyai.com/v2/upload")
                .header("authorization", apiKey)
                .post(audioFile.asRequestBody("audio/wav".toMediaType()))
                .build()

            val uploadResponse = client.newCall(uploadRequest).execute()
            if (!uploadResponse.isSuccessful) {
                return Result.failure(
                    Exception(
                        "AssemblyAI upload Error: ${uploadResponse.code} - " +
                            uploadResponse.body?.string().orEmpty()
                    )
                )
            }
            val uploadUrl = JSONObject(uploadResponse.body?.string() ?: "")
                .getString("upload_url")

            // Paso 2: Iniciar transcripción (AssemblyAI espera un cuerpo JSON)
            val transcriptBody = JSONObject().apply {
                put("audio_url", uploadUrl)
                put("language_code", "es")
            }
            val transcriptRequest = Request.Builder()
                .url("https://api.assemblyai.com/v2/transcript")
                .header("authorization", apiKey)
                .header("content-type", "application/json")
                .post(transcriptBody.toString().toRequestBody())
                .build()

            val transcriptResponse = client.newCall(transcriptRequest).execute()
            if (!transcriptResponse.isSuccessful) {
                return Result.failure(
                    Exception(
                        "AssemblyAI transcript Error: ${transcriptResponse.code} - " +
                            transcriptResponse.body?.string().orEmpty()
                    )
                )
            }
            val transcriptId = JSONObject(transcriptResponse.body?.string() ?: "")
                .getString("id")

            // Paso 3: Polling hasta completar
            var completed = false
            var text = ""
            var attempts = 0

            while (!completed && attempts < 60) {
                Thread.sleep(1000)
                attempts++

                val checkRequest = Request.Builder()
                    .url("https://api.assemblyai.com/v2/transcript/$transcriptId")
                    .header("authorization", apiKey)
                    .get()
                    .build()

                val checkResponse = client.newCall(checkRequest).execute()
                val json = JSONObject(checkResponse.body?.string() ?: "")

                when (json.getString("status")) {
                    "completed" -> {
                        completed = true
                        text = json.getString("text")
                    }
                    "error" -> return Result.failure(Exception("Transcription error"))
                }
            }

            if (!completed) {
                return Result.failure(
                    Exception("La transcripcion de AssemblyAI supero el tiempo limite")
                )
            }
            Result.success(text)
        } catch (e: Exception) {
            Log.e("WhisperCloudSTT", "Error AssemblyAI", e)
            Result.failure(e)
        }
    }
    
    /**
     * Deepgram
     * Free tier: $200 crédito inicial (aprox ~45 horas)
     * Web: https://deepgram.com/
     * Modelo: Nova-2 (muy preciso)
     */
    private fun transcribeWithDeepgram(audioFile: File): Result<String> {
        return try {
            val request = Request.Builder()
                .url("https://api.deepgram.com/v1/listen?language=es&model=nova-2")
                .header("Authorization", "Token $apiKey")
                .header("Content-Type", "audio/wav")
                .post(audioFile.asRequestBody("audio/wav".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val results = json.getJSONObject("results")
                val channels = results.getJSONArray("channels")
                val alternatives = channels.getJSONObject(0).getJSONArray("alternatives")
                val text = alternatives.getJSONObject(0).getString("transcript")
                Result.success(text)
            } else {
                Result.failure(Exception("Deepgram Error: ${response.code} - $responseBody"))
            }
        } catch (e: Exception) {
            Log.e("WhisperCloudSTT", "Error Deepgram", e)
            Result.failure(e)
        }
    }
    
    /**
     * Google Cloud Speech-to-Text
     * Free tier: 60 minutos/mes gratuitos
     * Requiere: Google Cloud account con billing habilitado (pero no cobra si estás en free tier)
     */
    private fun transcribeWithGoogle(audioFile: File): Result<String> {
        return try {
            // Convertir audio a Base64
            val audioBytes = audioFile.readBytes()
            val base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
            
            val jsonBody = JSONObject().apply {
                put("config", JSONObject().apply {
                    put("encoding", "LINEAR16")
                    put("sampleRateHertz", 16000)
                    put("languageCode", "es-ES")
                    put("enableAutomaticPunctuation", true)
                })
                put("audio", JSONObject().apply {
                    put("content", base64Audio)
                })
            }
            
            val request = Request.Builder()
                .url("https://speech.googleapis.com/v1/speech:recognize?key=$apiKey")
                .header("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody())
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val results = json.getJSONArray("results")
                val alternatives = results.getJSONObject(0).getJSONArray("alternatives")
                val text = alternatives.getJSONObject(0).getString("transcript")
                Result.success(text)
            } else {
                Result.failure(Exception("Google Error: ${response.code} - $responseBody"))
            }
        } catch (e: Exception) {
            Log.e("WhisperCloudSTT", "Error Google", e)
            Result.failure(e)
        }
    }
    
    private fun String.toRequestBody() = 
        okhttp3.RequestBody.create("application/json".toMediaType(), this)
}
