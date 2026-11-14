package com.example.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.example.data.NovaContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.sqrt

enum class VoiceListeningMode {
    IDLE,
    DIRECT_COMMAND,
    WAKE_WORD_LOOP
}

class NovaVoiceManager(
    private val context: Context,
    private val onModeChanged: (VoiceListeningMode) -> Unit,
    private val onRmsChanged: (Float) -> Unit,
    private val onPartialTranscript: (String) -> Unit,
    private val onCommandRecognized: (String) -> Unit,
    private val onSpeakingStateChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val aiRepository = NovaContainer.get(context).aiRepository

    private var speechRecognizer: SpeechRecognizer? = null
    private var audioCaptureJob: Job? = null
    @Volatile
    private var stopAndTranscribeRequested = false

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var currentMode = VoiceListeningMode.IDLE
    private var resumeWakeAfterAction = false
    @Volatile
    private var isSpeakingNow = false
    private var destroyed = false

    private val wakePrefixes = listOf(
        "hi nova",
        "hai nova",
        "hey nova",
        "halo nova",
        "hello nova",
        "he nova",
        "hay nova",
        "hai nofa",
        "hi nofa",
        "nova"
    )

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val idLocale = Locale("id", "ID")
                val res = tts?.isLanguageAvailable(idLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (res >= TextToSpeech.LANG_AVAILABLE) {
                    tts?.language = idLocale
                } else {
                    tts?.language = Locale.US
                }
                tts?.setSpeechRate(1.02f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            isSpeakingNow = true
                            onSpeakingStateChanged(true)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            isSpeakingNow = false
                            onSpeakingStateChanged(false)
                            if (utteranceId == UTTERANCE_WAKE_ACK) {
                                startDirectListening(keepWakeLoopAfter = resumeWakeAfterAction)
                            } else if (resumeWakeAfterAction && currentMode == VoiceListeningMode.IDLE) {
                                scheduleWakeRestart(450L)
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            isSpeakingNow = false
                            onSpeakingStateChanged(false)
                            if (resumeWakeAfterAction && currentMode == VoiceListeningMode.IDLE) {
                                scheduleWakeRestart(700L)
                            }
                        }
                    }
                })
            }
        }
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isSpeechAvailable(): Boolean {
        return aiRepository.hasCloudSpeechToText() || SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startDirectListening(keepWakeLoopAfter: Boolean) {
        mainHandler.post {
            if (destroyed) return@post
            if (!hasRecordPermission()) {
                onError("Izin mikrofon diperlukan untuk merekam suara.")
                return@post
            }
            if (!isSpeechAvailable()) {
                onError("Layanan pengenalan suara belum tersedia di perangkat ini.")
                return@post
            }
            resumeWakeAfterAction = keepWakeLoopAfter
            stopTtsInternal()
            stopAndTranscribeRequested = false
            currentMode = VoiceListeningMode.DIRECT_COMMAND
            onModeChanged(currentMode)

            if (aiRepository.hasCloudSpeechToText()) {
                startAudioCapture(isWakeWord = false)
            } else {
                beginSystemSpeechRecognition(isWakeWord = false)
            }
        }
    }

    fun startWakeWordLoop() {
        mainHandler.post {
            if (destroyed) return@post
            resumeWakeAfterAction = true
            if (!hasRecordPermission() || !isSpeechAvailable()) return@post
            if (isSpeakingNow || currentMode == VoiceListeningMode.DIRECT_COMMAND) return@post
            if (currentMode == VoiceListeningMode.WAKE_WORD_LOOP) return@post
            currentMode = VoiceListeningMode.WAKE_WORD_LOOP
            onModeChanged(currentMode)

            if (aiRepository.hasCloudSpeechToText()) {
                startAudioCapture(isWakeWord = true)
            } else {
                beginSystemSpeechRecognition(isWakeWord = true)
            }
        }
    }

    fun stopWakeWordLoop() {
        mainHandler.post {
            resumeWakeAfterAction = false
            if (currentMode == VoiceListeningMode.WAKE_WORD_LOOP) {
                stopListeningInternal(cancelAudioOnly = true)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            if (currentMode == VoiceListeningMode.DIRECT_COMMAND && audioCaptureJob?.isActive == true) {
                // Finish current manual recording and immediately transcribe what was spoken
                stopAndTranscribeRequested = true
            } else {
                stopListeningInternal(cancelAudioOnly = true)
            }
        }
    }

    fun speak(text: String) {
        mainHandler.post {
            if (destroyed || !ttsReady || text.isBlank()) return@post
            if (currentMode == VoiceListeningMode.WAKE_WORD_LOOP) {
                cancelActiveAudioAndRecognizer()
                currentMode = VoiceListeningMode.IDLE
                onModeChanged(VoiceListeningMode.IDLE)
            }
            isSpeakingNow = true
            onSpeakingStateChanged(true)
            val cleaned = text
                .replace("**", "")
                .replace("`", "")
                .replace("#", "")
                .trim()
            selectBestTtsLanguage(cleaned)
            tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "nova_reply_${UUID.randomUUID()}")
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            stopTtsInternal()
            if (resumeWakeAfterAction && currentMode == VoiceListeningMode.IDLE) {
                scheduleWakeRestart(400L)
            }
        }
    }

    private fun stopTtsInternal() {
        tts?.stop()
        if (isSpeakingNow) {
            isSpeakingNow = false
            onSpeakingStateChanged(false)
        }
    }

    private fun stopListeningInternal(cancelAudioOnly: Boolean) {
        mainHandler.removeCallbacksAndMessages(null)
        if (cancelAudioOnly) {
            stopAndTranscribeRequested = false
        }
        cancelActiveAudioAndRecognizer()
        currentMode = VoiceListeningMode.IDLE
        onModeChanged(VoiceListeningMode.IDLE)
        onRmsChanged(0f)
    }

    private fun cancelActiveAudioAndRecognizer() {
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        destroyRecognizer()
    }

    private fun scheduleWakeRestart(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(WAKE_RESTART_TOKEN)
        mainHandler.postAtTime({
            if (!destroyed && resumeWakeAfterAction && !isSpeakingNow &&
                currentMode != VoiceListeningMode.DIRECT_COMMAND
            ) {
                currentMode = VoiceListeningMode.WAKE_WORD_LOOP
                onModeChanged(currentMode)
                if (aiRepository.hasCloudSpeechToText()) {
                    startAudioCapture(isWakeWord = true)
                } else {
                    beginSystemSpeechRecognition(isWakeWord = true)
                }
            }
        }, WAKE_RESTART_TOKEN, android.os.SystemClock.uptimeMillis() + delayMs)
    }

    @SuppressLint("MissingPermission")
    private fun startAudioCapture(isWakeWord: Boolean) {
        val previousJob = audioCaptureJob
        destroyRecognizer()

        audioCaptureJob = voiceScope.launch {
            if (previousJob != null && previousJob.isActive) {
                previousJob.cancel()
                runCatching { previousJob.join() }
            }
            // Brief yield so hardware AudioRecord releases cleanly between sessions
            delay(90L)

            if (isWakeWord) {
                runWakeWordAudioLoop()
            } else {
                runDirectCommandAudioCapture()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(sampleRate: Int, bufferBytes: Int): AudioRecord? {
        val sources = listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        for (source in sources) {
            val recorder = runCatching {
                AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
                )
            }.getOrNull()
            if (recorder != null && recorder.state == AudioRecord.STATE_INITIALIZED) {
                return recorder
            }
            runCatching { recorder?.release() }
        }
        return null
    }

    private suspend fun runDirectCommandAudioCapture() {
        val sampleRate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(3200)
        val bufferSize = maxOf(minBuf * 2, 6400)

        val pcmStream = ByteArrayOutputStream()
        var captureError: String? = null

        withContext(Dispatchers.IO) {
            val recorder = createAudioRecord(sampleRate, bufferSize)
            if (recorder == null) {
                captureError = "Mikrofon sedang digunakan atau tidak dapat diinisialisasi."
                return@withContext
            }

            val chunk = ShortArray(640) // 40ms @ 16kHz
            val byteChunk = ByteArray(chunk.size * 2)
            var speechDetected = false
            var speechMs = 0L
            var silenceAfterSpeechMs = 0L
            var elapsedMs = 0L
            var noiseFloor = 0.015f

            try {
                recorder.startRecording()
                while (isActive && !stopAndTranscribeRequested && elapsedMs < 14_000L) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read <= 0) {
                        delay(20L)
                        continue
                    }
                    val rms = computeRms(chunk, read)
                    val visualRms = ((rms - noiseFloor) * 9f).coerceIn(0f, 1f)
                    mainHandler.post { onRmsChanged(visualRms) }

                    // Write little-endian 16-bit PCM
                    for (i in 0 until read) {
                        val s = chunk[i].toInt()
                        byteChunk[i * 2] = (s and 0xFF).toByte()
                        byteChunk[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    pcmStream.write(byteChunk, 0, read * 2)

                    val chunkMs = (read * 1000L) / sampleRate
                    elapsedMs += chunkMs

                    if (!speechDetected) {
                        noiseFloor = (noiseFloor * 0.92f + rms * 0.08f).coerceIn(0.008f, 0.06f)
                        if (rms > maxOf(0.032f, noiseFloor * 2.2f)) {
                            speechDetected = true
                            speechMs += chunkMs
                            silenceAfterSpeechMs = 0L
                        }
                    } else {
                        if (rms > maxOf(0.025f, noiseFloor * 1.8f)) {
                            speechMs += chunkMs
                            silenceAfterSpeechMs = 0L
                        } else {
                            silenceAfterSpeechMs += chunkMs
                        }
                        if (speechMs >= 320L && silenceAfterSpeechMs >= 1_150L) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                captureError = e.localizedMessage ?: "Gagal merekam audio."
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
            }
        }

        onRmsChanged(0f)
        if (destroyed) return

        val pcmBytes = pcmStream.toByteArray()
        if (captureError != null) {
            // Fallback to Android SpeechRecognizer if AudioRecord failed
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                beginSystemSpeechRecognition(isWakeWord = false)
            } else {
                currentMode = VoiceListeningMode.IDLE
                onModeChanged(VoiceListeningMode.IDLE)
                onError(captureError ?: "Gagal merekam suara.")
            }
            return
        }

        if (pcmBytes.size < 6400) {
            currentMode = VoiceListeningMode.IDLE
            onModeChanged(VoiceListeningMode.IDLE)
            onError("Suara tidak terdengar, silakan coba lagi.")
            if (resumeWakeAfterAction) scheduleWakeRestart(600L)
            return
        }

        onPartialTranscript("Mengubah suara ke teks…")
        val wavBytes = encodePcmToWav(pcmBytes, sampleRate = sampleRate)
        val result = aiRepository.transcribeWavAudio(wavBytes)

        result.onSuccess { text ->
            handleFinalTranscript(text, wasWakeWordSession = false)
        }.onFailure { err ->
            currentMode = VoiceListeningMode.IDLE
            onModeChanged(VoiceListeningMode.IDLE)
            onError("Gagal transkripsi suara: ${err.localizedMessage ?: "error jaringan"}")
            if (resumeWakeAfterAction) scheduleWakeRestart(900L)
        }
    }

    private suspend fun runWakeWordAudioLoop() {
        val sampleRate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(3200)
        val bufferSize = maxOf(minBuf * 2, 6400)

        withContext(Dispatchers.IO) {
            val recorder = createAudioRecord(sampleRate, bufferSize)
            if (recorder == null) {
                return@withContext
            }

            val chunk = ShortArray(640) // 40ms @ 16kHz
            val byteChunk = ByteArray(chunk.size * 2)
            // Keep a rolling 480ms pre-roll (12 chunks * 1280 bytes) so "Hi..." isn't clipped
            val preRollQueue = ArrayDeque<ByteArray>(14)
            val utteranceStream = ByteArrayOutputStream()

            var capturing = false
            var speechMs = 0L
            var silenceMs = 0L
            var totalCaptureMs = 0L
            var noiseFloor = 0.016f

            try {
                recorder.startRecording()
                while (isActive && !destroyed && resumeWakeAfterAction &&
                    currentMode == VoiceListeningMode.WAKE_WORD_LOOP
                ) {
                    if (isSpeakingNow) {
                        capturing = false
                        utteranceStream.reset()
                        preRollQueue.clear()
                        delay(120L)
                        continue
                    }

                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read <= 0) {
                        delay(25L)
                        continue
                    }

                    val rms = computeRms(chunk, read)
                    for (i in 0 until read) {
                        val s = chunk[i].toInt()
                        byteChunk[i * 2] = (s and 0xFF).toByte()
                        byteChunk[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    val copy = byteChunk.copyOf(read * 2)
                    val chunkMs = (read * 1000L) / sampleRate

                    if (!capturing) {
                        if (preRollQueue.size >= 12) {
                            preRollQueue.removeFirst()
                        }
                        preRollQueue.addLast(copy)

                        noiseFloor = (noiseFloor * 0.94f + rms * 0.06f).coerceIn(0.008f, 0.055f)
                        val triggerThreshold = maxOf(0.036f, noiseFloor * 2.35f)
                        if (rms > triggerThreshold) {
                            capturing = true
                            speechMs = chunkMs
                            silenceMs = 0L
                            totalCaptureMs = 0L
                            utteranceStream.reset()
                            for (pre in preRollQueue) {
                                utteranceStream.write(pre)
                                totalCaptureMs += 40L
                            }
                            preRollQueue.clear()
                        }
                    } else {
                        utteranceStream.write(copy)
                        totalCaptureMs += chunkMs
                        val sustainThreshold = maxOf(0.026f, noiseFloor * 1.8f)
                        if (rms > sustainThreshold) {
                            speechMs += chunkMs
                            silenceMs = 0L
                        } else {
                            silenceMs += chunkMs
                        }

                        val doneSpeaking = (speechMs >= 240L && silenceMs >= 720L) || totalCaptureMs >= 4_800L
                        if (doneSpeaking) {
                            val pcm = utteranceStream.toByteArray()
                            capturing = false
                            utteranceStream.reset()
                            if (speechMs >= 240L && pcm.size >= 8000) {
                                val wav = encodePcmToWav(pcm, sampleRate = sampleRate)
                                val res = aiRepository.transcribeWavAudio(wav)
                                val text = res.getOrNull()?.trim().orEmpty()
                                if (text.isNotBlank() && containsWakePhrase(text)) {
                                    // Stop AudioRecord cleanly before dispatching wake word action
                                    mainHandler.post {
                                        handleFinalTranscript(text, wasWakeWordSession = true)
                                    }
                                    break
                                }
                            }
                            speechMs = 0L
                            silenceMs = 0L
                            totalCaptureMs = 0L
                        }
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
            }
        }
    }

    private fun computeRms(samples: ShortArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        val mean = sum / count.coerceAtLeast(1)
        return (sqrt(mean) / 32768.0).toFloat()
    }

    private fun encodePcmToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize

        val out = ByteArrayOutputStream(44 + dataSize)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(intToLittleEndian(chunkSize))
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.write(intToLittleEndian(16)) // PCM subchunk size
        out.write(shortToLittleEndian(1)) // PCM format = 1
        out.write(shortToLittleEndian(channels))
        out.write(intToLittleEndian(sampleRate))
        out.write(intToLittleEndian(byteRate))
        out.write(shortToLittleEndian(channels * bitsPerSample / 8))
        out.write(shortToLittleEndian(bitsPerSample))
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.write(intToLittleEndian(dataSize))
        out.write(pcmData)
        return out.toByteArray()
    }

    private fun intToLittleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortToLittleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    private fun beginSystemSpeechRecognition(isWakeWord: Boolean) {
        destroyRecognizer()
        val emitRms = onRmsChanged
        val emitError = onError
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                emitRms(normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                emitRms(0f)
            }

            override fun onError(error: Int) {
                emitRms(0f)
                if (destroyed) return
                if (isWakeWord) {
                    currentMode = VoiceListeningMode.IDLE
                    onModeChanged(VoiceListeningMode.IDLE)
                    if (resumeWakeAfterAction && !isSpeakingNow) {
                        val backoff = when (error) {
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                            SpeechRecognizer.ERROR_AUDIO -> 1400L
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 2000L
                            else -> 650L
                        }
                        scheduleWakeRestart(backoff)
                    }
                } else {
                    currentMode = VoiceListeningMode.IDLE
                    onModeChanged(VoiceListeningMode.IDLE)
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Suara tidak terdengar, silakan coba lagi."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon belum diberikan."
                        else -> "Gagal mengenali suara (kode $error)."
                    }
                    emitError(message)
                    if (resumeWakeAfterAction) {
                        scheduleWakeRestart(900L)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                emitRms(0f)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                handleFinalTranscript(best, isWakeWord)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotEmpty() && !isWakeWord) {
                    onPartialTranscript(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, !isWakeWord)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching {
            recognizer.startListening(intent)
        }.onFailure {
            currentMode = VoiceListeningMode.IDLE
            onModeChanged(VoiceListeningMode.IDLE)
            if (!isWakeWord) {
                emitError("Tidak dapat memulai mikrofon suara.")
            } else if (resumeWakeAfterAction) {
                scheduleWakeRestart(1500L)
            }
        }
    }

    private fun containsWakePhrase(transcript: String): Boolean {
        val lower = transcript.lowercase(Locale.getDefault())
        return wakePrefixes.any { prefix ->
            lower == prefix || lower.startsWith("$prefix ") || lower.contains(" $prefix")
        }
    }

    private fun handleFinalTranscript(transcript: String, wasWakeWordSession: Boolean) {
        if (transcript.isBlank()) {
            currentMode = VoiceListeningMode.IDLE
            onModeChanged(VoiceListeningMode.IDLE)
            if (wasWakeWordSession && resumeWakeAfterAction) {
                scheduleWakeRestart(600L)
            }
            return
        }

        if (!wasWakeWordSession) {
            currentMode = VoiceListeningMode.IDLE
            onModeChanged(VoiceListeningMode.IDLE)
            onCommandRecognized(transcript)
            if (resumeWakeAfterAction && !isSpeakingNow) {
                scheduleWakeRestart(1500L)
            }
            return
        }

        val lower = transcript.lowercase(Locale.getDefault())
        val matchedPrefix = wakePrefixes.firstOrNull { prefix ->
            lower == prefix || lower.startsWith("$prefix ") || lower.contains(" $prefix")
        }

        if (matchedPrefix == null) {
            currentMode = VoiceListeningMode.IDLE
            onModeChanged(VoiceListeningMode.IDLE)
            if (resumeWakeAfterAction) {
                scheduleWakeRestart(550L)
            }
            return
        }

        val idx = lower.indexOf(matchedPrefix)
        val afterWake = transcript
            .substring((idx + matchedPrefix.length).coerceAtMost(transcript.length))
            .trim(',', '.', ' ', ':', '!')

        currentMode = VoiceListeningMode.IDLE
        onModeChanged(VoiceListeningMode.IDLE)

        if (afterWake.length >= 2) {
            onCommandRecognized(afterWake)
        } else {
            if (ttsReady) {
                cancelActiveAudioAndRecognizer()
                isSpeakingNow = true
                onSpeakingStateChanged(true)
                tts?.speak("Ya, Nova mendengarkan.", TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_WAKE_ACK)
            } else {
                startDirectListening(keepWakeLoopAfter = resumeWakeAfterAction)
            }
        }
    }

    private fun selectBestTtsLanguage(text: String) {
        val englishIndicators = listOf(" the ", " is ", " are ", " what ", " how ", " hello ")
        val lower = " ${text.lowercase(Locale.getDefault())} "
        val looksEnglish = englishIndicators.count { lower.contains(it) } >= 2
        val target = if (looksEnglish) Locale.US else Locale("id", "ID")
        val avail = tts?.isLanguageAvailable(target) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (avail >= TextToSpeech.LANG_AVAILABLE) {
            tts?.language = target
        }
    }

    private fun destroyRecognizer() {
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    fun release() {
        destroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        cancelActiveAudioAndRecognizer()
        voiceScope.cancel()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }

    companion object {
        private const val UTTERANCE_WAKE_ACK = "nova_wake_ack"
        private val WAKE_RESTART_TOKEN = Any()
    }
}
