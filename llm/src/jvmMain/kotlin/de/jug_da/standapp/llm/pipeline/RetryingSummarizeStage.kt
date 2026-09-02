package de.jug_da.standapp.llm.pipeline

import dev.standapp.engine.control.QualityScorer
import dev.standapp.engine.control.SummaryParseException
import dev.standapp.engine.control.SummaryParser
import dev.standapp.engine.entity.ScoredResult
import dev.standapp.engine.entity.StandupSummary
import dev.standapp.engine.pipeline.PipelineResult
import sk.ainet.data.source.PipelineStage

/**
 * Composite stage: prompt → generate → parse, re-running generation while
 * strict parsing fails and attempts remain. The final attempt parses
 * leniently so a run never throws on malformed model output.
 *
 * A linear [sk.ainet.data.source.DataPipeline] cannot loop, so the retry
 * lives here; both inner stages stay swappable [PipelineStage]s.
 */
class RetryingSummarizeStage(
    private val prompt: PipelineStage<CommitBatch, PromptedBatch>,
    private val generate: PipelineStage<PromptedBatch, RawOutput>,
    private val parseEnabled: Boolean,
    private val scoringEnabled: Boolean,
    maxAttempts: Int,
) : PipelineStage<CommitBatch, PipelineResult> {

    private val maxAttempts = if (parseEnabled) maxAttempts else 1

    override val name: String = "summarize[${prompt.name} -> ${generate.name} -> parse x$maxAttempts]"

    override fun validate(input: CommitBatch): Boolean = input.commits.isNotEmpty()

    override suspend fun process(input: CommitBatch): PipelineResult {
        val prompted = prompt.process(input)
        val type = input.request.promptType
        val commits = input.commits
        // Defaults for summaries whose format carries no date/author itself:
        // newest commit date (ISO strings sort lexicographically), most frequent author.
        val defaultDate = commits.maxOfOrNull { it.date } ?: ""
        val defaultAuthor = commits.groupingBy { it.authorName }.eachCount().maxByOrNull { it.value }?.key ?: ""

        var attempts = 0
        var raw = ""
        var summary: StandupSummary? = null
        while (attempts < maxAttempts && summary == null) {
            attempts++
            raw = generate.process(prompted).raw
            summary = if (parseEnabled) {
                val lastAttempt = attempts == maxAttempts
                try {
                    SummaryParser.parse(raw, type, defaultDate, defaultAuthor, strict = !lastAttempt)
                } catch (e: SummaryParseException) {
                    StageLog.stage("PARSE", "attempt $attempts rejected: ${e.message}")
                    null
                }
            } else {
                StandupSummary(raw = raw, date = defaultDate, author = defaultAuthor, sections = emptyList(), promptType = type)
            }
        }

        val scores = if (scoringEnabled) QualityScorer.score(raw, type, commits.map { it.id }.toSet()) else null
        return PipelineResult(
            prompt = prompted.prompt.user,
            raw = raw,
            result = ScoredResult(summary = checkNotNull(summary), scores = scores),
            attempts = attempts,
        )
    }
}
