package de.jug_da.standapp.benchmark

import java.io.File

/**
 * Report generators: Markdown comparison table, CSV export, pass/fail evaluation (PRD Section 7).
 */
object Reporting {

    data class CaseResult(
        val caseId: String,
        val backend: String,
        val promptType: PromptType,
        val run: Int,
        val latencyMs: Long,
        val charCount: Int,
        val autoScore: Scoring.AutoScoreResult,
        val humanScore: HumanScore? = null,
    )

    data class HumanScore(
        val faithfulness: Int = 0,
        val completeness: Int = 0,
        val structure: Int = 0,
        val actionability: Int = 0,
        val clarity: Int = 0,
    ) {
        val total: Int get() = faithfulness + completeness + structure + actionability + clarity
    }

    data class BackendSummary(
        val backend: String,
        val avgFaithfulness: Double,
        val avgCompleteness: Double,
        val avgStructure: Double,
        val autoPassRate: Double,
        val latencyP50: Long,
        val latencyP95: Long,
        val throughputMedian: Double,
        val determinism: Double,
    )

    // ── Pass/Fail Thresholds (PRD Section 7) ────────────────────────

    data class ThresholdResult(
        val criterion: String,
        val value: Double,
        val threshold: Double,
        val status: ThresholdStatus,
    )

    enum class ThresholdStatus { PASS, WARN, FAIL }

    fun evaluateThresholds(summary: BackendSummary): List<ThresholdResult> = listOf(
        ThresholdResult(
            criterion = "Faithfulness",
            value = summary.avgFaithfulness,
            threshold = 1.5,
            status = if (summary.avgFaithfulness >= 1.5) ThresholdStatus.PASS else ThresholdStatus.FAIL,
        ),
        ThresholdResult(
            criterion = "Structure (auto pass rate)",
            value = summary.autoPassRate,
            threshold = 0.9,
            status = if (summary.autoPassRate >= 0.9) ThresholdStatus.PASS else ThresholdStatus.FAIL,
        ),
        ThresholdResult(
            criterion = "Latency p50 (ms)",
            value = summary.latencyP50.toDouble(),
            threshold = 8000.0,
            status = when {
                summary.latencyP50 <= 8000 -> ThresholdStatus.PASS
                summary.latencyP50 <= 15000 -> ThresholdStatus.WARN
                else -> ThresholdStatus.FAIL
            },
        ),
    )

    // ── Markdown Table ──────────────────────────────────────────────

    fun markdownTable(summaries: List<BackendSummary>): String = buildString {
        appendLine("| Backend | Faithfulness (avg) | Completeness (avg) | Structure | Auto-checks pass% | Latency p50 | Latency p95 | Throughput | Determinism |")
        appendLine("|---------|--------------------|---------------------|-----------|--------------------|-------------|-------------|------------|-------------|")
        for (s in summaries) {
            appendLine(
                "| ${s.backend} | ${"%.2f".format(s.avgFaithfulness)} | ${"%.2f".format(s.avgCompleteness)} | ${"%.2f".format(s.avgStructure)} | ${"%.1f%%".format(s.autoPassRate * 100)} | ${s.latencyP50}ms | ${s.latencyP95}ms | ${"%.1f".format(s.throughputMedian)} c/s | ${"%.3f".format(s.determinism)} |"
            )
        }
    }

    // ── CSV Export ───────────────────────────────────────────────────

    private const val CSV_HEADER = "case_id,backend,prompt_type,run,latency_ms,char_count,json_parseable,json_schema_compliant,headings_present,all_ids_valid,no_hallucinated_ids,faithfulness,completeness,structure,actionability,clarity,total_human,total_auto_pass"

    fun writeCsv(results: List<CaseResult>, file: File) {
        file.printWriter().use { out ->
            out.println(CSV_HEADER)
            for (r in results) {
                val hs = r.humanScore
                out.println(
                    "${r.caseId},${r.backend},${r.promptType},${r.run},${r.latencyMs},${r.charCount}," +
                    "${r.autoScore.jsonParseable ?: ""},${r.autoScore.jsonSchemaCompliant ?: ""},${r.autoScore.headingsPresent ?: ""}," +
                    "${r.autoScore.allIdsValid},${r.autoScore.noHallucinatedIds}," +
                    "${hs?.faithfulness ?: ""},${hs?.completeness ?: ""},${hs?.structure ?: ""},${hs?.actionability ?: ""},${hs?.clarity ?: ""}," +
                    "${hs?.total ?: ""},${r.autoScore.passCount}"
                )
            }
        }
    }

    // ── Cloud vs Local Delta ────────────────────────────────────────

    data class DeltaRow(
        val metric: String,
        val localValue: Double,
        val cloudValue: Double,
        val delta: Double,
        val deltaPct: Double,
    )

    fun computeDeltas(local: BackendSummary, cloud: BackendSummary): List<DeltaRow> {
        fun row(name: String, l: Double, c: Double) = DeltaRow(
            metric = name,
            localValue = l,
            cloudValue = c,
            delta = l - c,
            deltaPct = if (c != 0.0) ((l - c) / c) * 100 else 0.0,
        )
        return listOf(
            row("Faithfulness", local.avgFaithfulness, cloud.avgFaithfulness),
            row("Completeness", local.avgCompleteness, cloud.avgCompleteness),
            row("Structure", local.avgStructure, cloud.avgStructure),
            row("Auto pass rate", local.autoPassRate, cloud.autoPassRate),
            row("Latency p50 (ms)", local.latencyP50.toDouble(), cloud.latencyP50.toDouble()),
            row("Throughput (c/s)", local.throughputMedian, cloud.throughputMedian),
            row("Determinism", local.determinism, cloud.determinism),
        )
    }

    fun deltaMarkdown(deltas: List<DeltaRow>, localName: String): String = buildString {
        appendLine("### $localName vs Cloud")
        appendLine()
        appendLine("| Metric | Local | Cloud | Delta | Delta % |")
        appendLine("|--------|-------|-------|-------|---------|")
        for (d in deltas) {
            appendLine("| ${d.metric} | ${"%.2f".format(d.localValue)} | ${"%.2f".format(d.cloudValue)} | ${"%.2f".format(d.delta)} | ${"%.1f%%".format(d.deltaPct)} |")
        }
    }
}
