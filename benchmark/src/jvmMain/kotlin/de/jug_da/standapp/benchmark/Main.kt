package de.jug_da.standapp.benchmark

import de.jug_da.standapp.llm.LLMBackendType
import de.jug_da.standapp.llm.LLMConfig
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * CLI entry point: `./gradlew :benchmark:run`
 *
 * Environment variables:
 * - BENCH_DIR           — path to bench/ directory (default: ./bench)
 * - BENCH_BACKENDS      — comma-separated backend names to test (default: all)
 * - BENCH_RUNS          — number of runs per case (default: 5)
 * - BENCH_CASES         — comma-separated case ids to run (default: all)
 * - BENCH_PROMPTS       — comma-separated prompt types: SUMMARY,JSON (default: both)
 * - BENCH_LOCAL_URL     — local REST endpoint URL (default: http://localhost:1234)
 * - BENCH_LOCAL_MODEL   — local REST model name (default: tinyllama-1.1b-chat-v1.0)
 * - BENCH_LOCAL_API_KEY — optional Bearer token for local REST endpoint
 * - BENCH_CLOUD_URL     — cloud REST endpoint URL (required for cloud baseline)
 * - BENCH_CLOUD_MODEL   — cloud model name (default: gpt-4o-mini)
 * - BENCH_CLOUD_API_KEY — optional cloud Bearer token (falls back to OPENAI_API_KEY)
 * - MCP_LLM_MODEL_PATH  — GGUF model path for SKAINET backend
 */
fun main() {
    val benchDir = File(System.getenv("BENCH_DIR") ?: "bench")
    val runsPerCase = System.getenv("BENCH_RUNS")?.toIntOrNull() ?: 5
    val caseFilter = System.getenv("BENCH_CASES")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?.takeIf { it.isNotEmpty() }

    val promptFilter = System.getenv("BENCH_PROMPTS")
        ?.split(",")
        ?.mapNotNull { raw ->
            val normalized = raw.trim().uppercase()
            PromptType.entries.find { it.name == normalized }
        }
        ?.distinct()
        ?.takeIf { it.isNotEmpty() }
        ?: PromptType.entries

    val localUrl = System.getenv("BENCH_LOCAL_URL") ?: "http://localhost:1234"
    val localModel = System.getenv("BENCH_LOCAL_MODEL") ?: "tinyllama-1.1b-chat-v1.0"
    val localApiKey = System.getenv("BENCH_LOCAL_API_KEY")
    val cloudUrl = System.getenv("BENCH_CLOUD_URL")
    val cloudModel = System.getenv("BENCH_CLOUD_MODEL") ?: "gpt-4o-mini"
    val cloudApiKey = System.getenv("BENCH_CLOUD_API_KEY") ?: System.getenv("OPENAI_API_KEY")
    val modelPath = System.getenv("MCP_LLM_MODEL_PATH") ?: ""
    val outputDir = File(System.getenv("BENCH_OUTPUT_DIR") ?: "benchmark-results")

    val requestedBackends = System.getenv("BENCH_BACKENDS")
        ?.split(",")
        ?.map { it.trim().uppercase() }

    // Build backend configurations
    val backends = mutableMapOf<String, Pair<LLMBackendType, LLMConfig>>()

    if (requestedBackends == null || "SKAINET" in requestedBackends) {
        if (modelPath.isNotBlank()) {
            backends["SKAINET"] = LLMBackendType.SKAINET to LLMConfig(modelPath = modelPath)
        } else {
            println("WARN: Skipping SKAINET — MCP_LLM_MODEL_PATH not set")
        }
    }

    if (requestedBackends == null || "JLAMA" in requestedBackends) {
        backends["JLAMA"] = LLMBackendType.JLAMA to LLMConfig()
    }

    if (requestedBackends == null || "REST_API" in requestedBackends) {
        backends["REST_API (local)"] = LLMBackendType.REST_API to LLMConfig(
            baseUrl = localUrl,
            modelName = localModel,
            apiKey = localApiKey,
        )
    }

    // Mandatory cloud baseline
    if (cloudUrl != null) {
        backends["REST_API (cloud)"] = LLMBackendType.REST_API to LLMConfig(
            baseUrl = cloudUrl,
            modelName = cloudModel,
            apiKey = cloudApiKey,
        )
    } else {
        println("WARN: BENCH_CLOUD_URL not set — cloud baseline will be skipped")
        println("      Set BENCH_CLOUD_URL to an OpenAI-compatible endpoint for mandatory cloud comparison")
    }

    if (backends.isEmpty()) {
        println("ERROR: No backends configured. Set environment variables and retry.")
        println()
        println("Usage examples:")
        println("  BENCH_BACKENDS=REST_API BENCH_LOCAL_URL=http://192.168.1.100:1234 BENCH_LOCAL_MODEL=tinyllama-1.1b-chat-v1.0 java -jar benchmark-jvm.jar")
        println("  BENCH_BACKENDS=REST_API BENCH_LOCAL_URL=http://localhost:11434 BENCH_LOCAL_MODEL=llama3.2:3b java -jar benchmark-jvm.jar")
        return
    }

    val runner = BenchmarkRunner(
        benchDir = benchDir,
        backends = backends,
        runsPerCase = runsPerCase,
        caseFilter = caseFilter,
        promptTypes = promptFilter,
    )

    runBlocking {
        runner.run()
    }

    // Generate reports
    outputDir.mkdirs()
    val results = runner.getResults()
    val summaries = runner.buildSummaries()

    // Markdown comparison table
    val mdReport = buildString {
        appendLine("# Benchmark Results")
        appendLine()
        appendLine("Cases: ${benchDir.listFiles { f -> f.name.startsWith("case-") }?.size ?: 0}")
        appendLine("Runs per case: $runsPerCase")
        appendLine()
        appendLine("## Comparison Table")
        appendLine()
        append(Reporting.markdownTable(summaries))
        appendLine()

        // Thresholds
        appendLine("## Pass/Fail Thresholds")
        appendLine()
        for (summary in summaries) {
            appendLine("### ${summary.backend}")
            val thresholds = Reporting.evaluateThresholds(summary)
            for (t in thresholds) {
                val icon = when (t.status) {
                    Reporting.ThresholdStatus.PASS -> "PASS"
                    Reporting.ThresholdStatus.WARN -> "WARN"
                    Reporting.ThresholdStatus.FAIL -> "FAIL"
                }
                appendLine("- [$icon] ${t.criterion}: ${"%.2f".format(t.value)} (threshold: ${"%.2f".format(t.threshold)})")
            }
            appendLine()
        }

        // Cloud vs local deltas
        val cloudSummary = summaries.find { it.backend.contains("cloud", ignoreCase = true) }
        if (cloudSummary != null) {
            appendLine("## Cloud vs Local Delta Analysis")
            appendLine()
            for (summary in summaries.filter { it != cloudSummary }) {
                val deltas = Reporting.computeDeltas(summary, cloudSummary)
                append(Reporting.deltaMarkdown(deltas, summary.backend))
                appendLine()
            }
        }
    }

    File(outputDir, "benchmark-report.md").writeText(mdReport)
    println("Markdown report: ${File(outputDir, "benchmark-report.md").absolutePath}")

    // CSV
    Reporting.writeCsv(results, File(outputDir, "benchmark-results.csv"))
    println("CSV results: ${File(outputDir, "benchmark-results.csv").absolutePath}")

    println("\nDone.")
}
