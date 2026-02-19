package de.jug_da.standapp.llm

/**
 * Programmatic configuration for creating [LLMService] instances
 * without relying on environment variables.
 *
 * @param modelPath Path to GGUF model file (SKAINET) or empty (JLAMA)
 * @param baseUrl   REST endpoint URL (REST_API backend)
 * @param modelName Model identifier for REST API
 * @param apiKey    Optional Bearer token for authenticated REST APIs
 */
data class LLMConfig(
    val modelPath: String = "",
    val baseUrl: String = "http://localhost:11434",
    val modelName: String = "llama3.2:3b",
    val apiKey: String? = null,
)
