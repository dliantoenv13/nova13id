package com.example.data.remote

enum class AiProvider(val label: String) {
    OPENROUTER("OpenRouter"),
    GROQ("Groq"),
    LOCAL("Lokal")
}

data class AiModelOption(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val provider: AiProvider,
    val description: String
)

object AiModelCatalog {
    val HERMES_3_FREE = AiModelOption(
        id = "nousresearch/hermes-3-llama-3.1-405b:free",
        displayName = "Nous Hermes 3 405B",
        shortBadge = "Hermes 3 · Free",
        provider = AiProvider.OPENROUTER,
        description = "Model open-source unggulan Nous Research di OpenRouter (Gratis)"
    )

    val OPENROUTER_AUTO_FREE = AiModelOption(
        id = "openrouter/free",
        displayName = "OpenRouter Free Auto",
        shortBadge = "OR Auto · Free",
        provider = AiProvider.OPENROUTER,
        description = "Router otomatis model open-source gratis tercepat"
    )

    val GROQ_LLAMA_70B = AiModelOption(
        id = "llama-3.3-70b-versatile",
        displayName = "Llama 3.3 70B",
        shortBadge = "Llama 70B · Groq",
        provider = AiProvider.GROQ,
        description = "Inferensi super cepat & gratis via Groq LPU"
    )

    val GROQ_LLAMA_8B = AiModelOption(
        id = "llama-3.1-8b-instant",
        displayName = "Llama 3.1 8B Instant",
        shortBadge = "Llama 8B · Groq",
        provider = AiProvider.GROQ,
        description = "Respons ultra-ringan untuk voice assistant cepat"
    )

    val LOCAL_LITE = AiModelOption(
        id = "nova-local-lite",
        displayName = "Nova Local Assistant",
        shortBadge = "Offline · Lokal",
        provider = AiProvider.LOCAL,
        description = "Asisten offline ringan tanpa kuota internet"
    )

    val ALL_MODELS = listOf(
        HERMES_3_FREE,
        OPENROUTER_AUTO_FREE,
        GROQ_LLAMA_70B,
        GROQ_LLAMA_8B,
        LOCAL_LITE
    )

    val DEFAULT_MODEL = HERMES_3_FREE

    fun findById(id: String): AiModelOption {
        return ALL_MODELS.firstOrNull { it.id == id } ?: DEFAULT_MODEL
    }
}
