package de.jug_da.app.cli

import dev.standapp.engine.entity.QualityScores
import dev.standapp.engine.entity.ScoredResult
import dev.standapp.engine.entity.Status
import dev.standapp.engine.pipeline.PipelineResult
import kotlinx.serialization.json.Json

object OutputRenderer {

    private val prettyJson = Json { prettyPrint = true }

    fun render(result: PipelineResult, format: OutputFormat): String = when (format) {
        OutputFormat.MD -> renderMarkdown(result)
        OutputFormat.JSON -> prettyJson.encodeToString(ScoredResult.serializer(), result.result)
        OutputFormat.TEXT -> renderText(result)
    }

    /**
     * SUMMARY runs already produce markdown — pass the raw output through.
     * For structured (JSON-prompt) runs, reconstruct markdown sections.
     */
    private fun renderMarkdown(result: PipelineResult): String {
        val summary = result.result.summary
        if (summary.sections.isEmpty() || summary.raw.contains("## ")) return summary.raw.trim()
        return buildString {
            summary.sections.forEach { section ->
                appendLine("## ${section.name}")
                section.items.forEach { item ->
                    val id = item.commitId?.let { " ($it)" } ?: ""
                    appendLine("- ${item.text}$id")
                }
                appendLine()
            }
        }.trim()
    }

    private fun renderText(result: PipelineResult): String {
        val summary = result.result.summary
        if (summary.sections.isEmpty()) return summary.raw.trim()
        return buildString {
            summary.sections.forEach { section ->
                appendLine("${section.name}:")
                section.items.forEach { item ->
                    val marker = when (item.status) {
                        Status.DONE -> "[x]"
                        Status.IN_PROGRESS -> "[~]"
                        Status.UNKNOWN -> "[ ]"
                    }
                    appendLine("  $marker ${item.text}")
                }
                appendLine()
            }
        }.trim()
    }

    /** Check-by-check quality report, written to stderr by the caller. */
    fun renderScoreReport(scores: QualityScores): String = buildString {
        appendLine("Quality report:")
        fun line(name: String, value: Boolean?) {
            if (value != null) appendLine("  ${if (value) "PASS" else "FAIL"}  $name")
        }
        line("JSON parseable", scores.jsonParseable)
        line("JSON schema compliant", scores.jsonSchemaCompliant)
        line("required headings present", scores.headingsPresent)
        line("referenced commit ids well-formed", scores.allIdsValid)
        line("no hallucinated commit ids", scores.noHallucinatedIds)
        append("  ${scores.passCount}/${scores.totalChecks} checks passed")
    }
}
