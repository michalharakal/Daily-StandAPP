package de.jug_da.standapp.llm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LLM backend calling any **OpenAI-compatible** `/v1/chat/completions` endpoint.
 *
 * Works with Ollama, llama.cpp server, vLLM, LM Studio, etc.
 */
class RestApiLLMService(
    private val baseUrl: String = "http://localhost:11434",
    private val modelName: String = "llama3.2:3b",
    private val apiKey: String? = null,
) : LLMService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000 // 2 min for slow local inference
            connectTimeoutMillis = 10_000
        }
    }

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): String {
        val request = ChatCompletionRequest(
            model = modelName,
            messages = listOf(
                MessagePayload(
                    role = "system",
                    content = "You are a helpful assistant that creates concise standup summaries from git commit data."
                ),
                MessagePayload(role = "user", content = prompt)
            ),
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP
        )

        val url = resolveCompletionsUrl(baseUrl)
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            if (!apiKey.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            setBody(request)
        }

        if (response.status.value !in 200..299) {
            val body = response.bodyAsText()
            error("REST API returned ${response.status}: $body")
        }

        val completion: ChatCompletionResponse = response.body()
        return completion.choices.firstOrNull()?.message?.content
            ?: error("REST API returned empty choices")
    }

    private fun resolveCompletionsUrl(baseUrl: String): String {
        val normalized = baseUrl.trimEnd('/')
        return when {
            normalized.endsWith("/v1/chat/completions") -> normalized
            normalized.endsWith("/chat/completions") -> normalized
            normalized.endsWith("/v1") -> "$normalized/chat/completions"
            else -> "$normalized/v1/chat/completions"
        }
    }

    // --- OpenAI chat completion request/response DTOs ---

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<MessagePayload>,
        @SerialName("max_tokens") val maxTokens: Int,
        val temperature: Float,
        @SerialName("top_p") val topP: Float
    )

    @Serializable
    private data class MessagePayload(
        val role: String,
        val content: String
    )

    @Serializable
    private data class ChatCompletionResponse(
        val choices: List<Choice>
    )

    @Serializable
    private data class Choice(
        val message: MessagePayload
    )
}
