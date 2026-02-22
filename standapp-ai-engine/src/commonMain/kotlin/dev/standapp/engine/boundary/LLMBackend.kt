package dev.standapp.engine.boundary

import dev.standapp.engine.entity.GenerationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface LLMBackend {
    suspend fun generate(prompt: String, config: GenerationConfig): String

    fun generateStream(prompt: String, config: GenerationConfig): Flow<String> =
        flow { emit(generate(prompt, config)) }
}
