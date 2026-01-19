package de.jug_da.standapp.llm

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import sk.ainet.apps.kllama.GGUFTokenizer
import sk.ainet.apps.kllama.LlamaIngestion
import sk.ainet.apps.kllama.LlamaRuntime
import sk.ainet.apps.kllama.createOptimalKvCache
import sk.ainet.lang.DirectCpuExecutionContext

class SkainetKLlamaService private constructor(
    private val runtime: LlamaRuntime,
    private val tokenizer: GGUFTokenizer,
    private val config: SkainetConfig
) : LLMService {

    private val mutex = Mutex()

    companion object {
        fun create(config: SkainetConfig): SkainetKLlamaService = runBlocking {
            val ctx = DirectCpuExecutionContext()
            val ingestion = LlamaIngestion(ctx)

            val weights = ingestion.load {
                SystemFileSystem.source(Path(config.modelPath)).buffered()
            }

            val tokenizer = GGUFTokenizer.fromSource(
                SystemFileSystem.source(Path(config.modelPath)).buffered()
            )

            val kvCache = createOptimalKvCache(
                nLayers = weights.nLayers,
                seqLen = config.maxSeqLen,
                kvDim = weights.kvDim
            )

            val runtime = LlamaRuntime(
                ctx = ctx,
                weights = weights,
                kvCache = kvCache,
                ropeFreqBase = config.ropeFreqBase,
                eps = config.eps
            )

            SkainetKLlamaService(runtime, tokenizer, config)
        }
    }

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): String = mutex.withLock {
        runtime.reset()

        val promptTokens = tokenizer.encode(prompt)
        val outputBuilder = StringBuilder()
        var tokenCount = 0

        runtime.generate(
            prompt = promptTokens,
            steps = promptTokens.size + maxTokens,
            temperature = temperature
        ) { tokenId ->
            if (tokenCount >= promptTokens.size) {
                outputBuilder.append(tokenizer.decode(tokenId))
            }
            tokenCount++
        }

        outputBuilder.toString()
    }

    fun generateStream(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        onToken: (String) -> Unit
    ) = runBlocking {
        mutex.withLock {
            runtime.reset()
            val promptTokens = tokenizer.encode(prompt)
            var tokenCount = 0

            runtime.generate(promptTokens, promptTokens.size + maxTokens, temperature) { tokenId ->
                if (tokenCount >= promptTokens.size) {
                    onToken(tokenizer.decode(tokenId))
                }
                tokenCount++
            }
        }
    }
}
