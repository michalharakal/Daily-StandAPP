package de.jug_da.standapp.benchmark.engines

import com.codahale.metrics.MetricRegistry
import de.jug_da.standapp.llm.LLMService
import io.teknek.deliverance.DType
import io.teknek.deliverance.generator.GeneratorParameters
import io.teknek.deliverance.model.AbstractModel
import io.teknek.deliverance.model.ModelSupport
import io.teknek.deliverance.math.WrappedForkJoinPool
import io.teknek.deliverance.safetensors.fetch.ModelFetcher
import io.teknek.deliverance.tensor.ArrayQueueTensorAllocator
import io.teknek.deliverance.tensor.KvBufferCacheSettings
import io.teknek.deliverance.tensor.operations.ConfigurableTensorProvider
import io.teknek.deliverance.toolcallparser.DefaultToolCallParser
import java.util.concurrent.ForkJoinPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Benchmark-only LLM backend powered by Deliverance
 * (https://github.com/edwardcapriolo/deliverance).
 *
 * Pure-Java JVM inference engine, used here to demonstrate that the project's
 * `LLMService` abstraction holds across different in-process engines. NOT on
 * the production classpath — this file is only compiled when the benchmark
 * module is built with `-Pdeliverance.enabled=true`. Production code paths
 * stay clean of `io.teknek.deliverance` deps.
 *
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
        topP: Float,
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

        // 0.0.11: ntokens became the TOTAL context budget (prompt must fit
        // inside it, defaults to the model's context length); the generated-
        // token cap is the separate maxTokens parameter.
        val params = GeneratorParameters()
            .withTemperature(temperature)
            .withMaxTokens(maxTokens)

        val response = model.generate(UUID.randomUUID(), ctx, params) { _, _, _, _ -> }
        response.responseText
    }

    companion object {
        /**
         * Load a model from a HuggingFace repo and return a ready-to-use
         * [DeliveranceLLMService]. Reflectively invoked from
         * `BenchmarkEngineRegistry` — keep the signature `(String, String)`.
         */
        @JvmStatic
        fun create(modelOwner: String, modelName: String): DeliveranceLLMService {
            println("[DeliveranceLLMService] Loading model $modelOwner/$modelName ...")
            val fetcher = ModelFetcher(modelOwner, modelName)
            val modelDir = fetcher.maybeDownload()
            return createFromPath(modelDir, fetcher)
        }

        @JvmStatic
        fun createFromPath(modelPath: File, fetcher: ModelFetcher? = null): DeliveranceLLMService {
            println("[DeliveranceLLMService] Loading model from ${modelPath.absolutePath} ...")
            val metricRegistry = MetricRegistry()
            // 0.0.11: TensorCache was replaced by the TensorAllocator interface;
            // tensor ops and model loading now also take an explicit fork-join
            // pool and a ToolCallParser.
            val allocator = ArrayQueueTensorAllocator(metricRegistry)
            val pool = WrappedForkJoinPool(ForkJoinPool.commonPool())
            val model = ModelSupport.loadModel(
                modelPath,
                DType.F32,
                DType.I8,
                ConfigurableTensorProvider(allocator, pool),
                metricRegistry,
                allocator,
                KvBufferCacheSettings(true),
                fetcher,
                DefaultToolCallParser(),
                pool,
            )
            println("[DeliveranceLLMService] Model loaded.")
            return DeliveranceLLMService(model)
        }
    }
}
