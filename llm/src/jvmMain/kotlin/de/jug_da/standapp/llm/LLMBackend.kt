package de.jug_da.standapp.llm

/**
 * Supported LLM backend types.
 *
 * All three backends are equal alternatives — set `MCP_LLM_BACKEND` to choose one.
 */
enum class LLMBackendType {
    SKAINET,
    REST_API,
    JLAMA;

    companion object {
        fun fromEnv(): LLMBackendType =
            when (System.getenv("MCP_LLM_BACKEND")?.uppercase()) {
                "SKAINET", "KLLAMA" -> SKAINET
                "REST", "REST_API", "OLLAMA" -> REST_API
                "JLAMA" -> JLAMA
                else -> error(
                    "MCP_LLM_BACKEND is required. Valid options: SKAINET, REST_API, JLAMA"
                )
            }
    }
}
