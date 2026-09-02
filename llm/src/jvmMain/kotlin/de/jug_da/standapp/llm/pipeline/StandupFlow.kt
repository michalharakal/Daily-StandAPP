package de.jug_da.standapp.llm.pipeline

import de.jug_da.standapp.llm.GitCommitSource
import de.jug_da.standapp.llm.LLMService
import de.jug_da.standapp.llm.model.ModelCatalog
import de.jug_da.standapp.llm.model.ModelProvider
import de.jug_da.standapp.llm.model.ModelSpec
import dev.standapp.engine.pipeline.PipelineResult
import dev.standapp.engine.pipeline.PostprocessSpec
import dev.standapp.engine.pipeline.PreprocessBuilder
import dev.standapp.engine.pipeline.PromptSpec
import sk.ainet.apps.kllama.chat.Tool
import sk.ainet.data.source.DataPipeline
import sk.ainet.data.source.PipelineStage
import sk.ainet.data.source.dataPipeline
import sk.ainet.data.source.stage
import kotlin.random.Random

@DslMarker
annotation class StandupFlowDsl

/**
 * The standup flow as a SKaiNET data pipeline:
 *
 * ```kotlin
 * val flow = standupFlow(models) {
 *     commits = qwenToolCall()                       // or gitCommits()
 *     preprocess { shortIds(7); firstMessageLine(120) }
 *     transform("drop-merges") { b -> b.copy(commits = b.commits.filterNot { it.message.startsWith("Merge") }) }
 *     prompt { type = PromptType.JSON }
 *     summarize(llama())                             // or llmService(RestApiLLMService(...))
 *     postprocess { parseSummary(); score(); retryOnInvalid(2) }
 *     onToken = { print(it) }
 * }
 * val result = flow.execute(StandupRequest(repoDir = "."))
 * ```
 *
 * Every step is a [PipelineStage], so a caller can add a `transform`, wrap a
 * model stage, or register extra tools without touching the built-ins. The
 * result is a plain [DataPipeline] — `describe()` prints the stage chain.
 */
fun standupFlow(
    models: ModelProvider,
    block: StandupFlowBuilder.() -> Unit,
): DataPipeline<StandupRequest, PipelineResult> = StandupFlowBuilder(models).apply(block).build()

@StandupFlowDsl
class StandupFlowBuilder internal constructor(val models: ModelProvider) {

    /** Streaming token sink for the summariser; default discards. */
    var onToken: (String) -> Unit = {}

    /** `StandupRequest -> CommitBatch`. Default: Qwen tool call with direct-git fallback. */
    var commits: PipelineStage<StandupRequest, CommitBatch> = qwenToolCall()

    private val transforms = mutableListOf<PipelineStage<CommitBatch, CommitBatch>>()
    private val promptSpec = PromptSpec()
    private var generate: PipelineStage<PromptedBatch, RawOutput>? = null
    private val post = PostprocessSpec()
    private val finishers = mutableListOf<PipelineStage<PipelineResult, PipelineResult>>()

    // ---- stage factories -------------------------------------------------

    fun qwenToolCall(
        spec: ModelSpec = ModelCatalog.QWEN3_0_6B,
        tools: List<Tool> = emptyList(),
        systemPrompt: String = QwenCommitsStage.DEFAULT_SYSTEM_PROMPT,
        maxTokens: Int = QwenCommitsStage.DEFAULT_MAX_TOKENS,
        source: GitCommitSource = GitCommitSource.jgit(),
    ): PipelineStage<StandupRequest, CommitBatch> =
        QwenCommitsStage(models, spec, source, tools, systemPrompt, maxTokens)

    fun gitCommits(source: GitCommitSource = GitCommitSource.jgit()): PipelineStage<StandupRequest, CommitBatch> =
        GitCommitsStage(source)

    fun llama(
        spec: ModelSpec = ModelCatalog.LLAMA_3_2_3B,
        seed: Long? = null,
    ): PipelineStage<PromptedBatch, RawOutput> =
        LlamaGenerateStage(models, spec, onToken = { onToken(it) }, random = seed?.let { Random(it) } ?: Random.Default)

    fun llmService(service: LLMService, name: String = "llm-service"): PipelineStage<PromptedBatch, RawOutput> =
        LLMServiceGenerateStage(service, name)

    // ---- DSL verbs ---------------------------------------------------------

    /** Commit-list transformations using the engine's `preprocess { }` steps. */
    fun preprocess(block: PreprocessBuilder.() -> Unit) {
        transforms += PreprocessStage(block)
    }

    /** Arbitrary custom stage between commit fetch and prompt rendering. */
    fun transform(name: String, fn: suspend (CommitBatch) -> CommitBatch) {
        transforms += stage(name, process = fn)
    }

    fun prompt(block: PromptSpec.() -> Unit) = promptSpec.block()

    /** The generation stage. Defaults to [llama] when not called. */
    fun summarize(generate: PipelineStage<PromptedBatch, RawOutput>) {
        this.generate = generate
    }

    fun postprocess(block: PostprocessSpec.() -> Unit) = post.block()

    /** Hooks on the final result (write a file, extra scoring, …). */
    fun finish(name: String, fn: suspend (PipelineResult) -> PipelineResult) {
        finishers += stage(name, process = fn)
    }

    internal fun build(): DataPipeline<StandupRequest, PipelineResult> {
        var commitsPipeline: DataPipeline<StandupRequest, CommitBatch> = dataPipeline<StandupRequest>()
            .stage(commits)
            .stage(RequireCommitsStage())
        transforms.forEach { commitsPipeline = commitsPipeline.stage(it) }
        val summarize = RetryingSummarizeStage(
            prompt = PromptStage(promptSpec),
            generate = generate ?: llama(),
            parseEnabled = post.parseEnabled,
            scoringEnabled = post.scoringEnabled,
            maxAttempts = post.maxAttempts,
        )
        var resultPipeline: DataPipeline<StandupRequest, PipelineResult> = commitsPipeline.stage(summarize)
        finishers.forEach { resultPipeline = resultPipeline.stage(it) }
        return resultPipeline
    }
}
