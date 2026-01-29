import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// --- Response types (OpenAI Chat Completions format) ---

@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage,
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ResponseMessage,
    @SerialName("finish_reason") val finishReason: String = "stop",
)

@Serializable
data class ResponseMessage(
    val role: String = "assistant",
    val content: String?,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

// --- Server factory ---

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

fun createLocalAIServer(port: Int = 8080): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    return embeddedServer(CIO, port = port) {
        install(ContentNegotiation) {
            json(lenientJson)
        }

        routing {
            post("/v1/chat/completions") {
                try {
                    val bodyText = call.receiveText()
                    System.err.println("[LocalAIServer] Received: $bodyText")

                    val root = lenientJson.parseToJsonElement(bodyText).jsonObject
                    val model = root["model"]?.jsonPrimitive?.content ?: "unknown"
                    val messages = root["messages"]?.jsonArray ?: emptyList()

                    val lastUserContent = messages
                        .lastOrNull { it.jsonObject["role"]?.jsonPrimitive?.content == "user" }
                        ?.jsonObject?.get("content")
                        ?.let { element ->
                            try {
                                element.jsonPrimitive.content
                            } catch (_: Exception) {
                                element.toString()
                            }
                        } ?: "No user message provided"

                    val responseText = "This is a local stub response to: $lastUserContent"

                    val response = ChatCompletionResponse(
                        id = "chatcmpl-local-${System.currentTimeMillis()}",
                        created = System.currentTimeMillis() / 1000,
                        model = model,
                        choices = listOf(
                            Choice(message = ResponseMessage(content = responseText))
                        ),
                        usage = Usage(
                            promptTokens = lastUserContent.length,
                            completionTokens = responseText.length,
                            totalTokens = lastUserContent.length + responseText.length,
                        ),
                    )

                    call.respond(response)
                } catch (e: Exception) {
                    System.err.println("[LocalAIServer] ERROR: ${e.message}")
                    e.printStackTrace(System.err)
                    call.respondText(
                        """{"error":{"message":"${e.message?.replace("\"", "'")}","type":"server_error"}}""",
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError
                    )
                }
            }

            get("/v1/models") {
                call.respond(mapOf("data" to listOf(mapOf("id" to "local-stub", "object" to "model"))))
            }
        }
    }
}
