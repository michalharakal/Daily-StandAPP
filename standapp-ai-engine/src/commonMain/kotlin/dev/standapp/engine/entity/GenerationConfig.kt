package dev.standapp.engine.entity

data class GenerationConfig(
    val maxTokens: Int = 512,
    val temperature: Float = 0.1f,
    val topP: Float = 0.9f,
)
