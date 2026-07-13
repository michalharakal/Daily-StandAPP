package dev.standapp.engine.pipeline

import dev.standapp.engine.control.PromptBuilder
import dev.standapp.engine.control.QualityScorer
import dev.standapp.engine.control.SummaryParseException
import dev.standapp.engine.control.SummaryParser
import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.ScoredResult
import dev.standapp.engine.entity.StandupSummary

/**
 * The inference stage, injected as code. The engine module stays free of
 * LLM/git dependencies — callers wire in `LLMService.generate`, a REST
 * call, or a test fake.
 */
typealias Infer = suspend (system: String, user: String) -> String

/** Everything a pipeline run produced, from prompt to scored summary. */
data class PipelineResult(
    /** The final user prompt sent to the model. */
    val prompt: String,
    /** Raw LLM output of the last attempt. */
    val raw: String,
    /** Parsed summary plus optional quality scores. */
    val result: ScoredResult,
    /** 1 unless retryOnInvalid re-ran inference. */
    val attempts: Int,
)

/**
 * A typed standup pipeline: preprocess → prompt → infer → postprocess.
 * Built via the [standupPipeline] DSL; immutable and reusable across runs.
 */
class StandupPipeline internal constructor(
    private val preprocess: (List<CommitInfo>) -> List<CommitInfo>,
    private val promptSpec: PromptSpec,
    private val infer: Infer,
    private val post: PostprocessSpec,
) {

    suspend fun run(commits: List<CommitInfo>): PipelineResult {
        val processed = preprocess(commits)
        val builder = PromptBuilder(promptSpec.system, promptSpec.summaryTemplate, promptSpec.jsonTemplate)
        val system = builder.buildSystemPrompt()
        val user = builder.buildUserPrompt(processed, promptSpec.type)

        // Defaults for summaries whose format carries no date/author itself:
        // newest commit date (ISO strings sort lexicographically), most
        // frequent author.
        val defaultDate = processed.maxOfOrNull { it.date } ?: ""
        val defaultAuthor = processed.groupingBy { it.authorName }.eachCount()
            .maxByOrNull { it.value }?.key ?: ""

        val maxAttempts = if (post.parseEnabled) post.maxAttempts else 1
        var attempts = 0
        var raw = ""
        var summary: StandupSummary? = null

        while (attempts < maxAttempts && summary == null) {
            attempts++
            raw = infer(system, user)
            summary = if (post.parseEnabled) {
                val lastAttempt = attempts == maxAttempts
                try {
                    SummaryParser.parse(raw, promptSpec.type, defaultDate, defaultAuthor, strict = !lastAttempt)
                } catch (e: SummaryParseException) {
                    null // strict failure with attempts left → re-infer
                }
            } else {
                StandupSummary(
                    raw = raw, date = defaultDate, author = defaultAuthor,
                    sections = emptyList(), promptType = promptSpec.type,
                )
            }
        }

        val scores = if (post.scoringEnabled) {
            QualityScorer.score(raw, promptSpec.type, processed.map { it.id }.toSet())
        } else {
            null
        }

        return PipelineResult(
            prompt = user,
            raw = raw,
            result = ScoredResult(summary = checkNotNull(summary), scores = scores),
            attempts = attempts,
        )
    }
}
