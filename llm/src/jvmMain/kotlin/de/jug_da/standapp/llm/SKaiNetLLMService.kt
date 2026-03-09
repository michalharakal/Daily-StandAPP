package de.jug_da.standapp.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.ainet.apps.kllama.java.GenerationConfig
import sk.ainet.apps.kllama.java.KLlamaJava
import sk.ainet.apps.kllama.java.KLlamaSession
import java.nio.file.Path

/**
 * LLM backend powered by SKaiNET's KLlama (pure Kotlin, no native bindings).
 *
 * Supports both GGUF and SafeTensors model formats via [KLlamaJava].
 * Bridges KLlamaSession's synchronous CPU inference to [LLMService]'s suspend API
 * by running on [Dispatchers.Default] (compute pool).
 */
class SKaiNetLLMService private constructor(
    private val session: KLlamaSession,
) : LLMService {

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float // accepted but unused -- SKaiNET only supports temperature sampling
    ): String = withContext(Dispatchers.Default) {
        val config = GenerationConfig.builder()
            .maxTokens(maxTokens)
            .temperature(temperature)
            .build()

        session.generate(prompt, config)
    }

    companion object {
        /**
         * Load a GGUF or SafeTensors model and return a ready-to-use [SKaiNetLLMService].
         *
         * Detects format automatically:
         * - Files ending in `.gguf` are loaded via [KLlamaJava.loadGGUF]
         * - Directories (containing safetensors files) are loaded via [KLlamaJava.loadSafeTensors]
         */
        fun create(modelPath: String): SKaiNetLLMService {
            val path = Path.of(modelPath)
            val isGGUF = modelPath.endsWith(".gguf", ignoreCase = true)

            println("[SKaiNetLLMService] Loading ${if (isGGUF) "GGUF" else "SafeTensors"} model from $modelPath...")
            val session = if (isGGUF) {
                KLlamaJava.loadGGUF(path)
            } else {
                KLlamaJava.loadSafeTensors(path)
            }

            println("[SKaiNetLLMService] Model loaded.")
            return SKaiNetLLMService(session)
        }
    }
}
