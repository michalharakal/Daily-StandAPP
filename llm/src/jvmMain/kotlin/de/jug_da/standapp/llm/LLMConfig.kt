package de.jug_da.standapp.llm

/**
 * Programmatic configuration for creating [LLMService] instances
 * without relying on environment variables.
 *
 * @param modelPath              Local GGUF for the SKAINET summariser (Llama 3.2 3B); blank = resolve/download
 * @param deliveranceModelOwner  HuggingFace model owner (DELIVERANCE)
 * @param deliveranceModelName   HuggingFace model name (DELIVERANCE)
 * @param baseUrl                REST endpoint URL (REST_API backend)
 * @param modelName              Model identifier for REST API
 * @param apiKey                 Optional Bearer token for authenticated REST APIs
 */
data class LLMConfig(
    val modelPath: String = "",
    val deliveranceModelOwner: String = "TinyLlama",
    val deliveranceModelName: String = "TinyLlama-1.1B-Chat-v1.0",
    val baseUrl: String = "http://localhost:11434",
    val modelName: String = "llama3.2:3b",
    val apiKey: String? = null,
)
