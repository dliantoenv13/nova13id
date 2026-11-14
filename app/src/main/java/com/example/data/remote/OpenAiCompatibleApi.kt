package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

@JsonClass(generateAdapter = true)
data class ChatMessageDto(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double = 0.7,
    @Json(name = "max_tokens") val maxTokens: Int = 700
)

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChoiceDto>? = null
)

@JsonClass(generateAdapter = true)
data class ChoiceDto(
    val index: Int? = null,
    val message: ResponseMessageDto? = null
)

@JsonClass(generateAdapter = true)
data class ResponseMessageDto(
    val role: String? = null,
    val content: String? = null,
    val reasoning: String? = null
)

@JsonClass(generateAdapter = true)
data class TranscriptionResponse(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class ApiErrorEnvelope(
    val error: ApiErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class ApiErrorDetail(
    val message: String? = null
)

interface OpenAiCompatibleApi {
    @POST
    suspend fun createChatCompletion(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") httpReferer: String? = null,
        @Header("X-Title") appTitle: String? = null,
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>

    @Multipart
    @POST
    suspend fun transcribeAudio(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") responseFormat: RequestBody,
        @Part("prompt") prompt: RequestBody
    ): Response<TranscriptionResponse>

    @GET
    suspend fun checkModels(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Response<Map<String, Any>>
}
