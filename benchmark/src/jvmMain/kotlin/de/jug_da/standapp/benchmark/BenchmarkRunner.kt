package de.jug_da.standapp.benchmark

import de.jug_da.standapp.llm.LLMBackendType
import de.jug_da.standapp.llm.LLMConfig
import de.jug_da.standapp.llm.LLMService
import de.jug_da.standapp.llm.LLMServiceFactory
import dev.standapp.engine.control.PromptBuilder
import dev.standapp.engine.control.QualityScorer
import dev.standapp.engine.entity.PromptType
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Orchestrates benchmark runs: loads cases, calls backends, collects scores and metrics.
 */
class BenchmarkRunner(
    private val benchDir: File,
    private val backends: Map<String, Pair<LLMBackendType, LLMConfig>>,
    private val runsPerCase: Int = 5,
    private val caseFilter: Set<String>? = null,
    private val promptTypes: List<PromptType> = PromptType.entries,
    private val timeoutMs: Long = 30_000,
) {
    private val cases: List<BenchmarkCase> = BenchmarkCaseLoader
        .loadAll(benchDir)
        .filter { caseFilter == null || it.id in caseFilter }
    private val results = mutableListOf<Reporting.CaseResult>()
    private val allOutputs = mutableMapOf<String, MutableList<String>>() // backend -> outputs for determinism
    private val promptBuilder = PromptBuilder()

    suspend fun run() {
        println("Loaded ${cases.size} benchmark cases from ${benchDir.absolutePath}")
        if (caseFilter != null) {
            println("Case filter: ${caseFilter.joinToString()}")
        }
        println("Backends: ${backends.keys.joinToString()}")
        println("Prompt types: ${promptTypes.joinToString()}")
        println("Runs per case: $runsPerCase")
        println()

        for ((backendName, backendSpec) in backends) {
            val (backendType, config) = backendSpec
            println("═══ Backend: $backendName ═══")

            val service: LLMService = try {
                LLMServiceFactory.create(backendType, config)
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
                    val prompt = promptBuilder.buildUserPrompt(commitInfos, promptType)

                    for (runIdx in 1..runsPerCase) {
                        val heapBefore = Metrics.heapUsageMb()
                        val startTime = System.currentTimeMillis()
                        var failedWithError = false

                        val output: String? = try {
                            withTimeoutOrNull(timeoutMs) {
                                service.generate(
                                    prompt = prompt,
                                    maxTokens = LLMService.DEFAULT_MAX_TOKENS,
                                    temperature = LLMService.DEFAULT_TEMPERATURE,
                                    topP = LLMService.DEFAULT_TOP_P,
                                )
                            }
                        } catch (e: Exception) {
                            failedWithError = true
                            errorCount++
                            println("  ERROR ${case.id}/$promptType run $runIdx: ${e.message}")
                            null
                        }

                        val latencyMs = System.currentTimeMillis() - startTime
                        val heapAfter = Metrics.heapUsageMb()

                        if (output == null) {
                            if (!failedWithError) {
                                timeoutCount++
                                println("  TIMEOUT ${case.id}/$promptType run $runIdx (${latencyMs}ms)")
                            }
                            continue
                        }

                        backendOutputs.add(output)

                        val inputIds = case.commits.map { it.id }.toSet()
                        val autoScore = QualityScorer.score(output, promptType, inputIds)

                        results.add(
                            Reporting.CaseResult(
                                caseId = case.id,
                                backend = backendName,
                                promptType = promptType,
                                run = runIdx,
                                latencyMs = latencyMs,
                                charCount = output.length,
                                autoScore = autoScore,
                            )
                        )

                        val passSymbol = if (autoScore.allPassed) "✓" else "✗"
                        println("  $passSymbol ${case.id}/$promptType #$runIdx — ${latencyMs}ms, ${output.length} chars, heap ${heapBefore}->${heapAfter}MB")
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
