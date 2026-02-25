package com.example.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Request types ---

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Double? = null,
    val n: Int? = null,
    val stream: Boolean? = null,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

// --- Response types ---

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
    val content: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

// --- Models endpoint ---

@Serializable
data class ModelsResponse(
    val data: List<ModelInfo>,
)

@Serializable
data class ModelInfo(
    val id: String,
    @SerialName("object") val objectType: String = "model",
)

// --- Error types ---

@Serializable
data class ErrorResponse(
    val error: ErrorDetail,
)

@Serializable
data class ErrorDetail(
    val message: String,
    val type: String,
)
