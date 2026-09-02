package de.jug_da.standapp.llm

import java.nio.file.Path

/**
 * Factory that instantiates the right [LLMService] based on environment variables.
 *
 * - `MCP_LLM_BACKEND`         – "SKAINET" | "REST_API" (default: SKAINET)
 * - `MCP_LLM_MODEL_PATH`      – Local GGUF for the SKAINET summariser (Llama 3.2 3B). If unset,
 *                               `STANDAPP_LLAMA_MODEL_PATH` is consulted, then the model is
 *                               downloaded into `STANDAPP_MODEL_CACHE_DIR` (default
 *                               `~/.cache/standapp/models`) on first use.
 * - `MCP_LLM_REST_BASE_URL`   – Base URL for the REST API backend (default: http://localhost:11434)
 * - `MCP_LLM_REST_MODEL`      – Model name for the REST API backend (default: llama3.2:3b)
 * - `MCP_LLM_REST_API_KEY`    – Optional Bearer token for authenticated REST API endpoints
 */
object LLMServiceFactory {

    /**
     * Create an [LLMService] from explicit configuration (no env vars).
     * Used by the benchmark module to iterate backends programmatically.
     * [onToken] overrides the SKAINET token sink (default: stdout streaming).
     */
    fun create(
        backendType: LLMBackendType,
        config: LLMConfig,
        onToken: ((String) -> Unit)? = null,
    ): LLMService {
        System.err.println("[LLMServiceFactory] Creating backend: $backendType (programmatic)")
        return when (backendType) {
            LLMBackendType.SKAINET ->
                LlamaChatLLMService.create(
                    modelPath = config.modelPath.takeIf { it.isNotBlank() }?.let(Path::of),
                    onToken = onToken,
                )
            LLMBackendType.REST_API ->
                RestApiLLMService(baseUrl = config.baseUrl, modelName = config.modelName, apiKey = config.apiKey)
        }
    }

    fun create(onToken: ((String) -> Unit)? = null): LLMService {
        val backend = LLMBackendType.fromEnv()
        System.err.println("[LLMServiceFactory] Selected backend: $backend")
        return when (backend) {
            LLMBackendType.SKAINET -> LlamaChatLLMService.create(onToken = onToken)
            LLMBackendType.REST_API -> {
                val baseUrl = System.getenv("MCP_LLM_REST_BASE_URL") ?: "http://localhost:11434"
                val model = System.getenv("MCP_LLM_REST_MODEL") ?: "llama3.2:3b"
                val apiKey = System.getenv("MCP_LLM_REST_API_KEY") ?: System.getenv("OPENAI_API_KEY")
                System.err.println("[LLMServiceFactory] REST API at $baseUrl, model=$model")
                RestApiLLMService(baseUrl = baseUrl, modelName = model, apiKey = apiKey)
            }
        }
    }
}
