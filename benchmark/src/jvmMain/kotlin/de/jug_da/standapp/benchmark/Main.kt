package de.jug_da.standapp.benchmark

import de.jug_da.standapp.benchmark.engines.BenchmarkEngineRegistry
import de.jug_da.standapp.llm.LLMBackendType
import de.jug_da.standapp.llm.LLMConfig
import de.jug_da.standapp.llm.LLMService
import de.jug_da.standapp.llm.LLMServiceFactory
import dev.standapp.engine.entity.PromptType
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * CLI entry point: `./gradlew :benchmark:jvmRun`
 *
 * Environment variables:
 * - BENCH_DIR               — path to bench/ directory (default: ./bench)
 * - BENCH_BACKENDS          — comma-separated backend names to test (default: all)
 * - BENCH_RUNS              — number of measured runs per case (default: 5)
 * - BENCH_WARMUP             — discarded warm-up runs before measurement (default: 0).
 *                              Use to neutralise JIT/class-loading bias on cold starts.
 * - BENCH_CASES             — comma-separated case ids to run (default: all)
 * - BENCH_PROMPTS           — comma-separated prompt types: SUMMARY,JSON (default: both)
 * - BENCH_LOCAL_URL         — local REST endpoint URL (default: http://localhost:1234)
 * - BENCH_LOCAL_MODEL       — local REST model name (default: tinyllama-1.1b-chat-v1.0)
 * - BENCH_LOCAL_API_KEY     — optional Bearer token for local REST endpoint
 * - BENCH_CLOUD_URL         — cloud REST endpoint URL (required for cloud baseline)
 * - BENCH_CLOUD_MODEL       — cloud model name (default: gpt-4o-mini)
 * - BENCH_CLOUD_API_KEY     — optional cloud Bearer token (falls back to OPENAI_API_KEY)
 * - MCP_LLM_MODEL_PATH      — GGUF model path for SKAINET backend
 * - BENCH_DELIVERANCE_MODEL — HuggingFace owner/name for the DELIVERANCE engine,
 *                              e.g. "TinyLlama/TinyLlama-1.1B-Chat-v1.0".
 *                              Requires building with `-Pdeliverance.enabled=true`
 *                              and `scripts/setup-bench-engines.sh` having installed
 *                              the deliverance jars to ~/.m2 first.
 * - BENCH_QXOTIC_MODEL      — Absolute path to a Llama 3.2 Q8_0 GGUF for the QXOTIC
 *                              engine, or the literal string "embedded" to reuse the
 *                              GGUF that the SKAINET path extracts to ~/.cache/standapp.
 *                              Requires building with `-Pqxotic.enabled=true` and
 *                              `scripts/setup-bench-engines.sh` having cloned + built
 *                              qxotic, with external/qxotic-classpath.txt produced.
 * - BENCH_QXOTIC_TIMEOUT_MS  — Override the qxotic subprocess timeout (default 600000).
 * - BENCH_TIMEOUT_MS        — Per-run generation timeout in ms (default 30000).
 *                              Local CPU engines routinely exceed 30 s on the
 *                              larger cases — raise this for full runs.
 */
fun main() {
    val benchDir = File(System.getenv("BENCH_DIR") ?: "bench")
    val runsPerCase = System.getenv("BENCH_RUNS")?.toIntOrNull() ?: 5
    val warmupRuns = (System.getenv("BENCH_WARMUP")?.toIntOrNull() ?: 0).coerceAtLeast(0)
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

    // Build backend factories. Each entry produces an LLMService on demand.
    val backends = mutableMapOf<String, () -> LLMService>()

    if (requestedBackends == null || "SKAINET" in requestedBackends) {
        // SKAINET: empty modelPath now means "use the embedded Llama 3.2 1B GGUF resource".
        backends["SKAINET"] = {
            LLMServiceFactory.create(LLMBackendType.SKAINET, LLMConfig(modelPath = modelPath))
        }
    }

    if (requestedBackends == null || "REST_API" in requestedBackends) {
        backends["REST_API (local)"] = {
            LLMServiceFactory.create(
                LLMBackendType.REST_API,
                LLMConfig(baseUrl = localUrl, modelName = localModel, apiKey = localApiKey),
            )
        }
    }

    // Mandatory cloud baseline
    if (cloudUrl != null) {
        backends["REST_API (cloud)"] = {
            LLMServiceFactory.create(
                LLMBackendType.REST_API,
                LLMConfig(baseUrl = cloudUrl, modelName = cloudModel, apiKey = cloudApiKey),
            )
        }
    } else {
        println("WARN: BENCH_CLOUD_URL not set — cloud baseline will be skipped")
        println("      Set BENCH_CLOUD_URL to an OpenAI-compatible endpoint for mandatory cloud comparison")
    }

    // Benchmark-only alternative engines (Deliverance, qxotic) plug in via
    // reflection. If their conditional source set wasn't included at build
    // time (i.e. -Pdeliverance.enabled=true), the registry simply returns
    // null and we print a hint.
    val deliveranceModel = System.getenv("BENCH_DELIVERANCE_MODEL")
    if (deliveranceModel != null && (requestedBackends == null || "DELIVERANCE" in requestedBackends)) {
        val factory = BenchmarkEngineRegistry.deliveranceFactory(deliveranceModel)
        if (factory != null) {
            backends["DELIVERANCE"] = factory
        } else {
            println("WARN: BENCH_DELIVERANCE_MODEL=$deliveranceModel set but DeliveranceLLMService is not on the classpath.")
            println("      Re-run with: ./gradlew :benchmark:jvmRun -Pdeliverance.enabled=true")
            println("      And first install the deliverance jars: ./scripts/setup-bench-engines.sh")
        }
    }

    val qxoticModel = System.getenv("BENCH_QXOTIC_MODEL")
    if (qxoticModel != null && (requestedBackends == null || "QXOTIC" in requestedBackends)) {
        val factory = BenchmarkEngineRegistry.qxoticFactory(qxoticModel)
        if (factory != null) {
            backends["QXOTIC"] = factory
        } else {
            println("WARN: BENCH_QXOTIC_MODEL=$qxoticModel set but QxoticLLMService is not on the classpath.")
            println("      Re-run with: ./gradlew :benchmark:jvmRun -Pqxotic.enabled=true")
            println("      And first install qxotic + capture classpath: ./scripts/setup-bench-engines.sh")
        }
    }

    if (backends.isEmpty()) {
        println("ERROR: No backends configured. Set environment variables and retry.")
        println()
        println("Usage examples:")
        println("  BENCH_BACKENDS=REST_API BENCH_LOCAL_URL=http://192.168.1.100:1234 BENCH_LOCAL_MODEL=tinyllama-1.1b-chat-v1.0 ./gradlew :benchmark:jvmRun")
        return
    }

    val timeoutMs = System.getenv("BENCH_TIMEOUT_MS")?.toLongOrNull()?.takeIf { it > 0 } ?: 30_000L

    val runner = BenchmarkRunner(
        benchDir = benchDir,
        backends = backends,
        runsPerCase = runsPerCase,
        warmupRuns = warmupRuns,
        caseFilter = caseFilter,
        promptTypes = promptFilter,
        timeoutMs = timeoutMs,
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
