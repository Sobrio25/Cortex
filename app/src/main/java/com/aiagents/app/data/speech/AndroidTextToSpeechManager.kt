package com.aiagents.app.data.speech

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.aiagents.app.data.local.VoicePreferences
import com.aiagents.app.data.speech.GROQ_TTS_ENDPOINT
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes assistant speech to Google TTS, downloaded Piper voices, or a user-owned server.
 * No voice or model is selected automatically.
 */
@Singleton
class AndroidTextToSpeechManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: VoicePreferences,
    private val selfHostedVoiceApi: SelfHostedVoiceApi
) {
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _offlineVoiceAvailable = MutableStateFlow(false)
    val offlineVoiceAvailable: StateFlow<Boolean> = _offlineVoiceAvailable.asStateFlow()

    private val _googleVoices = MutableStateFlow<List<GoogleTtsVoice>>(emptyList())
    val googleVoices: StateFlow<List<GoogleTtsVoice>> = _googleVoices.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playbackTracker = SpeechPlaybackTracker()
    private val requestCounter = AtomicLong(0L)

    private var engine: TextToSpeech? = null
    private var selectedLocale: Locale = Locale.getDefault()
    private var activeRequest: SpeechPlaybackRequest? = null
    private var piperJob: Job? = null
    private var piperTrack: AudioTrack? = null
    private var remoteJob: Job? = null
    private var remotePlayer: MediaPlayer? = null
    private var remoteAudioFile: File? = null
    private var remotePcmTrack: AudioTrack? = null

    init {
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                attachGooglePlaybackListener()
                refreshVoice(selectedLocale)
                _isReady.value = true
            } else {
                _error.value = "No se pudo iniciar Google TTS"
            }
        }
    }

    @Synchronized
    fun speak(text: String, locale: Locale = Locale.getDefault()) {
        if (text.isBlank()) return
        when (val mode = preferences.ttsMode.value) {
            AssistantTtsMode.NONE -> {
                _error.value = "Elige una voz en Ajustes > Voz"
                _isSpeaking.value = false
            }
            AssistantTtsMode.GOOGLE -> speakWithGoogle(text, locale)
            AssistantTtsMode.PIPER_ALD,
            AssistantTtsMode.PIPER_CLAUDE -> speakWithPiper(text, checkNotNull(mode.assetId))
            AssistantTtsMode.REMOTE_SERVER -> speakWithRemote(text)
            AssistantTtsMode.GROQ -> speakWithGroq(text)
        }
    }

    fun previewGroqVoice() {
        speakWithGroq("Hola, soy Cortex. Esta es una prueba de la voz de Groq.")
    }

    fun previewGoogleVoice(voiceId: String) {
        val tts = engine
        if (tts == null || !_isReady.value) {
            _error.value = "Google TTS todavia se esta iniciando"
            return
        }
        val voice = tts.voices.orEmpty().firstOrNull { it.name == voiceId }
        if (voice == null || !voice.isInstalledLocally()) {
            _error.value = "Descarga esa voz desde Google TTS antes de probarla"
            return
        }
        speakWithGoogleVoice(
            text = "Hola, soy Cortex. Esta es una prueba de voz.",
            locale = voice.locale,
            voice = voice
        )
    }

    fun previewPiperVoice(mode: AssistantTtsMode) {
        require(mode == AssistantTtsMode.PIPER_ALD || mode == AssistantTtsMode.PIPER_CLAUDE)
        speakWithPiper(
            text = "Hola, soy Cortex. Esta es una prueba de voz en espanol de Mexico.",
            assetId = checkNotNull(mode.assetId)
        )
    }

    fun previewRemoteVoice() {
        speakWithRemote("Hola, soy Cortex. Esta es una prueba de la voz del servidor.")
    }

    fun selectGoogleVoice(voiceId: String): Boolean {
        val tts = engine ?: return false
        val voice = tts.voices.orEmpty().firstOrNull { it.name == voiceId }
        if (voice == null || !voice.isInstalledLocally()) {
            _error.value = "Descarga esa voz desde Google TTS antes de seleccionarla"
            return false
        }
        tts.voice = voice
        selectedLocale = voice.locale
        preferences.setGoogleVoiceId(voice.name)
        _offlineVoiceAvailable.value = true
        _error.value = null
        return true
    }

    fun stop() {
        requestCounter.incrementAndGet()
        piperJob?.cancel()
        piperJob = null
        remoteJob?.cancel()
        remoteJob = null
        piperTrack?.runCatching {
            pause()
            flush()
            release()
        }
        piperTrack = null
        remotePlayer?.runCatching {
            stop()
            release()
        }
        remotePlayer = null
        remotePcmTrack?.runCatching {
            pause()
            flush()
            release()
        }
        remotePcmTrack = null
        remoteAudioFile?.delete()
        remoteAudioFile = null
        playbackTracker.cancel()
        activeRequest = null
        engine?.stop()
        _isSpeaking.value = false
    }

    fun createInstallVoiceIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)

    fun refreshVoice(locale: Locale = Locale.getDefault()) {
        val tts = engine ?: return
        selectedLocale = locale
        val voices = tts.voices.orEmpty()
            .filter { sameLanguage(it.locale, locale) }
            .sortedWith(
                compareBy<Voice> { it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
                    .thenBy { it.name }
            )
        _googleVoices.value = voices.mapIndexed { index, voice ->
            GoogleTtsVoice(
                id = voice.name,
                displayName = googleVoiceLabel(voice, index),
                languageTag = voice.locale.toLanguageTag(),
                quality = voice.quality,
                latency = voice.latency,
                requiresNetwork = voice.isNetworkConnectionRequired,
                installed = voice.isInstalledLocally()
            )
        }

        val selectedId = preferences.googleVoiceId.value
        val selected = voices.firstOrNull { it.name == selectedId && it.isInstalledLocally() }
        if (selected != null) {
            tts.voice = selected
            selectedLocale = selected.locale
        }
        _offlineVoiceAvailable.value = when (val mode = preferences.ttsMode.value) {
            AssistantTtsMode.GOOGLE -> selected != null
            AssistantTtsMode.PIPER_ALD,
            AssistantTtsMode.PIPER_CLAUDE ->
                mode.assetId?.let { OnDemandVoiceFeatureLoader.isAssetReady(context, it) } == true
            AssistantTtsMode.REMOTE_SERVER ->
                SelfHostedVoiceApi.isConfigured(preferences.remoteTtsConfig.value)
            AssistantTtsMode.GROQ -> preferences.groqTtsConfig.value.isConfigured
            AssistantTtsMode.NONE -> false
        }
        _isReady.value = true
    }

    fun dismissError() {
        _error.value = null
    }

    @Synchronized
    private fun speakWithGoogle(text: String, locale: Locale) {
        val tts = engine
        if (tts == null || !_isReady.value) {
            _error.value = "Google TTS todavia se esta iniciando"
            return
        }
        val voiceId = preferences.googleVoiceId.value
        val voice = tts.voices.orEmpty().firstOrNull { it.name == voiceId && it.isInstalledLocally() }
        if (voice == null) {
            _error.value = "Elige y descarga una voz de Google TTS"
            return
        }
        if (!sameLanguage(voice.locale, locale)) {
            _error.value = "La voz elegida no admite ${locale.displayLanguage}"
            return
        }
        speakWithGoogleVoice(text, locale, voice)
    }

    private fun speakWithGoogleVoice(text: String, locale: Locale, voice: Voice) {
        val tts = engine
        if (tts == null || !_isReady.value) {
            _error.value = "Google TTS todavia se esta iniciando"
            return
        }
        if (!sameLanguage(voice.locale, locale)) {
            _error.value = "La voz elegida no admite ${locale.displayLanguage}"
            return
        }
        stop()
        tts.voice = voice
        selectedLocale = voice.locale

        val maxLength = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceAtLeast(500)
        val chunks = SpokenTextFormatter.chunk(text, maxLength)
        if (chunks.isEmpty()) return

        val request = playbackTracker.start(chunks)
        activeRequest = request
        _error.value = null
        for ((index, chunk) in request.chunks.withIndex()) {
            val result = tts.speak(
                chunk,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                request.utteranceIds[index]
            )
            if (result == TextToSpeech.ERROR) {
                tts.stop()
                handlePlaybackError(request.utteranceIds[index])
                return
            }
        }
    }

    private fun speakWithPiper(text: String, assetId: String) {
        if (!OnDemandVoiceFeatureLoader.isAssetReady(context, assetId)) {
            _error.value = "Descarga la voz Piper antes de usarla"
            return
        }
        stop()
        val requestId = requestCounter.incrementAndGet()
        _isSpeaking.value = true
        _error.value = null
        piperJob = scope.launch {
            try {
                streamPiperAudio(
                    text = SpokenTextFormatter.clean(text),
                    assetId = assetId,
                    requestId = requestId
                )
            } catch (_: CancellationException) {
                if (requestId == requestCounter.get()) finishPiperPlayback(requestId)
            } catch (error: Throwable) {
                if (requestId == requestCounter.get()) {
                    finishPiperPlayback(requestId)
                    _error.value = error.message ?: "No se pudo generar la voz Piper"
                }
            }
        }
    }

    /**
     * Piper's native API returns one complete waveform per call. To avoid waiting for the whole
     * response, synthesize sentence-sized pieces ahead of playback and feed them to a streaming
     * track. One buffered piece keeps synthesis and playback overlapped without retaining an
     * entire response in memory.
     */
    private suspend fun streamPiperAudio(text: String, assetId: String, requestId: Long) {
        val textChunks = SpokenTextFormatter.chunk(text, PIPER_STREAM_CHUNK_MAX_CHARS)
        if (textChunks.isEmpty()) {
            finishPiperPlayback(requestId)
            return
        }

        coroutineScope {
            val audioChunks = Channel<OfflineTtsAudio>(capacity = PIPER_BUFFERED_CHUNKS)
            val producer = launch(Dispatchers.Default) {
                try {
                    for (chunk in textChunks) {
                        if (requestId != requestCounter.get()) {
                            throw CancellationException("TTS cancelado")
                        }
                        val audio = OnDemandVoiceFeatureLoader.synthesize(
                            context = context,
                            assetId = assetId,
                            text = chunk,
                            speed = 1f
                        ).getOrElse { throw it }
                        if (audio.samples.isNotEmpty()) audioChunks.send(audio)
                    }
                    audioChunks.close()
                } catch (error: Throwable) {
                    audioChunks.close(error)
                }
            }

            var track: AudioTrack? = null
            var sampleRate = 0
            var expectedFrames = 0L
            try {
                for (audio in audioChunks) {
                    if (requestId != requestCounter.get()) {
                        throw CancellationException("TTS cancelado")
                    }
                    val activeTrack = track ?: createPiperStreamTrack(audio.sampleRate).also {
                        track = it
                        piperTrack = it
                        sampleRate = audio.sampleRate
                        it.play()
                    }
                    check(audio.sampleRate == sampleRate) {
                        "Piper cambio la frecuencia de muestreo durante la reproduccion"
                    }
                    writePiperSamples(activeTrack, audio.samples, requestId)
                    expectedFrames += audio.samples.size
                }
                val completedTrack = track
                if (completedTrack != null && requestId == requestCounter.get()) {
                    waitForPiperDrain(completedTrack, expectedFrames, sampleRate, requestId)
                }
            } finally {
                producer.cancel()
                audioChunks.cancel()
            }
        }
        if (requestId == requestCounter.get()) finishPiperPlayback(requestId)
    }

    private fun createPiperStreamTrack(sampleRate: Int): AudioTrack {
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        check(minBufferBytes > 0) { "Android no admite audio PCM float a $sampleRate Hz" }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBufferBytes, PIPER_MIN_BUFFER_BYTES))
            .build()
            .also { track ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val lowLatencyFrames = (sampleRate / 50).coerceAtLeast(1)
                    track.setStartThresholdInFrames(
                        lowLatencyFrames.coerceAtMost(track.bufferSizeInFrames)
                    )
                }
            }
    }

    private suspend fun writePiperSamples(
        track: AudioTrack,
        samples: FloatArray,
        requestId: Long
    ) {
        var offset = 0
        while (offset < samples.size) {
            if (requestId != requestCounter.get()) throw CancellationException("TTS cancelado")
            val written = kotlinx.coroutines.withContext(Dispatchers.IO) {
                track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
            }
            check(written >= 0) { "AudioTrack no pudo escribir PCM de Piper: $written" }
            check(written > 0) { "AudioTrack no acepto los datos PCM de Piper" }
            offset += written
        }
    }

    private suspend fun waitForPiperDrain(
        track: AudioTrack,
        expectedFrames: Long,
        sampleRate: Int,
        requestId: Long
    ) {
        val maximumDrainMs = expectedFrames * 1_000L / sampleRate + PIPER_DRAIN_GRACE_MS
        val drainStartedAt = System.currentTimeMillis()
        while (requestId == requestCounter.get() &&
            track.playedFrames() < expectedFrames &&
            System.currentTimeMillis() - drainStartedAt < maximumDrainMs
        ) {
            delay(20L)
        }
    }

    private fun speakWithGroq(text: String) {
        val config = preferences.groqTtsConfig.value
        if (!config.isConfigured) {
            _error.value = "Configura la API key y la voz de Groq en Ajustes > Voz"
            return
        }
        stop()
        val requestId = requestCounter.incrementAndGet()
        _isSpeaking.value = true
        _error.value = null
        remoteJob = scope.launch {
            selfHostedVoiceApi.synthesize(
                text = text,
                config = RemoteTtsConfig(
                    endpointUrl = GROQ_TTS_ENDPOINT,
                    model = config.model,
                    voice = config.voice,
                    apiKey = config.apiKey,
                    apiFlavor = RemoteTtsApiFlavor.OPENAI,
                    audioMode = RemoteTtsAudioMode.BUFFERED_WAV
                )
            ).onSuccess { audio ->
                if (requestId != requestCounter.get()) return@onSuccess
                playRemoteAudio(audio, requestId)
            }.onFailure { error ->
                if (requestId != requestCounter.get()) return@onFailure
                _isSpeaking.value = false
                _error.value = error.message ?: "No se pudo generar la voz de Groq"
            }
        }
    }

    private fun speakWithRemote(text: String) {
        val config = preferences.remoteTtsConfig.value
        val validationError = SelfHostedVoiceApi.endpointValidationError(config.endpointUrl)
        if (validationError != null ||
            config.model.isBlank() ||
            Qwen3TtsVoiceSkill.requiresVoice(config) && config.voice.isBlank()
        ) {
            _error.value = validationError ?: "Completa los datos requeridos del servidor TTS"
            return
        }
        stop()
        val requestId = requestCounter.incrementAndGet()
        _isSpeaking.value = true
        _error.value = null
        remoteJob = scope.launch {
            if (config.audioMode == RemoteTtsAudioMode.STREAMING_PCM) {
                streamRemotePcm(text, config, requestId)
                return@launch
            }
            selfHostedVoiceApi.synthesize(
                text = text,
                config = config
            ).onSuccess { audio ->
                if (requestId != requestCounter.get()) return@onSuccess
                playRemoteAudio(audio, requestId)
            }.onFailure { error ->
                if (requestId != requestCounter.get()) return@onFailure
                _isSpeaking.value = false
                _error.value = error.message ?: "No se pudo generar la voz remota"
            }
        }
    }

    private suspend fun streamRemotePcm(
        text: String,
        config: RemoteTtsConfig,
        requestId: Long
    ) {
        var trailingByte: Byte? = null
        val result = selfHostedVoiceApi.streamPcm(text, config) { chunk ->
            if (requestId != requestCounter.get()) throw CancellationException("TTS cancelado")
            val pcm = trailingByte?.let { previous ->
                ByteArray(chunk.size + 1).also { combined ->
                    combined[0] = previous
                    chunk.copyInto(combined, destinationOffset = 1)
                }
            } ?: chunk
            val playableBytes = pcm.size - pcm.size % PCM16_MONO_BYTES_PER_FRAME.toInt()
            trailingByte = if (playableBytes < pcm.size) pcm.last() else null
            if (playableBytes == 0) return@streamPcm

            val track = remotePcmTrack ?: createRemotePcmTrack(config.pcmSampleRate).also {
                remotePcmTrack = it
                it.play()
                Log.d("AndroidTTS", "TTS PCM playback started at ${config.pcmSampleRate} Hz")
            }
            var offset = 0
            var zeroWriteRetries = 0
            while (offset < playableBytes) {
                if (requestId != requestCounter.get()) throw CancellationException("TTS cancelado")
                val written = track.write(
                    pcm,
                    offset,
                    playableBytes - offset,
                    AudioTrack.WRITE_BLOCKING
                )
                check(written >= 0) { "AudioTrack no pudo escribir PCM: $written" }
                if (written == 0) {
                    check(++zeroWriteRetries <= MAX_ZERO_WRITE_RETRIES) {
                        "AudioTrack no acepto los datos PCM"
                    }
                    Thread.sleep(2L)
                    continue
                }
                zeroWriteRetries = 0
                offset += written
            }
        }

        val stream = result.getOrElse { error ->
            if (requestId == requestCounter.get()) {
                finishRemotePcmPlayback(requestId)
                _error.value = error.message ?: "No se pudo reproducir el flujo PCM"
            }
            return
        }
        if (requestId == requestCounter.get()) {
            val expectedFrames = stream.bytesReceived / PCM16_MONO_BYTES_PER_FRAME
            val maximumDrainMs = expectedFrames * 1_000L / config.pcmSampleRate + 1_000L
            val drainStartedAt = System.currentTimeMillis()
            while (requestId == requestCounter.get() &&
                remotePcmTrack.playedFrames() < expectedFrames &&
                System.currentTimeMillis() - drainStartedAt < maximumDrainMs
            ) {
                delay(20L)
            }
            finishRemotePcmPlayback(requestId)
        }
    }

    private fun createRemotePcmTrack(sampleRate: Int): AudioTrack {
        val minimumBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minimumBuffer > 0) { "Android no admite PCM a $sampleRate Hz" }
        val bufferBytes = maxOf(minimumBuffer, 4 * 1024)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()
            .also { track ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val lowLatencyFrames = (sampleRate / 50).coerceAtLeast(1)
                    track.setStartThresholdInFrames(
                        lowLatencyFrames.coerceAtMost(track.bufferSizeInFrames)
                    )
                }
            }
    }

    private fun AudioTrack?.playedFrames(): Long =
        this?.playbackHeadPosition?.toLong()?.and(0xffff_ffffL) ?: 0L

    private fun finishRemotePcmPlayback(requestId: Long) {
        if (requestId != requestCounter.get()) return
        remotePcmTrack?.runCatching {
            stop()
            release()
        }
        remotePcmTrack = null
        remoteJob = null
        _isSpeaking.value = false
    }

    private suspend fun playRemoteAudio(audio: RemoteTtsAudio, requestId: Long) {
        if (audio.bytes.isEmpty() || requestId != requestCounter.get()) {
            _isSpeaking.value = false
            return
        }
        // Keep disk I/O and the blocking prepare() off the main thread; MediaPlayer itself is
        // created here (Looper thread) so completion/error listeners fire reliably.
        val file = withContext(Dispatchers.IO) {
            File.createTempFile("cortex_remote_tts_", audio.fileExtension, context.cacheDir)
                .apply { writeBytes(audio.bytes) }
        }
        remoteAudioFile = file
        runCatching {
            MediaPlayer().also { player ->
                remotePlayer = player
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                player.setDataSource(file.absolutePath)
                player.setOnCompletionListener {
                    finishRemotePlayback(requestId)
                }
                player.setOnErrorListener { _, what, extra ->
                    Log.e("AndroidTTS", "Remote audio playback failed: what=$what extra=$extra")
                    if (requestId == requestCounter.get()) {
                        _error.value = "No se pudo reproducir el audio del servidor TTS"
                    }
                    finishRemotePlayback(requestId)
                    true
                }
                withContext(Dispatchers.IO) { player.prepare() }
                player.start()
            }
        }.onFailure { error ->
            Log.e("AndroidTTS", "Remote audio preparation failed", error)
            finishRemotePlayback(requestId)
            _error.value = error.message ?: "El servidor devolvio un formato de audio no compatible"
        }
    }

    private fun finishRemotePlayback(requestId: Long) {
        if (requestId != requestCounter.get()) return
        remotePlayer?.release()
        remotePlayer = null
        remoteAudioFile?.delete()
        remoteAudioFile = null
        remoteJob = null
        _isSpeaking.value = false
    }

    private fun finishPiperPlayback(requestId: Long) {
        if (requestId != requestCounter.get()) return
        piperTrack?.runCatching {
            stop()
            release()
        }
        piperTrack = null
        piperJob = null
        _isSpeaking.value = false
    }

    private fun attachGooglePlaybackListener() {
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (playbackTracker.owns(utteranceId)) _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                val request = activeRequest
                if (request != null && playbackTracker.isFinal(request, utteranceId)) {
                    _isSpeaking.value = false
                    playbackTracker.cancel()
                    activeRequest = null
                }
            }

            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?) = handlePlaybackError(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) =
                handlePlaybackError(utteranceId)

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (playbackTracker.owns(utteranceId)) {
                    _isSpeaking.value = false
                    playbackTracker.cancel()
                    activeRequest = null
                }
            }
        })
    }

    private fun handlePlaybackError(utteranceId: String?) {
        if (!playbackTracker.owns(utteranceId)) return
        _isSpeaking.value = false
        _error.value = "No se pudo reproducir la respuesta"
        playbackTracker.cancel()
        activeRequest = null
    }

    private fun Voice.isInstalledLocally(): Boolean =
        !isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features.orEmpty()

    private fun googleVoiceLabel(voice: Voice, index: Int): String {
        val region = voice.locale.getDisplayCountry(voice.locale).takeIf(String::isNotBlank)
        val location = listOfNotNull(voice.locale.getDisplayLanguage(voice.locale), region)
            .joinToString(" · ")
        return "$location · Voz ${index + 1}"
    }

    private fun sameLanguage(first: Locale, second: Locale): Boolean =
        first.language.equals(second.language, ignoreCase = true)

    companion object {
        private const val PCM16_MONO_BYTES_PER_FRAME = 2L
        private const val MAX_ZERO_WRITE_RETRIES = 50
        private const val PIPER_STREAM_CHUNK_MAX_CHARS = 180
        private const val PIPER_BUFFERED_CHUNKS = 1
        private const val PIPER_MIN_BUFFER_BYTES = 4 * 1024
        private const val PIPER_DRAIN_GRACE_MS = 1_000L
    }
}
