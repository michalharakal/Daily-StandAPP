package de.jug_da.standapp.llm.pipeline

import de.jug_da.standapp.llm.model.LocalModel
import de.jug_da.standapp.llm.model.ModelCatalog
import de.jug_da.standapp.llm.model.ModelProvider
import de.jug_da.standapp.llm.model.ModelSpec
import de.jug_da.standapp.llm.model.PrefillSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.ainet.apps.kllama.chat.ChatMessage
import sk.ainet.apps.kllama.chat.ChatRole
import sk.ainet.apps.kllama.chat.Llama3ChatTemplate
import sk.ainet.apps.llm.GenerateResult
import sk.ainet.apps.llm.PrefillStrategy
import sk.ainet.apps.llm.generateUntilStop
import sk.ainet.data.source.PipelineStage
import kotlin.random.Random

/**
 * Summarisation stage on Llama 3.2 Instruct.
 *
 * Renders system + user through [Llama3ChatTemplate] (no tools), prefills in
 * batches and generates until any Llama 3 end-of-turn token. Tokens stream to
 * [onToken] as they are produced.
 */
class LlamaGenerateStage(
    private val models: ModelProvider,
    private val spec: ModelSpec = ModelCatalog.LLAMA_3_2_3B,
    private val onToken: (String) -> Unit = {},
    private val random: Random = Random.Default,
    private val prefill: PrefillStrategy = PrefillSettings.fromEnv(),
    /**
     * Top-k cut applied whenever nucleus sampling is active. Upstream's
     * `sampleFromLogits(topK = 0, topP < 1)` uses k = vocab size and its
     * insertion loop goes quadratic over Llama's 128k vocabulary (~10 s per
     * token measured), so top-p is always paired with a finite k here.
     */
    private val topK: Int = DEFAULT_TOP_K,
) : PipelineStage<PromptedBatch, RawOutput> {

    override val name: String = "llama"
    private var attempt = 0

    override suspend fun process(input: PromptedBatch): RawOutput {
        attempt++
        val r = input.batch.request
        val result = generate(input.prompt.system, input.prompt.user, r.maxTokens, r.temperature, r.topP, attempt)
        return RawOutput(input, result.text, attempt)
    }

    /** Core generation, shared with [de.jug_da.standapp.llm.LlamaChatLLMService]. */
    suspend fun generate(
        system: String,
        user: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        attempt: Int = 1,
    ): GenerateResult = models.withModel(spec) { model ->
        withContext(Dispatchers.Default) { generateWith(model, system, user, maxTokens, temperature, topP, attempt) }
    }

    private fun generateWith(
        model: LocalModel,
        system: String,
        user: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        attempt: Int,
    ): GenerateResult {
        val rendered = template.apply(
            listOf(ChatMessage(ChatRole.SYSTEM, system), ChatMessage(ChatRole.USER, user)),
            tools = emptyList(),
            addGenerationPrompt = true,
        )
        // <|begin_of_text|> is a control token in the GGUF vocab → single id; do not add BOS again.
        val promptTokens = model.tokenizer.encode(rendered)
        val window = model.inferenceWindow
        require(promptTokens.size < window) {
            "prompt is ${promptTokens.size} tokens but the inference window is $window; " +
                "shorten it with preprocess { limit(n); firstMessageLine(...) }"
        }
        val budget = minOf(maxTokens, window - promptTokens.size - 1)
        if (budget < maxTokens) {
            StageLog.stage("WARN", "maxTokens clamped $maxTokens -> $budget to fit the $window-token window")
        }
        StageLog.stage("INPUT", "model=${model.spec.id} promptTokens=${promptTokens.size} maxTokens=$budget temp=$temperature topP=$topP prefill=$prefill attempt=$attempt")
        StageLog.block("SYSTEM PROMPT", system)
        StageLog.block("USER PROMPT", user)

        model.runtime.reset()
        val started = System.nanoTime()
        var first = true
        var produced = 0
        val result = model.runtime.generateUntilStop(
            prompt = promptTokens,
            maxTokens = budget,
            eosTokenIds = model.stopTokenIds,
            temperature = temperature,
            topK = if (topP < 1f) topK else 0,
            topP = topP,
            random = random,
            onToken = { id ->
                if (first) {
                    StageLog.stage("PREFILL DONE", "first token after ${(System.nanoTime() - started) / 1_000_000}ms")
                    StageLog.stage("SCOPE", "after prefill: ${model.scopeReport()}")
                    first = false
                } else if (produced == 1) {
                    StageLog.stage("SCOPE", "decode step: ${model.scopeReport()}")
                }
                produced++
                onToken(model.tokenizer.decode(id))
            },
            decode = { model.tokenizer.decode(it) },
            onPrefill = { done, total ->
                if (done == total || done % 512 == 0) StageLog.stage("PREFILL", "$done/$total")
            },
            prefillStrategy = prefill,
        )
        val totalMs = (System.nanoTime() - started) / 1_000_000
        val tokSec = if (totalMs > 0) "%.2f".format(produced * 1000.0 / totalMs) else "n/a"
        StageLog.stage("COMPLETE", "$produced tokens in ${totalMs}ms ($tokSec tok/s) stoppedByEos=${result.stoppedByEos}")
        StageLog.block("FINAL ANSWER", result.text)
        return result
    }

    companion object {
        /** llama.cpp's default top-k. */
        const val DEFAULT_TOP_K = 40
        private val template = Llama3ChatTemplate()
    }
}
