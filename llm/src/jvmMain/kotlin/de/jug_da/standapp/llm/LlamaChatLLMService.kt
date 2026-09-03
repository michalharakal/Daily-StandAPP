package de.jug_da.standapp.llm

import de.jug_da.standapp.llm.model.ModelCatalog
import de.jug_da.standapp.llm.model.ModelProvider
import de.jug_da.standapp.llm.model.ModelResolver
import de.jug_da.standapp.llm.model.ModelSpec
import de.jug_da.standapp.llm.pipeline.LlamaGenerateStage
import java.nio.file.Path

/**
 * [LLMService] over the local Llama 3.2 summariser — the SKAINET backend for
 * callers that speak the plain prompt-in/text-out interface (benchmark,
 * `LLMSummarizer`, smoke prompts). The standup CLI drives the same
 * [LlamaGenerateStage] through the pipeline DSL instead.
 */
class LlamaChatLLMService(
    private val models: ModelProvider,
    private val systemPrompt: String = LLMService.SYSTEM_PROMPT,
    onToken: (String) -> Unit = {},
    spec: ModelSpec = ModelCatalog.LLAMA_3_2_3B,
) : LLMService, AutoCloseable {

    private val stage = LlamaGenerateStage(models, spec, onToken)

    override suspend fun generate(prompt: String, maxTokens: Int, temperature: Float, topP: Float): String =
        stage.generate(systemPrompt, prompt, maxTokens, temperature, topP).text

    override fun close() = models.close()

    companion object {
        /**
         * Keeps the model resident across calls; [modelPath] overrides the
         * catalog download (blank → resolver precedence, see [ModelResolver]).
         */
        fun create(
            modelPath: Path? = null,
            systemPrompt: String = LLMService.SYSTEM_PROMPT,
            onToken: ((String) -> Unit)? = null,
        ): LlamaChatLLMService {
            val overrides = modelPath?.let { mapOf(ModelCatalog.LLAMA_3_2_3B to it) } ?: emptyMap()
            val provider = ModelProvider.caching(ModelResolver(), overrides)
            return LlamaChatLLMService(provider, systemPrompt, onToken ?: STDOUT_SINK)
        }

        private val STDOUT_SINK: (String) -> Unit = { token ->
            System.out.print(token)
            System.out.flush()
        }
    }
}
