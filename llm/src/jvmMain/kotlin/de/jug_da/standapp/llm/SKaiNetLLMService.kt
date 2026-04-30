package de.jug_da.standapp.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.ainet.apps.kllama.java.GenerationConfig
import sk.ainet.apps.kllama.java.KLlamaJava
import sk.ainet.apps.kllama.java.KLlamaSession
import java.nio.file.Path

/**
 * LLM backend powered by skainet-transformers 0.21.1's [KLlamaSession].
 *
 * Calls [KLlamaSession.generate] directly with streaming token output.
 * The earlier `JavaAgentLoop` path was abandoned because Llama 3.2 1B
 * Q8_0 cannot reliably emit the strict tool-call JSON the Llama 3 chat
 * template expects, and the loop's prompt re-formatting + tool-call
 * parsing on every round caused multi-minute hangs on long
 * summarisation prompts (60+ commits).
 */
class SKaiNetLLMService private constructor(
    private val session: KLlamaSession,
    private val systemPrompt: String,
) : LLMService, AutoCloseable {

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
    ): String = withContext(Dispatchers.Default) {
        // Force temperature ≥ 0.6: at temp 0.1 with this raw-session path
        // the model collapses into repetition ("Goodbye. Goodbye. Goodbye..."),
        // because there's no chat-template structure to steer sampling.
        val effectiveTemperature = maxOf(temperature, 0.6f)
        val config = GenerationConfig.builder()
            .maxTokens(maxTokens)
            .temperature(effectiveTemperature)
            .build()
        val fullPrompt = formatLlama3Turn(systemPrompt, prompt)
        System.err.println("[SKaiNetLLMService] generate(): prompt=${fullPrompt.length} chars, maxTokens=$maxTokens, temp=$effectiveTemperature")
        var tokensProduced = 0
        val started = System.nanoTime()
        val result = session.generate(fullPrompt, config) { token ->
            tokensProduced++
            if (tokensProduced == 1) {
                val ttfb = (System.nanoTime() - started) / 1_000_000
                System.err.println("[SKaiNetLLMService] first token after ${ttfb}ms")
            }
            System.out.print(token)
            System.out.flush()
        }
        val totalMs = (System.nanoTime() - started) / 1_000_000
        System.err.println("[SKaiNetLLMService] done: $tokensProduced tokens in ${totalMs}ms (${"%.2f".format(tokensProduced * 1000.0 / totalMs)} tok/s)")
        result
    }

    /**
     * Wrap [system] + [user] in the Llama 3 instruct turn-tag layout the
     * GGUF tokenizer expects. Without these tags, Llama 3.2 1B treats the
     * input as plain continuation text and degenerates.
     */
    private fun formatLlama3Turn(system: String, user: String): String =
        "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n" +
            "$system<|eot_id|>" +
            "<|start_header_id|>user<|end_header_id|>\n\n" +
            "$user<|eot_id|>" +
            "<|start_header_id|>assistant<|end_header_id|>\n\n"

    override fun close() {
        session.close()
    }

    companion object {

        private const val DEFAULT_SYSTEM_PROMPT: String =
            "You are a helpful assistant that writes concise daily standup " +
                    "summaries from a list of git commits."

        fun create(
            modelPath: Path,
            systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        ): SKaiNetLLMService {
            println("[SKaiNetLLMService] Loading GGUF model from $modelPath …")
            val session = KLlamaJava.loadGGUF(modelPath, null)
            println("[SKaiNetLLMService] Model loaded; raw KLlamaSession path (no agent loop).")
            return SKaiNetLLMService(session, systemPrompt)
        }
    }
}
