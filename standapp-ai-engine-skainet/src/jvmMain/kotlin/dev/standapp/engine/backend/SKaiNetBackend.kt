package dev.standapp.engine.backend

import dev.standapp.engine.boundary.LLMBackend
import dev.standapp.engine.entity.GenerationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.ainet.apps.kllama.CpuAttentionBackend
import sk.ainet.apps.kllama.GGUFTokenizer
import sk.ainet.apps.kllama.LlamaIngestion
import sk.ainet.apps.kllama.LlamaLoadConfig
import sk.ainet.apps.kllama.LlamaRuntime
import sk.ainet.apps.kllama.agent.generateUntilStop
import sk.ainet.apps.kllama.chat.ChatMessage
import sk.ainet.apps.kllama.chat.ChatRole
import sk.ainet.apps.kllama.chat.Llama3ChatTemplate
import sk.ainet.io.JvmRandomAccessSource
import sk.ainet.io.gguf.llama.LlamaWeightLoader
import sk.ainet.lang.nn.DefaultNeuralNetworkExecutionContext
import sk.ainet.lang.types.FP32

/**
 * LLM backend powered by SKaiNET's KLlama (pure Kotlin, no native bindings).
 *
 * Runs inference on [Dispatchers.Default] (CPU compute pool).
 */
class SKaiNetBackend private constructor(
    private val runtime: LlamaRuntime<FP32>,
    private val tokenizer: GGUFTokenizer,
    private val chatTemplate: Llama3ChatTemplate = Llama3ChatTemplate(),
) : LLMBackend {

    override suspend fun generate(prompt: String, config: GenerationConfig): String =
        withContext(Dispatchers.Default) {
            runtime.reset()

            val messages = listOf(
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = "You are a helpful assistant that creates concise standup summaries from git commit data.",
                ),
                ChatMessage(role = ChatRole.USER, content = prompt),
            )

            val formattedPrompt = chatTemplate.apply(
                messages = messages,
                tools = emptyList(),
                addGenerationPrompt = true,
            )

            val promptTokens = tokenizer.encode(formattedPrompt)

            val result = runtime.generateUntilStop(
                prompt = promptTokens,
                maxTokens = config.maxTokens,
                eosTokenId = tokenizer.eosId,
                temperature = config.temperature,
                decode = { tokenizer.decode(it) },
            )

            result.text
        }

    companion object {
        /**
         * Load a GGUF model and return a ready-to-use [SKaiNetBackend].
         *
         * Uses streaming mode so only metadata is loaded upfront;
         * tensor data is loaded on demand during inference.
         */
        fun create(modelPath: String): SKaiNetBackend {
            val ctx = DefaultNeuralNetworkExecutionContext()

            val ingestion = LlamaIngestion<FP32>(
                ctx = ctx,
                dtype = FP32::class,
                config = LlamaLoadConfig(
                    quantPolicy = LlamaWeightLoader.QuantPolicy.DEQUANTIZE_TO_FP32,
                    allowQuantized = false,
                ),
            )

            println("[SKaiNetBackend] Loading GGUF model from $modelPath (streaming)...")
            val weights = kotlinx.coroutines.runBlocking {
                ingestion.loadStreaming {
                    JvmRandomAccessSource.open(modelPath)
                }
            }

            val backend = CpuAttentionBackend<FP32>(ctx, weights, FP32::class)
            val runtime = LlamaRuntime<FP32>(ctx, weights, backend, FP32::class)

            val tokenizer = JvmRandomAccessSource.open(modelPath).use { source ->
                GGUFTokenizer.fromRandomAccessSource(source)
            }

            println("[SKaiNetBackend] Model loaded. Vocab size: ${tokenizer.vocabSize}")
            return SKaiNetBackend(runtime, tokenizer)
        }
    }
}
