package de.jug_da.app.cli

import de.jug_da.data.git.commitsByAuthorAndPeriod
import de.jug_da.data.git.getAllCommitsInPeriod
import de.jug_da.standapp.llm.LLMBackendType
import de.jug_da.standapp.llm.LLMConfig
import de.jug_da.standapp.llm.LLMService
import de.jug_da.standapp.llm.LLMServiceFactory
import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.PromptType
import dev.standapp.engine.pipeline.PipelineResult
import dev.standapp.engine.pipeline.standupPipeline
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlin.time.Instant

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

    // Human-facing status goes to stderr so stdout stays clean for the
    // rendered summary (pipe- and --format json-friendly).
    val toolCalling = cli.toolCalling || System.getenv("STANDAPP_TOOL_CALLING") == "1"
    val backend = cli.backend ?: LLMBackendType.fromEnv()
    System.err.println("Daily StandAPP — repo=${cli.repoDir} backend=$backend toolCalling=$toolCalling format=${cli.format}")

    if (!File(cli.repoDir, ".git").exists()) {
        System.err.println("Error: '${cli.repoDir}' is not a git repository (no .git directory).")
        return EXIT_GIT
    }

    // Streaming tokens must not pollute machine-readable stdout. The same
    // goes for model-load chatter (factory/engine printlns, library banners):
    // when stdout carries the result, everything else is diverted to stderr
    // for the whole generation phase (restored before the final emit).
    val streamTokens = cli.format == OutputFormat.MD && cli.output == null
    val tokenSink: ((String) -> Unit)? = if (streamTokens) null else { _ -> }
    val redirectStdout = !streamTokens

    val realOut = System.out
    if (redirectStdout) System.setOut(PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.err), true))
    try {

    val service = try {
        when {
            toolCalling -> LLMServiceFactory.createToolCallingForGit(repoDir = cli.repoDir)
            cli.backend != null || cli.modelPath != null ->
                LLMServiceFactory.create(backend, buildConfig(cli), tokenSink)
            else -> LLMServiceFactory.create(tokenSink)
        }
    } catch (e: Exception) {
        System.err.println("Error creating LLM backend: ${e.message}")
        System.err.println("Set MCP_LLM_BACKEND=REST_API (or --backend rest_api) to use a remote endpoint.")
        return EXIT_LLM
    }

    // STANDAPP_TINY_PROMPT=1 → bypass summarisation and send a one-line
    // prompt. Verifies the inference path without the full prefill cost.
    if (System.getenv("STANDAPP_TINY_PROMPT") == "1") {
        val reply = runBlocking { service.generate("Say hello in three words.", cli.maxTokens, cli.temperature, LLMService.DEFAULT_TOP_P) }
        realOut.println(reply)
        return EXIT_OK
    }

    if (toolCalling) {
        return runToolCalling(cli, service, realOut)
    }

    val (start, end) = resolveWindow(cli)
    val gitInfos = if (cli.author != null) {
        commitsByAuthorAndPeriod(cli.repoDir, cli.author, start, end)
    } else {
        getAllCommitsInPeriod(cli.repoDir, start, end)
    }
    if (gitInfos.isEmpty()) {
        System.err.println("No commits found in the selected period ($start .. $end).")
        return EXIT_GIT
    }
    System.err.println("Found ${gitInfos.size} commit(s).")

    val commits = gitInfos.map {
        CommitInfo(
            id = it.id,
            authorName = it.authorName,
            authorEmail = it.authorEmail,
            date = it.whenDate.toString(),
            message = it.message,
        )
    }

    // The pipeline as code — preprocess, prompt, infer, and postprocess are
    // all declared right here (see dev.standapp.engine.pipeline).
    val pipeline = standupPipeline {
        preprocess { shortIds(7) }
        prompt { type = if (cli.format == OutputFormat.JSON) PromptType.JSON else PromptType.SUMMARY }
        infer { _, user -> service.generate(user, cli.maxTokens, cli.temperature, LLMService.DEFAULT_TOP_P) }
        postprocess {
            parseSummary()
            if (cli.score) score()
            if (cli.format == OutputFormat.JSON) retryOnInvalid(maxAttempts = 2)
        }
    }

    val result: PipelineResult = try {
        runBlocking { pipeline.run(commits) }
    } catch (e: Exception) {
        System.err.println("Error during generation: ${e.message}")
        return EXIT_LLM
    }
    if (result.attempts > 1) {
        System.err.println("(model output needed ${result.attempts} attempts)")
    }

    emit(OutputRenderer.render(result, cli.format), cli.output, realOut)

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
        if (redirectStdout) System.setOut(realOut)
    }
}

/**
 * Tool-calling keeps its own short prompt and skips the pipeline's prompt
 * stage by design: the model fetches commits itself via `get_recent_commits`.
 */
private fun runToolCalling(cli: CliArgs, service: LLMService, realOut: PrintStream): Int {
    val prompt = buildString {
        append("Summarise the work done in this git repository over the last ${cli.days} day(s). ")
        append("Call the `get_recent_commits` tool exactly once to fetch the commits")
        if (cli.author != null) append(" (pass author=\"${cli.author}\")")
        append(", then write a concise standup report with three markdown sections: ")
        append("`## Yesterday`, `## Today`, `## Blockers`. Reference commit IDs where useful.")
    }
    val summary = try {
        runBlocking { service.generate(prompt, cli.maxTokens, cli.temperature, LLMService.DEFAULT_TOP_P) }
    } catch (e: Exception) {
        System.err.println("Error during generation: ${e.message}")
        return EXIT_LLM
    }
    emit(summary.trim(), cli.output, realOut)
    return EXIT_OK
}

private fun resolveWindow(cli: CliArgs): Pair<Instant, Instant> {
    val tz = TimeZone.currentSystemDefault()
    return if (cli.since != null) {
        val untilDay = cli.until ?: Clock.System.todayIn(tz)
        cli.since.atStartOfDayIn(tz) to untilDay.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)
    } else {
        val now = Clock.System.now()
        Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - cli.days * 86_400_000L) to now
    }
}

private fun buildConfig(cli: CliArgs): LLMConfig = LLMConfig(
    modelPath = cli.modelPath ?: System.getenv("MCP_LLM_MODEL_PATH").orEmpty(),
    baseUrl = System.getenv("MCP_LLM_REST_BASE_URL") ?: "http://localhost:11434",
    modelName = System.getenv("MCP_LLM_REST_MODEL") ?: "llama3.2:3b",
    apiKey = System.getenv("MCP_LLM_REST_API_KEY") ?: System.getenv("OPENAI_API_KEY"),
)

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

        Generate a daily standup summary from Git commit history using a local LLM.

        Arguments:
          REPO_DIR                Path to git repository (default: current directory)

        Options:
          -r, --repo DIR          Path to git repository
          -a, --author NAME       Filter commits by author name
          -d, --days N            Look back N days from now (default: 1)
              --since YYYY-MM-DD  Explicit window start (wins over --days)
              --until YYYY-MM-DD  Explicit window end, inclusive (requires --since)
          -f, --format FMT        Output format: md (default) | json | text
          -b, --backend NAME      LLM backend: skainet | rest_api (overrides MCP_LLM_BACKEND)
          -m, --model PATH        GGUF model path (overrides MCP_LLM_MODEL_PATH, SKAINET only)
          -o, --output FILE       Write the summary to FILE instead of stdout
              --score             Print a quality report to stderr (failed checks exit 5)
              --max-tokens N      Generation budget (default: 512)
              --temperature F     Sampling temperature (default: 0.1; SKAINET clamps to >= 0.6)
              --tool-calling      Let the model fetch commits itself via the agent loop (SKAINET only)
          -h, --help              Show this help

        Exit codes:
          0 success   2 usage error   3 git error / no commits
          4 LLM/backend error   5 validation failure (--score or unparseable JSON)

        Environment variables (flags win over env):
          MCP_LLM_BACKEND, MCP_LLM_MODEL_PATH, MCP_LLM_REST_BASE_URL,
          MCP_LLM_REST_MODEL, MCP_LLM_REST_API_KEY,
          STANDAPP_TOOL_CALLING=1 (legacy for --tool-calling),
          STANDAPP_TINY_PROMPT=1 (inference smoke test)

        Examples:
          standapp --repo /path/to/repo --days 7 --score
          standapp --repo . --since 2026-07-01 --until 2026-07-11 --format json -o standup.json
          MCP_LLM_BACKEND=REST_API standapp --repo . --days 7
        """.trimIndent()
    )
}
