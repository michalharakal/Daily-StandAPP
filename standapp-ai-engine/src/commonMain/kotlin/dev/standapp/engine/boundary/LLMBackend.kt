package dev.standapp.engine.boundary

import dev.standapp.engine.entity.GenerationConfig

interface LLMBackend {
    suspend fun generate(prompt: String, config: GenerationConfig): String
}
