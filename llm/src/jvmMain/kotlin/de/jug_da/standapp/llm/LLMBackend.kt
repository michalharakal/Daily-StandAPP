package de.jug_da.standapp.llm

/**
 * Supported LLM backend types.
 *
 * Set `MCP_LLM_BACKEND` to choose one.
 */
enum class LLMBackendType {
    SKAINET,
    REST_API;

    companion object {
        fun fromEnv(): LLMBackendType =
            when (System.getenv("MCP_LLM_BACKEND")?.uppercase()) {
                "SKAINET", "KLLAMA" -> SKAINET
                "REST", "REST_API", "OLLAMA" -> REST_API
                else -> error(
                    "MCP_LLM_BACKEND is required. Valid options: SKAINET, REST_API"
                )
            }
    }
}
