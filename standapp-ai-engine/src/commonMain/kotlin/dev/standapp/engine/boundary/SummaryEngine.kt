package dev.standapp.engine.boundary

import dev.standapp.engine.control.OutputParser
import dev.standapp.engine.control.PromptBuilder
import dev.standapp.engine.control.QualityScorer
import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.GenerationConfig
import dev.standapp.engine.entity.PromptType
import dev.standapp.engine.entity.ScoredResult
import dev.standapp.engine.entity.StandupSummary
import dev.standapp.engine.entity.SummaryProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SummaryEngine(
    private val backend: LLMBackend,
    private val promptBuilder: PromptBuilder,
    private val config: GenerationConfig,
    private val scoringEnabled: Boolean,
) {
    suspend fun summarize(commits: List<CommitInfo>, promptType: PromptType): StandupSummary {
        val userPrompt = promptBuilder.buildUserPrompt(commits, promptType)
        val fullPrompt = "${promptBuilder.buildSystemPrompt()}\n\n$userPrompt"
        val raw = backend.generate(fullPrompt, config)
        return OutputParser.parse(raw, promptType)
    }

    suspend fun summarizeAndScore(commits: List<CommitInfo>, promptType: PromptType): ScoredResult {
        val summary = summarize(commits, promptType)
        val inputIds = commits.map { it.id }.toSet()
        val scores = QualityScorer.score(summary.raw, promptType, inputIds)
        return ScoredResult(summary, scores)
    }

    fun summarizeWithProgress(
        commits: List<CommitInfo>,
        promptType: PromptType,
    ): Flow<SummaryProgress> = flow {
        try {
            emit(SummaryProgress.BuildingPrompt)
            val userPrompt = promptBuilder.buildUserPrompt(commits, promptType)
            val fullPrompt = "${promptBuilder.buildSystemPrompt()}\n\n$userPrompt"

            emit(SummaryProgress.Generating)
            val accumulated = StringBuilder()
            backend.generateStream(fullPrompt, config).collect { token ->
                accumulated.append(token)
                emit(SummaryProgress.Streaming(token, accumulated.toString()))
            }
            val raw = accumulated.toString()

            emit(SummaryProgress.Parsing)
            val summary = OutputParser.parse(raw, promptType)

            if (scoringEnabled) {
                emit(SummaryProgress.Scoring)
                val inputIds = commits.map { it.id }.toSet()
                val scores = QualityScorer.score(raw, promptType, inputIds)
                emit(SummaryProgress.Complete(ScoredResult(summary, scores)))
            } else {
                emit(SummaryProgress.Complete(ScoredResult(summary)))
            }
        } catch (e: Throwable) {
            emit(SummaryProgress.Failed(e))
        }
    }
}
