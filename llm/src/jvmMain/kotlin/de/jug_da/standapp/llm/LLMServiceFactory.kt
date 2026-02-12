package de.jug_da.standapp.llm

/**
 * Factory that instantiates the right [LLMService] based on environment variables.
 *
 * Environment variables:
 * - `MCP_LLM_BACKEND`       – "SKAINET" | "KLLAMA" | "REST" | "REST_API" | "OLLAMA" | "JLAMA" (required)
 * - `MCP_LLM_MODEL_PATH`    – Path to GGUF model file (SKAINET backend)
 * - `MCP_LLM_REST_BASE_URL` – Base URL for REST API backend (default: http://localhost:11434)
 * - `MCP_LLM_REST_MODEL`    – Model name for REST API backend (default: llama3.2:3b)
 */
object LLMServiceFactory {

    fun create(): LLMService {
        val backend = LLMBackendType.fromEnv()
        println("[LLMServiceFactory] Selected backend: $backend")

        return when (backend) {
            LLMBackendType.SKAINET -> {
                val modelPath = System.getenv("MCP_LLM_MODEL_PATH")
                    ?: error("MCP_LLM_MODEL_PATH is required for SKAINET backend")
                println("[LLMServiceFactory] Loading SKaiNET model from: $modelPath")
                SKaiNetLLMService.create(modelPath)
            }

            LLMBackendType.REST_API -> {
                val baseUrl = System.getenv("MCP_LLM_REST_BASE_URL") ?: "http://localhost:11434"
                val model = System.getenv("MCP_LLM_REST_MODEL") ?: "llama3.2:3b"
                println("[LLMServiceFactory] REST API at $baseUrl, model=$model")
                RestApiLLMService(baseUrl = baseUrl, modelName = model)
            }

            LLMBackendType.JLAMA -> {
                println("[LLMServiceFactory] Using JLama backend")
                JLamaService.create(modelPath = "", tokenizerPath = "")
            }
        }
    }
}
