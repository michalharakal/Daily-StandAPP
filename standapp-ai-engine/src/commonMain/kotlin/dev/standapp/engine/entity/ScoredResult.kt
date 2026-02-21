package dev.standapp.engine.entity

import kotlinx.serialization.Serializable

@Serializable
data class ScoredResult(
    val summary: StandupSummary,
    val scores: QualityScores,
)

@Serializable
data class QualityScores(
    val jsonParseable: Boolean? = null,
    val jsonSchemaCompliant: Boolean? = null,
    val headingsPresent: Boolean? = null,
    val allIdsValid: Boolean,
    val noHallucinatedIds: Boolean,
    val passCount: Int,
    val totalChecks: Int,
)
