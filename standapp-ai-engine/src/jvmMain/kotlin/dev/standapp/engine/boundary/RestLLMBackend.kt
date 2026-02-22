package dev.standapp.engine.boundary

import dev.standapp.engine.entity.GenerationConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RestLLMBackend(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "llama3.2:3b",
    private val apiKey: String? = null,
    private val requestTimeoutMs: Long = 120_000,
    private val connectTimeoutMs: Long = 10_000,
) : LLMBackend {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonParser)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeoutMs
            connectTimeoutMillis = connectTimeoutMs
        }
    }

    override suspend fun generate(prompt: String, config: GenerationConfig): String {
        val request = ChatCompletionRequest(
            model = model,
            messages = buildMessages(prompt),
            maxTokens = config.maxTokens,
            temperature = config.temperature,
            topP = config.topP,
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

    override fun generateStream(prompt: String, config: GenerationConfig): Flow<String> = flow {
        val request = StreamChatCompletionRequest(
            model = model,
            messages = buildMessages(prompt),
            maxTokens = config.maxTokens,
            temperature = config.temperature,
            topP = config.topP,
            stream = true,
        )

        val url = resolveCompletionsUrl(baseUrl)
        client.preparePost(url) {
            contentType(ContentType.Application.Json)
            if (!apiKey.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            setBody(jsonParser.encodeToString(StreamChatCompletionRequest.serializer(), request))
        }.execute { response ->
            if (response.status.value !in 200..299) {
                val body = response.bodyAsText()
                error("REST API returned ${response.status}: $body")
            }

            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                val delta = try {
                    val chunk = jsonParser.decodeFromString(StreamChatCompletionResponse.serializer(), data)
                    chunk.choices.firstOrNull()?.delta?.content
                } catch (_: Exception) {
                    null
                }
                if (delta != null) {
                    emit(delta)
                }
            }
        }
    }

    private fun buildMessages(prompt: String) = listOf(
        MessagePayload(role = "system", content = "You are a helpful assistant that creates concise standup summaries from git commit data."),
        MessagePayload(role = "user", content = prompt),
    )

    private fun resolveCompletionsUrl(baseUrl: String): String {
        val normalized = baseUrl.trimEnd('/')
        return when {
            normalized.endsWith("/v1/chat/completions") -> normalized
            normalized.endsWith("/chat/completions") -> normalized
            normalized.endsWith("/v1") -> "$normalized/chat/completions"
            else -> "$normalized/v1/chat/completions"
        }
    }

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<MessagePayload>,
        @SerialName("max_tokens") val maxTokens: Int,
        val temperature: Float,
        @SerialName("top_p") val topP: Float,
    )

    @Serializable
    private data class StreamChatCompletionRequest(
        val model: String,
        val messages: List<MessagePayload>,
        @SerialName("max_tokens") val maxTokens: Int,
        val temperature: Float,
        @SerialName("top_p") val topP: Float,
        val stream: Boolean = true,
    )

    @Serializable
    private data class MessagePayload(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class ChatCompletionResponse(
        val choices: List<Choice>,
    )

    @Serializable
    private data class Choice(
        val message: MessagePayload,
    )

    @Serializable
    private data class StreamChatCompletionResponse(
        val choices: List<StreamChoice>,
    )

    @Serializable
    private data class StreamChoice(
        val delta: DeltaPayload,
    )

    @Serializable
    private data class DeltaPayload(
        val content: String? = null,
    )
}
