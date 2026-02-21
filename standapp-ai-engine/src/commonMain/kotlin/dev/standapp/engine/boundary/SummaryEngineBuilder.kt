package dev.standapp.engine.boundary

import dev.standapp.engine.control.PromptBuilder
import dev.standapp.engine.entity.GenerationConfig
import dev.standapp.engine.entity.PromptType

class PromptConfig {
    var system: String? = null
    private var summaryTemplate: String? = null
    private var jsonTemplate: String? = null

    fun user(type: PromptType, template: String) {
        when (type) {
            PromptType.SUMMARY -> summaryTemplate = template
            PromptType.JSON -> jsonTemplate = template
        }
    }

    internal fun buildPromptBuilder(): PromptBuilder {
        val args = mutableMapOf<String, Any>()
        return PromptBuilder(
            systemPrompt = system ?: dev.standapp.engine.control.DefaultPrompts.SYSTEM,
            summaryTemplate = summaryTemplate ?: dev.standapp.engine.control.DefaultPrompts.SUMMARY_USER,
            jsonTemplate = jsonTemplate ?: dev.standapp.engine.control.DefaultPrompts.JSON_USER,
        )
    }
}

class SummaryEngineBuilder {
    var backend: LLMBackend? = null
    var maxTokens: Int = 512
    var temperature: Float = 0.1f
    var topP: Float = 0.9f
    var scoring: Boolean = false

    private var promptConfig: PromptConfig? = null

    fun prompts(block: PromptConfig.() -> Unit) {
        promptConfig = PromptConfig().apply(block)
    }

    fun build(): SummaryEngine {
        val b = backend ?: error("backend must be set in StandupEngine { ... }")
        val config = GenerationConfig(maxTokens, temperature, topP)
        val promptBuilder = promptConfig?.buildPromptBuilder() ?: PromptBuilder()
        return SummaryEngine(b, promptBuilder, config, scoring)
    }
}

fun StandupEngine(block: SummaryEngineBuilder.() -> Unit): SummaryEngine =
    SummaryEngineBuilder().apply(block).build()
