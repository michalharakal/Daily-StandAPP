package de.jug_da.standapp.benchmark

import de.jug_da.standapp.llm.LLMService
import dev.standapp.engine.entity.PromptType
import dev.standapp.engine.pipeline.standupPipeline
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Benchmark-local marker so timeouts stay distinguishable from engine errors. */
private class InferenceTimeoutException : RuntimeException("inference timed out")

/**
 * Orchestrates benchmark runs: loads cases, calls backends, collects scores and metrics.
 *
 * Each (backend, prompt type) combination runs through the same
 * [standupPipeline] the CLI uses — single code path, identical prompt bytes
 * to the pre-pipeline runner, so results stay comparable across runs.
 *
 * `backends` maps a human-readable name (e.g. "SKAINET", "REST_API (local)",
 * "DELIVERANCE") to a *factory* that produces an [LLMService]. Using factories
 * (rather than `(LLMBackendType, LLMConfig)` pairs) lets benchmark-only engines
 * — Deliverance, qxotic — plug in via reflection without their classes needing
 * to live in the production `LLMBackendType` enum.
 */
class BenchmarkRunner(
    private val benchDir: File,
    private val backends: Map<String, () -> LLMService>,
    private val runsPerCase: Int = 5,
    private val warmupRuns: Int = 0,
    private val caseFilter: Set<String>? = null,
    private val promptTypes: List<PromptType> = PromptType.entries,
    private val timeoutMs: Long = 30_000,
) {
    private val cases: List<BenchmarkCase> = BenchmarkCaseLoader
        .loadAll(benchDir)
        .filter { caseFilter == null || it.id in caseFilter }
    private val results = mutableListOf<Reporting.CaseResult>()
    private val allOutputs = mutableMapOf<String, MutableList<String>>() // backend -> outputs for determinism

    suspend fun run() {
        println("Loaded ${cases.size} benchmark cases from ${benchDir.absolutePath}")
        if (caseFilter != null) {
            println("Case filter: ${caseFilter.joinToString()}")
        }
        println("Backends: ${backends.keys.joinToString()}")
        println("Prompt types: ${promptTypes.joinToString()}")
        println("Runs per case: $runsPerCase (+$warmupRuns warm-up, discarded)")
        println()

        for ((backendName, factory) in backends) {
            println("═══ Backend: $backendName ═══")

            val service: LLMService = try {
                factory()
            } catch (e: Exception) {
                println("  SKIP — failed to create backend: ${e.message}")
                continue
            }

            val backendOutputs = allOutputs.getOrPut(backendName) { mutableListOf() }
            var timeoutCount = 0
            var errorCount = 0

            for (case in cases) {
                val commitInfos = case.commits.map { it.toCommitInfo() }

                for (promptType in promptTypes) {
                    // Same pipeline the CLI declares — no preprocess step, so
                    // the prompt bytes match the pre-pipeline runner exactly.
                    val pipeline = standupPipeline {
                        prompt { type = promptType }
                        infer { _, user ->
                            withTimeoutOrNull(timeoutMs) {
                                service.generate(
                                    prompt = user,
                                    maxTokens = LLMService.DEFAULT_MAX_TOKENS,
                                    temperature = LLMService.DEFAULT_TEMPERATURE,
                                    topP = LLMService.DEFAULT_TOP_P,
                                )
                            } ?: throw InferenceTimeoutException()
                        }
                        postprocess {
                            parseSummary() // lenient — a malformed output is scored, never fatal
                            score()
                        }
                    }

                    // Warm-up runs neutralise JIT compilation, class loading, and
                    // KV-cache allocation costs that bias the first measured runs.
                    // Results are discarded; they do not feed determinism either.
                    for (warmupIdx in 1..warmupRuns) {
                        val warmStart = System.currentTimeMillis()
                        try {
                            pipeline.run(commitInfos)
                        } catch (e: Exception) {
                            println("  WARMUP-ERROR ${case.id}/$promptType warmup $warmupIdx: ${e.message}")
                        }
                        val warmMs = System.currentTimeMillis() - warmStart
                        println("  ~ ${case.id}/$promptType warmup $warmupIdx — ${warmMs}ms (discarded)")
                    }

                    for (runIdx in 1..runsPerCase) {
                        val heapBefore = Metrics.heapUsageMb()
                        val startTime = System.currentTimeMillis()

                        val outcome = try {
                            pipeline.run(commitInfos)
                        } catch (e: InferenceTimeoutException) {
                            timeoutCount++
                            println("  TIMEOUT ${case.id}/$promptType run $runIdx (${System.currentTimeMillis() - startTime}ms)")
                            null
                        } catch (e: Exception) {
                            errorCount++
                            println("  ERROR ${case.id}/$promptType run $runIdx: ${e.message}")
                            null
                        }

                        val latencyMs = System.currentTimeMillis() - startTime
                        val heapAfter = Metrics.heapUsageMb()
                        if (outcome == null) continue

                        backendOutputs.add(outcome.raw)
                        val autoScore = outcome.result.scores
                            ?: error("pipeline was built with score() — scores must be present")

                        results.add(
                            Reporting.CaseResult(
                                caseId = case.id,
                                backend = backendName,
                                promptType = promptType,
                                run = runIdx,
                                latencyMs = latencyMs,
                                charCount = outcome.raw.length,
                                autoScore = autoScore,
                            )
                        )

                        val passSymbol = if (autoScore.allPassed) "✓" else "✗"
                        println("  $passSymbol ${case.id}/$promptType #$runIdx — ${latencyMs}ms, ${outcome.raw.length} chars, heap ${heapBefore}->${heapAfter}MB")
                    }
                }
            }

            println("  Timeouts: $timeoutCount, Errors: $errorCount")
            println()
        }
    }

    fun getResults(): List<Reporting.CaseResult> = results.toList()

    fun buildSummaries(): List<Reporting.BackendSummary> {
        return results.groupBy { it.backend }.map { (backend, backendResults) ->
            val latencies = backendResults.map { it.latencyMs }.sorted()
            val throughputs = backendResults.map {
                if (it.latencyMs > 0) it.charCount.toDouble() / (it.latencyMs / 1000.0) else 0.0
            }.sorted()

            Reporting.BackendSummary(
                backend = backend,
                avgFaithfulness = backendResults.mapNotNull { it.humanScore?.faithfulness?.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0,
                avgCompleteness = backendResults.mapNotNull { it.humanScore?.completeness?.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0,
                avgStructure = backendResults.mapNotNull { it.humanScore?.structure?.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0,
                autoPassRate = if (backendResults.isEmpty()) 0.0 else backendResults.count { it.autoScore.allPassed }.toDouble() / backendResults.size,
                latencyP50 = Metrics.percentile(latencies, 50.0),
                latencyP95 = Metrics.percentile(latencies, 95.0),
                throughputMedian = if (throughputs.isEmpty()) 0.0 else throughputs[throughputs.size / 2],
                determinism = Metrics.computeDeterminism(allOutputs[backend] ?: emptyList()),
            )
        }
    }
}
