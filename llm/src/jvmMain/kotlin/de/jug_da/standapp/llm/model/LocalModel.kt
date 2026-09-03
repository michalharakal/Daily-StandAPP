package de.jug_da.standapp.llm.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.ainet.apps.kllama.chat.ModelMetadata
import sk.ainet.apps.kllama.chat.ModelMetadataExtraction
import sk.ainet.apps.llm.OptimizedLLMMode
import sk.ainet.apps.llm.OptimizedLLMRuntime
import sk.ainet.apps.llm.Tokenizer
import sk.ainet.apps.llm.tokenizer.TokenizerFactory
import sk.ainet.context.DirectCpuExecutionContext
import sk.ainet.io.JvmRandomAccessSource
import sk.ainet.io.gguf.StreamingGGUFReader
import sk.ainet.lang.nn.dsl.decoder.DecoderGgufWeightLoader
import sk.ainet.lang.nn.dsl.decoder.GgufDecoderMetadata
import sk.ainet.lang.tensor.data.MemorySegmentTensorDataFactory
import sk.ainet.lang.types.FP32
import sk.ainet.models.llama.LlamaNetworkLoader
import sk.ainet.models.qwen.QwenNetworkLoader
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * A loaded GGUF model: runtime, tokenizer, metadata and the stop-token set,
 * ready for the agent loop (Qwen) or plain generation (Llama).
 *
 * Loading follows the SKaiNET-transformers DSL path (the same one
 * `skainet-cli` and `QwenToolCallSmokeTest` use): streaming GGUF weights are
 * kept packed and memory-mapped, the family-specific network loader builds
 * the module, and [OptimizedLLMRuntime] executes it in DIRECT mode.
 */
class LocalModel private constructor(
    val spec: ModelSpec,
    val path: Path,
    val runtime: OptimizedLLMRuntime<FP32>,
    val tokenizer: Tokenizer,
    val chatMetadata: ModelMetadata,
    val ggufMetadata: GgufDecoderMetadata,
    /** Every token id that ends a turn; use with `generateUntilStop(eosTokenIds = …)`. */
    val stopTokenIds: Set<Int>,
    /** The single most specific end-of-turn id (agent loop takes one). */
    val primaryEosTokenId: Int,
    private val tensorFactory: MemorySegmentTensorDataFactory,
) : AutoCloseable {

    /** `usedFloats/overflowBytes` of the last forward step's activation slab, for `[STAGE/SCOPE]` logs. */
    fun scopeReport(): String {
        val scope = runtime.forwardScopeMetrics ?: return "scope=off"
        return "slabUsed=${scope.usedFloats}/${scope.slabFloats} floats overflow=${scope.overflowBytes / (1L shl 20)} MB"
    }

    /** Longest prompt + generation the runtime's KV cache accommodates. */
    val inferenceWindow: Int get() = minOf(ggufMetadata.contextLength, MAX_INFERENCE_LEN)

    override fun close() {
        tensorFactory.close()
    }

    companion object {
        /** Mirrors the default `maxInferenceLen` of `llamaNetwork()` / `qwenNetwork()`. */
        const val MAX_INFERENCE_LEN = 4096

        suspend fun load(
            spec: ModelSpec,
            path: Path,
            log: (String) -> Unit = System.err::println,
        ): LocalModel = withContext(Dispatchers.Default) {
            require(path.exists()) { "${spec.id}: model file does not exist: $path" }
            KernelSetup.ensureInstalled(log)

            val chatMetadata = JvmRandomAccessSource.open(path.toString()).use { source ->
                StreamingGGUFReader.open(source).use { reader ->
                    ModelMetadataExtraction.fromGgufFields(reader.fields)
                }
            }
            val architecture = chatMetadata.architecture
            require(architecture != null && architecture in spec.family.acceptedArchitectures) {
                "${spec.id}: $path has architecture '$architecture', expected one of ${spec.family.acceptedArchitectures}"
            }
            val tokenizer = JvmRandomAccessSource.open(path.toString()).use { source ->
                TokenizerFactory.fromGgufSource(source)
            }

            val started = System.nanoTime()
            log("[MODEL LOAD] ${spec.id}: arch=$architecture family=${chatMetadata.family} path=$path")
            val tensorFactory = MemorySegmentTensorDataFactory()
            val ctx = DirectCpuExecutionContext(tensorDataFactory = tensorFactory)
            val loader = DecoderGgufWeightLoader(
                randomAccessProvider = { JvmRandomAccessSource.open(path.toString()) },
                acceptedArchitectures = spec.family.acceptedArchitectures,
            )
            val weights = loader.loadToMapStreaming<FP32, Float>(ctx)
            val module = when (spec.family) {
                ModelFamily.QWEN -> QwenNetworkLoader.fromWeights(weights)
                ModelFamily.LLAMA -> LlamaNetworkLoader.fromWeights(weights)
            }
            val slabFloats = SlabSettings.fromEnv()
            val runtime = OptimizedLLMRuntime(
                model = module,
                ctx = ctx,
                mode = OptimizedLLMMode.DIRECT,
                dtype = FP32::class,
                bos = weights.metadata.bosTokenId,
                forwardSlabFloats = slabFloats,
            )

            val turnEnders = when (spec.family) {
                ModelFamily.QWEN -> listOf("<|im_end|>", "<|endoftext|>")
                ModelFamily.LLAMA -> listOf("<|eot_id|>", "<|end_of_text|>")
            }
            val specialIds = turnEnders.mapNotNull { tokenizer.encode(it).singleOrNull() }
            val stops = (specialIds + tokenizer.eosTokenId).toSet()
            val primaryEos = specialIds.firstOrNull() ?: tokenizer.eosTokenId

            val ms = (System.nanoTime() - started) / 1_000_000
            log(
                "[MODEL LOAD] ${spec.id}: done in ${ms}ms — layers=${weights.metadata.blockCount} " +
                    "ctx=${weights.metadata.contextLength} (window ${minOf(weights.metadata.contextLength, MAX_INFERENCE_LEN)}) " +
                    "vocab=${weights.metadata.vocabSize} bos=${weights.metadata.bosTokenId} stop=$stops slabFloats=$slabFloats"
            )
            LocalModel(spec, path, runtime, tokenizer, chatMetadata, weights.metadata, stops, primaryEos, tensorFactory)
        }
    }
}
