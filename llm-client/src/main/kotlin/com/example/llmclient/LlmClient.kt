package com.example.llmclient

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.Closeable

class LlmClient(
    private val baseUrl: String = "http://localhost:8080",
) : Closeable {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    /**
     * POST /v1/chat/completions
     */
    suspend fun chatCompletion(request: ChatCompletionRequest): ChatCompletionResponse {
        val response = httpClient.post("$baseUrl/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.body()
    }

    /**
     * Convenience overload: send a single user message with a given model.
     */
    suspend fun chatCompletion(
        model: String,
        userMessage: String,
        systemMessage: String? = null,
    ): ChatCompletionResponse {
        val messages = buildList {
            if (systemMessage != null) {
                add(ChatMessage(role = "system", content = systemMessage))
            }
            add(ChatMessage(role = "user", content = userMessage))
        }
        return chatCompletion(ChatCompletionRequest(model = model, messages = messages))
    }

    /**
     * GET /v1/models
     */
    suspend fun listModels(): ModelsResponse {
        val response = httpClient.get("$baseUrl/v1/models")
        return response.body()
    }

    override fun close() {
        httpClient.close()
    }
}
