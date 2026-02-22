package dev.standapp.engine.entity

sealed interface SummaryProgress {
    data object BuildingPrompt : SummaryProgress
    data object Generating : SummaryProgress
    data class Streaming(val tokenDelta: String, val accumulated: String) : SummaryProgress
    data object Parsing : SummaryProgress
    data object Scoring : SummaryProgress
    data class Complete(val result: ScoredResult) : SummaryProgress
    data class Failed(val error: Throwable) : SummaryProgress
}
