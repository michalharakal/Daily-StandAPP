package de.jug_da.standapp.llm

/**
 * Supported LLM backend types.
 *
 * Set `MCP_LLM_BACKEND` to choose one.
 */
enum class LLMBackendType {
    SKAINET,
    // DELIVERANCE,  // commented out – using local backends
    REST_API;

    companion object {
        // SKAINET is the default — the Llama 3.2 1B Instruct model is embedded as a
        // JAR resource so the app runs self-contained with no env vars set.
        fun fromEnv(): LLMBackendType = parse(System.getenv("MCP_LLM_BACKEND"))

        /** Shared by `MCP_LLM_BACKEND` and the CLI's `--backend` flag. */
        fun parse(raw: String?): LLMBackendType =
            when (raw?.uppercase()) {
                null, "" -> SKAINET
                "SKAINET", "KLLAMA" -> SKAINET
                "REST", "REST_API", "OLLAMA" -> REST_API
                else -> error("Unknown LLM backend '$raw'. Valid options: SKAINET, REST_API")
            }
    }
}
