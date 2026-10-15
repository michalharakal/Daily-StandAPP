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
import sk.ainet.decisions.DecisionAdapter
import sk.ainet.decisions.dsl.DecisionSchema
import sk.ainet.decisions.dsl.DecisionSchemaScope
import sk.ainet.decisions.dsl.decisionSchema
import java.nio.file.Path
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
 *     decide("triage", laya()) {                     // typed decisions before any text
 *         choice("kind", "What kind of change is this?", "feature", "bugfix", "refactor", "docs", "test", "chore")
 *         probability("standup", "Is this commit worth mentioning in the daily stand-up?")
 *     }
 *     dropIrrelevant("standup"); annotateTriage()
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

    /** Laya as the decision model of a [decide] stage; `checkpoint` overrides the env/download resolution. */
    fun laya(checkpoint: Path? = null): DecisionModelSource = layaDecisionModel(checkpoint)

    /** Any [sk.ainet.decisions.DecisionAdapter] (tests, other local models) as a [decide] model. */
    fun decisionModel(modelId: String, adapter: suspend () -> DecisionAdapter): DecisionModelSource =
        DecisionModelSource(modelId, adapter)

    // ---- DSL verbs ---------------------------------------------------------

    /** Commit-list transformations using the engine's `preprocess { }` steps. */
    fun preprocess(block: PreprocessBuilder.() -> Unit) {
        transforms += PreprocessStage(block)
    }

    /** Arbitrary custom stage between commit fetch and prompt rendering. */
    fun transform(name: String, fn: suspend (CommitBatch) -> CommitBatch) {
        transforms += stage(name, process = fn)
    }

    /**
     * Typed decisions per commit before any text is generated: a `sk.ainet.decisions.dsl`
     * schema (`choice` / `score` / `probability` questions, optional `policy { }`) answered by
     * [model], accepted or abstained under the schema's policy ([DEFAULT_TRIAGE_POLICY] when it
     * declares none), stored in [CommitBatch.triage]. Follow with [dropIrrelevant] and
     * [annotateTriage], or read the outcomes in your own `transform`.
     */
    fun decide(name: String = "triage", model: DecisionModelSource, block: DecisionSchemaScope.() -> Unit) {
        val source = model
        decide(name, model, decisionSchema(name) { model(source.modelId); block() })
    }

    /** The same with a schema declared elsewhere, e.g. shared between the CLI and a server. */
    fun decide(name: String = "triage", model: DecisionModelSource, schema: DecisionSchema) {
        transforms += DecisionTriageStage(name, model, schema)
    }

    /** Remove the commits a [decide] stage marked `questionId = no`; abstentions stay. */
    fun dropIrrelevant(questionId: String = "standup", name: String = "relevant") {
        transforms += stage(name, process = { batch: CommitBatch -> batch.dropIrrelevant(questionId) })
    }

    /** Prefix each commit message with its decisions so the summariser sees them. */
    fun annotateTriage(name: String = "annotate-triage") {
        transforms += stage(name, process = { batch: CommitBatch -> batch.annotateTriage() })
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
