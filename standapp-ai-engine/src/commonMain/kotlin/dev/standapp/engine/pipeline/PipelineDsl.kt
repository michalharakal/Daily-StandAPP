package dev.standapp.engine.pipeline

import dev.standapp.engine.control.DefaultPrompts
import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.PromptType

@DslMarker
annotation class StandupDsl

/**
 * Declare a standup pipeline as code:
 *
 * ```kotlin
 * val pipeline = standupPipeline {
 *     preprocess  { shortIds(7); limit(50) }
 *     prompt      { type = PromptType.JSON }
 *     infer       { _, user -> llm.generate(user, maxTokens, temperature, topP) }
 *     postprocess { parseSummary(); score(); retryOnInvalid(2) }
 * }
 * val result = pipeline.run(commits)
 * ```
 *
 * Only `infer { }` is required; every other stage has a sensible default
 * (identity preprocess, SUMMARY prompt, raw-passthrough postprocess).
 */
fun standupPipeline(block: StandupPipelineBuilder.() -> Unit): StandupPipeline =
    StandupPipelineBuilder().apply(block).build()

@StandupDsl
class StandupPipelineBuilder {
    private var preprocess: (List<CommitInfo>) -> List<CommitInfo> = { it }
    private val promptSpec = PromptSpec()
    private var infer: Infer? = null
    private val post = PostprocessSpec()

    /** Commit-list transformations, applied in declaration order. */
    fun preprocess(block: PreprocessBuilder.() -> Unit) {
        preprocess = PreprocessBuilder().apply(block).build()
    }

    /** Prompt type and templates; defaults come from [DefaultPrompts]. */
    fun prompt(block: PromptSpec.() -> Unit) {
        promptSpec.block()
    }

    /** The LLM call. Required. Exceptions propagate to the caller untouched. */
    fun infer(fn: Infer) {
        infer = fn
    }

    /** Parsing, scoring, and retry behaviour for the model output. */
    fun postprocess(block: PostprocessSpec.() -> Unit) {
        post.block()
    }

    internal fun build(): StandupPipeline {
        val inferFn = checkNotNull(infer) { "standupPipeline requires an infer { } stage" }
        return StandupPipeline(preprocess, promptSpec, inferFn, post)
    }
}

@StandupDsl
class PreprocessBuilder {
    private val steps = mutableListOf<(List<CommitInfo>) -> List<CommitInfo>>()

    /** Truncate commit ids (e.g. to 7 chars) — smaller prompts, stable references. */
    fun shortIds(length: Int = 7) {
        steps += { commits -> commits.map { it.copy(id = it.id.take(length)) } }
    }

    /** Keep only the first [n] commits (git log order: newest first). */
    fun limit(n: Int) {
        steps += { it.take(n) }
    }

    /**
     * Reduce each commit message to its first line, capped at [maxLength]
     * chars. Real-world commit bodies run to paragraphs — on a small local
     * model that multiplies prefill cost (each prompt token is a full
     * forward pass) without adding summarisable signal.
     */
    fun firstMessageLine(maxLength: Int = 120) {
        steps += { commits ->
            commits.map { commit ->
                commit.copy(message = commit.message.lineSequence().first().take(maxLength))
            }
        }
    }

    fun filter(predicate: (CommitInfo) -> Boolean) {
        steps += { it.filter(predicate) }
    }

    internal fun build(): (List<CommitInfo>) -> List<CommitInfo> =
        { commits -> steps.fold(commits) { acc, step -> step(acc) } }
}

@StandupDsl
class PromptSpec {
    var type: PromptType = PromptType.SUMMARY
    var system: String = DefaultPrompts.SYSTEM
    var summaryTemplate: String = DefaultPrompts.SUMMARY_USER
    var jsonTemplate: String = DefaultPrompts.JSON_USER
}

@StandupDsl
class PostprocessSpec {
    internal var parseEnabled = false
    internal var scoringEnabled = false
    internal var maxAttempts = 1

    /** Parse raw output into [dev.standapp.engine.entity.StandupSummary] (lenient unless retryOnInvalid is set). */
    fun parseSummary() {
        parseEnabled = true
    }

    /** Run [dev.standapp.engine.control.QualityScorer] against the pipeline's own input commit ids. */
    fun score() {
        scoringEnabled = true
    }

    /**
     * Re-run inference when strict parsing fails, up to [maxAttempts] total
     * attempts; the final attempt parses leniently so a run never throws on
     * malformed model output.
     */
    fun retryOnInvalid(maxAttempts: Int = 2) {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
        this.maxAttempts = maxAttempts
    }
}
