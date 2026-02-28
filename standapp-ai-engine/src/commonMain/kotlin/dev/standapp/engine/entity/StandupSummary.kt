package dev.standapp.engine.entity

import kotlinx.serialization.Serializable

@Serializable
data class StandupSummary(
    val raw: String,
    val date: String,
    val author: String,
    val sections: List<SummarySection>,
    val promptType: PromptType,
)

@Serializable
data class SummarySection(
    val name: String,
    val items: List<SummaryItem>,
)

@Serializable
data class SummaryItem(
    val commitId: String? = null,
    val text: String,
    val status: Status = Status.UNKNOWN,
)

@Serializable
enum class Status { DONE, IN_PROGRESS, UNKNOWN }

@Serializable
enum class PromptType { SUMMARY, JSON }
