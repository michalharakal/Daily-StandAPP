package de.jug_da.app.cli

import de.jug_da.standapp.llm.LLMBackendType
import de.jug_da.standapp.llm.LLMService
import de.jug_da.standapp.llm.LlamaChatLLMService
import de.jug_da.standapp.llm.RestApiLLMService
import de.jug_da.standapp.llm.model.ModelCatalog
import de.jug_da.standapp.llm.model.ModelProvider
import de.jug_da.standapp.llm.model.ModelResolver
import de.jug_da.standapp.llm.model.ModelSpec
import de.jug_da.standapp.llm.pipeline.GitAccessException
import de.jug_da.standapp.llm.pipeline.NoCommitsException
import de.jug_da.standapp.llm.pipeline.StandupRequest
import de.jug_da.standapp.llm.pipeline.standupFlow
import dev.standapp.engine.entity.PromptType
import dev.standapp.engine.pipeline.PipelineResult
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import sk.ainet.data.source.DataSourceException
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.time.Clock

const val EXIT_OK = 0
const val EXIT_USAGE = 2
const val EXIT_GIT = 3
const val EXIT_LLM = 4
const val EXIT_VALIDATION = 5

fun main(args: Array<String>) {
    exitProcess(run(args))
}

internal fun run(args: Array<String>): Int {
    val cli = try {
        CliArgs.parse(args)
    } catch (e: CliUsageException) {
        System.err.println("Error: ${e.message}")
        System.err.println()
        printUsage(System.err)
        return EXIT_USAGE
    }
    if (cli.help) {
        printUsage(System.out)
        return EXIT_OK
    }

    val backend = cli.backend ?: LLMBackendType.fromEnv()
    // The Qwen tool understands day windows only; explicit --since and the
    // REST backend (no local models) take the direct git path.
    val commitsMode = when {
        backend == LLMBackendType.REST_API || cli.since != null -> CommitsMode.GIT
        else -> cli.commits
    }
    System.err.println("Daily StandAPP — repo=${cli.repoDir} backend=$backend commits=$commitsMode format=${cli.format}")

    if (!File(cli.repoDir, ".git").exists()) {
        System.err.println("Error: '${cli.repoDir}' is not a git repository (no .git directory).")
        return EXIT_GIT
    }

    // stdout carries only the result. Library banners, model-load chatter and
    // [STAGE/...] lines go to stderr for the whole run: System.out is diverted
    // and the real stream is kept for streamed tokens and the final emit.
    val streamTokens = cli.format == OutputFormat.MD && cli.output == null
    val realOut = System.out
    System.setOut(PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.err), true))
    val tokenSink: (String) -> Unit = if (streamTokens) { token -> realOut.print(token); realOut.flush() } else { _ -> }

    val overrides = buildMap<ModelSpec, Path> {
        cli.qwenModelPath?.let { put(ModelCatalog.QWEN3_0_6B, Path.of(it)) }
        cli.llamaModelPath?.let { put(ModelCatalog.LLAMA_3_2_3B, Path.of(it)) }
    }
    val models: ModelProvider = if (cli.keepModels) {
        ModelProvider.caching(ModelResolver(), overrides)
    } else {
        ModelProvider.releasing(ModelResolver(), overrides)
    }

    try {
        // STANDAPP_TINY_PROMPT=1 → bypass the pipeline and send a one-line
        // prompt to the summariser. Verifies download + inference without a repo.
        // The reply is printed once at the end (no token streaming).
        if (System.getenv("STANDAPP_TINY_PROMPT") == "1") {
            val service: LLMService = when (backend) {
                LLMBackendType.SKAINET -> LlamaChatLLMService(models)
                LLMBackendType.REST_API -> restService()
            }
            val reply = try {
                runBlocking { service.generate("Say hello in three words.", cli.maxTokens, cli.temperature, LLMService.DEFAULT_TOP_P) }
            } catch (e: Exception) {
                return reportLlmError(e)
            }
            realOut.println(reply.trim())
            return EXIT_OK
        }

        // The pipeline as code — every stage is a SKaiNET PipelineStage.
        val flow = standupFlow(models) {
            commits = if (commitsMode == CommitsMode.QWEN) qwenToolCall() else gitCommits()
            // firstMessageLine keeps prefill sane on real repos: multi-paragraph
            // commit bodies multiply prompt tokens without adding signal.
            preprocess { shortIds(7); firstMessageLine(120); limit(MAX_COMMITS) }
            prompt { type = if (cli.format == OutputFormat.JSON) PromptType.JSON else PromptType.SUMMARY }
            summarize(
                when (backend) {
                    LLMBackendType.SKAINET -> llama()
                    LLMBackendType.REST_API -> llmService(restService(), name = "rest-api")
                }
            )
            postprocess {
                parseSummary()
                if (cli.score) score()
                if (cli.format == OutputFormat.JSON) retryOnInvalid(maxAttempts = 2)
            }
            onToken = tokenSink
        }
        System.err.println("[PIPELINE] ${flow.describe()}")

        val request = buildRequest(cli)
        val result: PipelineResult = try {
            runBlocking { flow.execute(request) }
        } catch (e: NoCommitsException) {
            System.err.println(e.message)
            return EXIT_GIT
        } catch (e: GitAccessException) {
            System.err.println("Error: ${e.message}")
            return EXIT_GIT
        } catch (e: DataSourceException) {
            System.err.println("Error resolving a model: ${e.message}")
            System.err.println("Set STANDAPP_QWEN_MODEL_PATH / STANDAPP_LLAMA_MODEL_PATH to local GGUF files, or check network access.")
            return EXIT_LLM
        } catch (e: Exception) {
            return reportLlmError(e)
        }
        if (result.attempts > 1) {
            System.err.println("(model output needed ${result.attempts} attempts)")
        }

        val rendered = OutputRenderer.render(result, cli.format)
        if (streamTokens) {
            // The summary was already streamed token by token; close the line and
            // only print the rendered form when it differs (e.g. reconstructed sections).
            realOut.println()
            if (rendered != result.raw.trim()) realOut.println(rendered)
        } else {
            emit(rendered, cli.output, realOut)
        }

        var exit = EXIT_OK
        result.result.scores?.let { scores ->
            System.err.println()
            System.err.println(OutputRenderer.renderScoreReport(scores))
            if (scores.passCount < scores.totalChecks) exit = EXIT_VALIDATION
        }
        if (cli.format == OutputFormat.JSON && result.result.summary.sections.isEmpty()) {
            System.err.println("Warning: model output could not be parsed as JSON after ${result.attempts} attempt(s); raw text emitted.")
            exit = EXIT_VALIDATION
        }
        return exit
    } finally {
        models.close()
        System.setOut(realOut)
    }
}

private const val MAX_COMMITS = 60

private fun reportLlmError(e: Exception): Int {
    System.err.println("Error during generation: ${e.message}")
    System.err.println("Set MCP_LLM_BACKEND=REST_API (or --backend rest_api) to use a remote endpoint.")
    return EXIT_LLM
}

private fun restService(): RestApiLLMService = RestApiLLMService(
    baseUrl = System.getenv("MCP_LLM_REST_BASE_URL") ?: "http://localhost:11434",
    modelName = System.getenv("MCP_LLM_REST_MODEL") ?: "llama3.2:3b",
    apiKey = System.getenv("MCP_LLM_REST_API_KEY") ?: System.getenv("OPENAI_API_KEY"),
)

private fun buildRequest(cli: CliArgs): StandupRequest {
    val tz = TimeZone.currentSystemDefault()
    val since = cli.since?.atStartOfDayIn(tz)
    val until = cli.since?.let { (cli.until ?: Clock.System.todayIn(tz)).plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz) }
    return StandupRequest(
        repoDir = cli.repoDir,
        author = cli.author,
        days = cli.days,
        since = since,
        until = until,
        promptType = if (cli.format == OutputFormat.JSON) PromptType.JSON else PromptType.SUMMARY,
        maxTokens = cli.maxTokens,
        temperature = cli.temperature,
        topP = LLMService.DEFAULT_TOP_P,
    )
}

private fun emit(text: String, outputFile: String?, out: PrintStream) {
    if (outputFile != null) {
        File(outputFile).writeText(text + "\n")
        System.err.println("Summary written to $outputFile")
    } else {
        out.println(text)
    }
}

private fun printUsage(out: PrintStream) {
    out.println(
        """
        Usage: standapp [OPTIONS] [REPO_DIR]

        Generate a daily standup summary from Git commit history with two local models:
        Qwen3 0.6B calls the get_recent_commits tool, Llama 3.2 3B writes the summary.
        Models are downloaded on first run (~2.7 GB) into the model cache.

        Arguments:
          REPO_DIR                Path to git repository (default: current directory)

        Options:
          -r, --repo DIR          Path to git repository
          -a, --author NAME       Filter commits by author name
          -d, --days N            Look back N days from now (default: 1)
              --since YYYY-MM-DD  Explicit window start (wins over --days; uses direct git access)
              --until YYYY-MM-DD  Explicit window end, inclusive (requires --since)
          -f, --format FMT        Output format: md (default) | json | text
          -b, --backend NAME      LLM backend: skainet | rest_api (overrides MCP_LLM_BACKEND)
              --commits MODE      qwen (default: tool call) | git (direct JGit access)
              --qwen-model PATH   Local GGUF for the tool-calling stage
          -m, --llama-model PATH  Local GGUF for the summariser (alias: --model)
              --keep-models       Keep both models loaded instead of releasing after each stage
          -o, --output FILE       Write the summary to FILE instead of stdout
              --score             Print a quality report to stderr (failed checks exit 5)
              --max-tokens N      Generation budget (default: 512)
              --temperature F     Sampling temperature (default: 0.1)
          -h, --help              Show this help

        Exit codes:
          0 success   2 usage error   3 git error / no commits
          4 LLM/backend/model error   5 validation failure (--score or unparseable JSON)

        Environment variables (flags win over env):
          MCP_LLM_BACKEND, MCP_LLM_REST_BASE_URL, MCP_LLM_REST_MODEL, MCP_LLM_REST_API_KEY,
          STANDAPP_QWEN_MODEL_PATH, STANDAPP_LLAMA_MODEL_PATH (or MCP_LLM_MODEL_PATH),
          STANDAPP_MODEL_CACHE_DIR (default ~/.cache/standapp/models), STANDAPP_OFFLINE=1,
          HF_TOKEN (optional Hugging Face token), STANDAPP_TINY_PROMPT=1 (inference smoke test)

        Examples:
          standapp --repo /path/to/repo --days 7 --score
          standapp --repo . --since 2026-07-01 --until 2026-07-11 --format json -o standup.json
          MCP_LLM_BACKEND=REST_API standapp --repo . --days 7
        """.trimIndent()
    )
}
