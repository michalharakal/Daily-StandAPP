package de.jug_da.standapp.llm

import java.nio.file.Path

/**
 * Factory that instantiates the right [LLMService] based on environment variables.
 *
 * Environment variables:
 * - `MCP_LLM_BACKEND`            – "SKAINET" | "REST_API" (default: SKAINET)
 * - `MCP_LLM_MODEL_PATH`         – Override path to GGUF model file. If unset on the
 *                                   SKAINET backend, the embedded Llama 3.2 1B Instruct
 *                                   resource is extracted to `~/.cache/standapp/models/`.
 * - `MCP_LLM_REST_BASE_URL`      – Base URL for REST API backend (default: http://localhost:11434)
 * - `MCP_LLM_REST_MODEL`         – Model name for REST API backend (default: llama3.2:3b)
 * - `MCP_LLM_REST_API_KEY`       – Optional Bearer token for authenticated REST API endpoints
 */
object LLMServiceFactory {

    /**
     * Create an [LLMService] from explicit configuration (no env vars).
     * Used by the benchmark module to iterate backends programmatically.
     */
    fun create(backendType: LLMBackendType, config: LLMConfig): LLMService {
        println("[LLMServiceFactory] Creating backend: $backendType (programmatic)")
        return when (backendType) {
            LLMBackendType.SKAINET -> {
                val modelPath = if (config.modelPath.isNotBlank()) {
                    Path.of(config.modelPath)
                } else {
                    EmbeddedModelLoader.extract()
                }
                SKaiNetLLMService.create(modelPath)
            }
            LLMBackendType.REST_API -> {
                RestApiLLMService(
                    baseUrl = config.baseUrl,
                    modelName = config.modelName,
                    apiKey = config.apiKey,
                )
            }
        }
    }

    /**
     * Build a SKAINET-backed tool-calling [LLMService] with the git tool
     * registered against [repoDir]. Forces the SKAINET path regardless of
     * `MCP_LLM_BACKEND`, since the agent loop is implemented against
     * [sk.ainet.apps.kllama.java.KLlamaSession].
     */
    fun createToolCallingForGit(
        repoDir: String,
        systemPrompt: String = ToolCallingLLMService.DEFAULT_SYSTEM_PROMPT,
    ): LLMService {
        val override = System.getenv("MCP_LLM_MODEL_PATH")
        val modelPath = if (!override.isNullOrBlank()) {
            println("[LLMServiceFactory] tool-calling: using MCP_LLM_MODEL_PATH override: $override")
            Path.of(override)
        } else {
            EmbeddedModelLoader.extract()
        }
        return ToolCallingLLMService.create(
            modelPath = modelPath,
            tools = listOf(GetRecentCommitsTool(repoDir)),
            systemPrompt = systemPrompt,
        )
    }

    fun create(): LLMService {
        val backend = LLMBackendType.fromEnv()
        println("[LLMServiceFactory] Selected backend: $backend")

        return when (backend) {
            LLMBackendType.SKAINET -> {
                val override = System.getenv("MCP_LLM_MODEL_PATH")
                val modelPath = if (!override.isNullOrBlank()) {
                    println("[LLMServiceFactory] Using MCP_LLM_MODEL_PATH override: $override")
                    Path.of(override)
                } else {
                    EmbeddedModelLoader.extract()
                }
                SKaiNetLLMService.create(modelPath)
            }

            LLMBackendType.REST_API -> {
                val baseUrl = System.getenv("MCP_LLM_REST_BASE_URL") ?: "http://localhost:11434"
                val model = System.getenv("MCP_LLM_REST_MODEL") ?: "llama3.2:3b"
                val apiKey = System.getenv("MCP_LLM_REST_API_KEY") ?: System.getenv("OPENAI_API_KEY")
                println("[LLMServiceFactory] REST API at $baseUrl, model=$model")
                RestApiLLMService(baseUrl = baseUrl, modelName = model, apiKey = apiKey)
            }
        }
    }
}
