package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import com.example.BuildConfig
import com.example.data.local.ChatMessageEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AiReplyResult(
    val content: String,
    val usedModelLabel: String,
    val usedFallback: Boolean = false
)

data class ApiDiagnosticStatus(
    val openRouterConfigured: Boolean = false,
    val groqConfigured: Boolean = false,
    val openRouterStatus: String = "Belum diuji",
    val groqStatus: String = "Belum diuji"
)

class OpenSourceAiRepository(private val appContext: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(22, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        )
        .build()

    private val api: OpenAiCompatibleApi = Retrofit.Builder()
        .baseUrl("https://openrouter.ai/api/v1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenAiCompatibleApi::class.java)

    fun hasCloudSpeechToText(): Boolean {
        return resolveApiKey(AiProvider.GROQ) != null && isNetworkAvailable()
    }

    suspend fun transcribeWavAudio(wavBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        val groqKey = resolveApiKey(AiProvider.GROQ)
            ?: return@withContext Result.failure(IllegalStateException("GROQ_API_KEY belum tersedia"))
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(IllegalStateException("Tidak ada koneksi internet"))
        }
        return@withContext try {
            val fileBody = wavBytes.toRequestBody("audio/wav".toMediaType())
            val filePart = MultipartBody.Part.createFormData("file", "speech.wav", fileBody)
            val modelPart = "whisper-large-v3-turbo".toRequestBody("text/plain".toMediaType())
            val formatPart = "json".toRequestBody("text/plain".toMediaType())
            val promptPart = "Hi Nova. Hai Nova. Halo Nova. Bahasa Indonesia dan English.".toRequestBody("text/plain".toMediaType())

            val response = api.transcribeAudio(
                url = "https://api.groq.com/openai/v1/audio/transcriptions",
                authorization = "Bearer $groqKey",
                file = filePart,
                model = modelPart,
                responseFormat = formatPart,
                prompt = promptPart
            )
            val text = response.body()?.text?.trim().orEmpty()
            if (response.isSuccessful && text.isNotBlank()) {
                Result.success(text)
            } else {
                val err = parseErrorMessage(response.errorBody()?.string())
                Result.failure(IllegalStateException(err ?: "STT HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun currentKeyPresence(): ApiDiagnosticStatus {
        return ApiDiagnosticStatus(
            openRouterConfigured = resolveApiKey(AiProvider.OPENROUTER) != null,
            groqConfigured = resolveApiKey(AiProvider.GROQ) != null,
            openRouterStatus = if (resolveApiKey(AiProvider.OPENROUTER) != null) {
                "API key terdeteksi (siap diuji)"
            } else {
                "OPENROUTER_API_KEY belum diisi"
            },
            groqStatus = if (resolveApiKey(AiProvider.GROQ) != null) {
                "API key terdeteksi (siap diuji)"
            } else {
                "GROQ_API_KEY belum diisi"
            }
        )
    }

    suspend fun testApiConnections(): ApiDiagnosticStatus = withContext(Dispatchers.IO) {
        val orKey = resolveApiKey(AiProvider.OPENROUTER)
        val groqKey = resolveApiKey(AiProvider.GROQ)

        if (!isNetworkAvailable()) {
            return@withContext ApiDiagnosticStatus(
                openRouterConfigured = orKey != null,
                groqConfigured = groqKey != null,
                openRouterStatus = "Tidak ada koneksi internet",
                groqStatus = "Tidak ada koneksi internet"
            )
        }

        val orStatus = if (orKey == null) {
            "Key belum diisi"
        } else {
            pingChatEndpoint(
                url = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = orKey,
                modelId = AiModelCatalog.OPENROUTER_AUTO_FREE.id,
                isOpenRouter = true
            )
        }

        val groqStatus = if (groqKey == null) {
            "Key belum diisi"
        } else {
            pingChatEndpoint(
                url = "https://api.groq.com/openai/v1/chat/completions",
                apiKey = groqKey,
                modelId = AiModelCatalog.GROQ_LLAMA_8B.id,
                isOpenRouter = false
            )
        }

        ApiDiagnosticStatus(
            openRouterConfigured = orKey != null,
            groqConfigured = groqKey != null,
            openRouterStatus = orStatus,
            groqStatus = groqStatus
        )
    }

    private suspend fun pingChatEndpoint(
        url: String,
        apiKey: String,
        modelId: String,
        isOpenRouter: Boolean
    ): String {
        return try {
            val response = api.createChatCompletion(
                url = url,
                authorization = "Bearer $apiKey",
                httpReferer = if (isOpenRouter) "https://github.com/nova-ai-android" else null,
                appTitle = if (isOpenRouter) "Nova AI" else null,
                request = ChatCompletionRequest(
                    model = modelId,
                    messages = listOf(ChatMessageDto(role = "user", content = "Hi")),
                    maxTokens = 12
                )
            )
            if (response.isSuccessful) {
                "Terhubung (HTTP ${response.code()} OK)"
            } else {
                val err = parseErrorMessage(response.errorBody()?.string())
                "Gagal HTTP ${response.code()}${if (err != null) ": $err" else ""}"
            }
        } catch (e: Exception) {
            "Error koneksi: ${e.localizedMessage ?: "timeout"}"
        }
    }

    suspend fun generateReply(
        userPrompt: String,
        history: List<ChatMessageEntity>,
        modelOption: AiModelOption,
        isVoiceMode: Boolean
    ): AiReplyResult = withContext(Dispatchers.IO) {
        if (modelOption.provider == AiProvider.LOCAL) {
            return@withContext AiReplyResult(
                content = buildLocalAssistantResponse(userPrompt, reasonNote = null),
                usedModelLabel = modelOption.shortBadge,
                usedFallback = false
            )
        }

        val orKey = resolveApiKey(AiProvider.OPENROUTER)
        val groqKey = resolveApiKey(AiProvider.GROQ)

        if (orKey == null && groqKey == null) {
            return@withContext AiReplyResult(
                content = buildLocalAssistantResponse(
                    userPrompt,
                    reasonNote = "Mode Lokal aktif karena OPENROUTER_API_KEY & GROQ_API_KEY belum diisi di Secrets AI Studio / .env."
                ),
                usedModelLabel = "Local Fallback",
                usedFallback = true
            )
        }

        if (!isNetworkAvailable()) {
            return@withContext AiReplyResult(
                content = buildLocalAssistantResponse(
                    userPrompt,
                    reasonNote = "Tidak ada koneksi internet. Nova menjawab menggunakan mode lokal."
                ),
                usedModelLabel = "Offline Fallback",
                usedFallback = true
            )
        }

        val systemInstruction = if (isVoiceMode) {
            "Kamu adalah Nova, asisten AI futuristik yang cerdas, ramah, dan ringkas. " +
                "Jawab dalam bahasa yang sama dengan pengguna (utamakan Bahasa Indonesia jika pengguna berbahasa Indonesia). " +
                "Karena jawaban ini akan dibacakan lewat suara (TTS), buat jawaban natural, jelas, dan maksimal 2-4 kalimat tanpa markdown rumit."
        } else {
            "Kamu adalah Nova, asisten AI modern berbasis model open-source yang cepat, akurat, dan ramah. " +
                "Jawab dalam bahasa yang sama dengan pengguna (utamakan Bahasa Indonesia jika pengguna berbahasa Indonesia)."
        }

        val messagesPayload = buildList {
            add(ChatMessageDto(role = "system", content = systemInstruction))
            history.takeLast(8).forEach { msg ->
                if (msg.role == "user" || msg.role == "assistant") {
                    add(ChatMessageDto(role = msg.role, content = msg.content))
                }
            }
            if (history.lastOrNull()?.content != userPrompt) {
                add(ChatMessageDto(role = "user", content = userPrompt))
            }
        }

        // Build prioritized cloud attempt chain so if a specific :free slug is busy/unavailable,
        // Nova automatically fails over to OpenRouter Auto Free or Groq Llama 3.3 without hanging.
        val candidates = buildList {
            add(modelOption)
            if (modelOption.provider == AiProvider.OPENROUTER) {
                if (modelOption.id != AiModelCatalog.OPENROUTER_AUTO_FREE.id) {
                    add(AiModelCatalog.OPENROUTER_AUTO_FREE)
                }
                if (groqKey != null) {
                    add(AiModelCatalog.GROQ_LLAMA_70B)
                    add(AiModelCatalog.GROQ_LLAMA_8B)
                }
            } else if (modelOption.provider == AiProvider.GROQ) {
                if (modelOption.id != AiModelCatalog.GROQ_LLAMA_8B.id) {
                    add(AiModelCatalog.GROQ_LLAMA_8B)
                }
                if (orKey != null) {
                    add(AiModelCatalog.OPENROUTER_AUTO_FREE)
                }
            }
        }

        var lastErrorNote = ""
        for (candidate in candidates) {
            val key = resolveApiKey(candidate.provider) ?: continue
            val endpointUrl = when (candidate.provider) {
                AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
                AiProvider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
                AiProvider.LOCAL -> continue
            }

            try {
                val response = api.createChatCompletion(
                    url = endpointUrl,
                    authorization = "Bearer $key",
                    httpReferer = if (candidate.provider == AiProvider.OPENROUTER) {
                        "https://github.com/nova-ai-android"
                    } else {
                        null
                    },
                    appTitle = if (candidate.provider == AiProvider.OPENROUTER) "Nova AI" else null,
                    request = ChatCompletionRequest(
                        model = candidate.id,
                        messages = messagesPayload,
                        maxTokens = if (isVoiceMode) 260 else 750
                    )
                )

                val choiceMsg = response.body()?.choices?.firstOrNull()?.message
                val replyText = (choiceMsg?.content ?: choiceMsg?.reasoning)?.trim()
                if (response.isSuccessful && !replyText.isNullOrBlank()) {
                    val label = if (candidate.id == modelOption.id) {
                        candidate.shortBadge
                    } else {
                        "${candidate.shortBadge} (Auto)"
                    }
                    return@withContext AiReplyResult(
                        content = replyText,
                        usedModelLabel = label,
                        usedFallback = false
                    )
                } else {
                    val parsedErr = parseErrorMessage(response.errorBody()?.string())
                    lastErrorNote = "${candidate.shortBadge}: HTTP ${response.code()}" +
                        if (parsedErr != null) " ($parsedErr)" else ""
                }
            } catch (e: Exception) {
                lastErrorNote = "${candidate.shortBadge}: ${e.localizedMessage ?: "timeout"}"
            }
        }

        return@withContext AiReplyResult(
            content = buildLocalAssistantResponse(
                userPrompt,
                reasonNote = "Semua endpoint cloud gagal merespons ($lastErrorNote). Beralih ke Mode Lokal."
            ),
            usedModelLabel = "Local Fallback",
            usedFallback = true
        )
    }

    private fun parseErrorMessage(errBody: String?): String? {
        if (errBody.isNullOrBlank()) return null
        return runCatching {
            moshi.adapter(ApiErrorEnvelope::class.java).fromJson(errBody)?.error?.message
        }.getOrNull()?.take(120)
    }

    private fun resolveApiKey(provider: AiProvider): String? {
        val raw = when (provider) {
            AiProvider.OPENROUTER -> BuildConfig.OPENROUTER_API_KEY
            AiProvider.GROQ -> BuildConfig.GROQ_API_KEY
            AiProvider.LOCAL -> ""
        }.trim()
        return if (raw.isBlank() || raw.startsWith("MY_")) null else raw
    }

    @Suppress("DEPRECATION")
    private fun isNetworkAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val info = cm.activeNetworkInfo
            info != null && info.isConnected
        }
    }

    private fun buildLocalAssistantResponse(prompt: String, reasonNote: String?): String {
        val lower = prompt.lowercase(Locale.getDefault()).trim()
        val coreReply = when {
            lower.contains("jam berapa") || lower.contains("waktu") || lower.contains("what time") -> {
                val timeStr = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date())
                "Sekarang pukul $timeStr."
            }
            lower.contains("tanggal") || lower.contains("hari apa") || lower.contains("today") -> {
                val dateStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date())
                "Hari ini adalah $dateStr."
            }
            lower.contains("baterai") || lower.contains("battery") -> {
                val pct = readBatteryPercent()
                if (pct >= 0) "Sisa baterai perangkat Anda saat ini $pct%." else "Status baterai tidak dapat dibaca saat ini."
            }
            lower.startsWith("hitung ") || lower.contains("berapa ") && Regex("""\d+\s*[+\-*/x]\s*\d+""").containsMatchIn(lower) -> {
                val exprMatch = Regex("""[\d.,+\-*/xX()\s]+""").findAll(lower)
                    .map { it.value.trim() }
                    .firstOrNull { it.any(Char::isDigit) && it.any { c -> c in "+-*/xX" } }
                val value = exprMatch?.let { evaluateSimpleMath(it.replace('x', '*').replace('X', '*')) }
                if (value != null) {
                    val formatted = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
                    "Hasil perhitungannya adalah $formatted."
                } else {
                    "Sebutkan operasi matematika yang ingin dihitung, misalnya: hitung 25 * 4."
                }
            }
            lower == "hi nova" || lower == "hai nova" || lower == "halo nova" || lower == "halo" || lower == "hai" -> {
                "Halo! Nova siap membantu. Anda bisa bertanya apa saja atau ucapkan perintah Anda sekarang."
            }
            lower.contains("siapa kamu") || lower.contains("who are you") -> {
                "Saya Nova, asisten AI futuristik Anda yang mendukung model open-source seperti Nous Hermes 3 dan Llama, lengkap dengan mode suara Hi Nova."
            }
            else -> {
                "Saya menerima pesan Anda: \"$prompt\". Saat ini saya berjalan dalam mode asisten lokal ringan."
            }
        }

        return if (reasonNote != null) {
            "$coreReply\n\n[$reasonNote]"
        } else {
            coreReply
        }
    }

    private fun readBatteryPercent(): Int {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100) / scale else -1
    }

    private fun evaluateSimpleMath(expr: String): Double? {
        return runCatching {
            val clean = expr.replace(" ", "").replace(",", ".")
            object {
                var pos = -1
                var ch = 0
                fun nextChar() { ch = if (++pos < clean.length) clean[pos].code else -1 }
                fun eat(charToEat: Int): Boolean {
                    while (ch == ' '.code) nextChar()
                    if (ch == charToEat) { nextChar(); return true }
                    return false
                }
                fun parse(): Double {
                    nextChar()
                    val x = parseExpression()
                    if (pos < clean.length) throw IllegalArgumentException("Unexpected char")
                    return x
                }
                fun parseExpression(): Double {
                    var x = parseTerm()
                    while (true) {
                        when {
                            eat('+'.code) -> x += parseTerm()
                            eat('-'.code) -> x -= parseTerm()
                            else -> return x
                        }
                    }
                }
                fun parseTerm(): Double {
                    var x = parseFactor()
                    while (true) {
                        when {
                            eat('*'.code) -> x *= parseFactor()
                            eat('/'.code) -> x /= parseFactor()
                            else -> return x
                        }
                    }
                }
                fun parseFactor(): Double {
                    if (eat('+'.code)) return parseFactor()
                    if (eat('-'.code)) return -parseFactor()
                    val startPos = pos
                    val x: Double
                    if (eat('('.code)) {
                        x = parseExpression()
                        eat(')'.code)
                    } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                        while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                        x = clean.substring(startPos, pos).toDouble()
                    } else {
                        throw IllegalArgumentException("Unexpected")
                    }
                    return x
                }
            }.parse()
        }.getOrNull()
    }
}
