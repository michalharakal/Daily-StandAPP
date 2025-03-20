package de.jug_da.standapp.llm

interface LLMService {
    suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): String

    companion object {
        const val DEFAULT_MAX_TOKENS = 512
        const val DEFAULT_TEMPERATURE = 0.1f
        const val DEFAULT_TOP_P = 0.9f
    }
}