package de.jug_da.standapp.llm

import com.codahale.metrics.MetricRegistry
import io.teknek.deliverance.DType
import io.teknek.deliverance.generator.GeneratorParameters
import io.teknek.deliverance.model.AbstractModel
import io.teknek.deliverance.model.ModelSupport
import io.teknek.deliverance.safetensors.fetch.ModelFetcher
import io.teknek.deliverance.tensor.KvBufferCacheSettings
import io.teknek.deliverance.tensor.TensorCache
import io.teknek.deliverance.tensor.operations.ConfigurableTensorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * LLM backend powered by Deliverance (pure Java inference engine).
 *
 * Runs transformer models in-process on the JVM using Java's Vector API.
 * Bridges Deliverance's synchronous inference to [LLMService]'s suspend API
 * by running on [Dispatchers.Default] (compute pool).
 */
class DeliveranceLLMService private constructor(
    private val model: AbstractModel,
) : LLMService {

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): String = withContext(Dispatchers.Default) {
        val promptSupport = model.promptSupport()
        val ctx = if (promptSupport.isPresent) {
            promptSupport.get().builder()
                .addSystemMessage(LLMService.SYSTEM_PROMPT)
                .addUserMessage(prompt)
                .build()
        } else {
            io.teknek.deliverance.safetensors.prompt.PromptContext.of(prompt)
        }

        val params = GeneratorParameters()
            .withTemperature(temperature)
            .withNtokens(maxTokens)

        val response = model.generate(UUID.randomUUID(), ctx, params) { _, _, _, _ -> }
        response.responseText
    }

    companion object {
        /**
         * Load a model from a HuggingFace repo and return a ready-to-use [DeliveranceLLMService].
         *
         * @param modelOwner HuggingFace model owner (e.g. "TinyLlama")
         * @param modelName  HuggingFace model name (e.g. "TinyLlama-1.1B-Chat-v1.0")
         */
        fun create(modelOwner: String, modelName: String): DeliveranceLLMService {
            println("[DeliveranceLLMService] Loading model $modelOwner/$modelName...")
            val fetcher = ModelFetcher(modelOwner, modelName)
            val modelDir = fetcher.maybeDownload()
            return createFromPath(modelDir, fetcher)
        }

        /**
         * Load a model from a local directory.
         *
         * @param modelPath path to the directory containing model files (config.json, safetensors)
         */
        fun createFromPath(modelPath: File, fetcher: ModelFetcher? = null): DeliveranceLLMService {
            println("[DeliveranceLLMService] Loading model from ${modelPath.absolutePath}...")
            val metricRegistry = MetricRegistry()
            val tensorCache = TensorCache(metricRegistry)
            val model = ModelSupport.loadModel(
                modelPath,
                DType.F32,
                DType.I8,
                ConfigurableTensorProvider(tensorCache),
                metricRegistry,
                tensorCache,
                KvBufferCacheSettings(true),
                fetcher,
            )
            println("[DeliveranceLLMService] Model loaded successfully.")
            return DeliveranceLLMService(model)
        }
    }
}
