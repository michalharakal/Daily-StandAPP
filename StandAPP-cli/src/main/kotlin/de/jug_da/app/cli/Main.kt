@file:Suppress("DEPRECATION")

package de.jug_da.app.cli

import de.jug_da.data.git.commitsByAuthorAndPeriod
import de.jug_da.data.git.getAllCommitsInPeriod
import de.jug_da.standapp.llm.LLMBackendType
import de.jug_da.standapp.llm.LLMService
import de.jug_da.standapp.llm.LLMServiceFactory
import dev.standapp.engine.control.PromptBuilder
import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.PromptType
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant

fun main(args: Array<String>) {
    val config = parseArgs(args)

    println("Daily StandAPP - Standup Summary Generator")
    println("=".repeat(45))
    println("Repository: ${config.repoDir}")
    println("Period:     ${config.days} day(s)")
    if (config.author != null) println("Author:     ${config.author}")
    println()

    val backend = LLMBackendType.fromEnv()
    println("Generating standup summary (backend: $backend)…")
    println()

    val service = try {
        LLMServiceFactory.create()
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        System.err.println()
        System.err.println("Set MCP_LLM_BACKEND=REST_API to use a remote OpenAI-compatible endpoint.")
        return
    }

    // STANDAPP_TINY_PROMPT=1 → bypass the heavy summarisation template
    // and just send a one-line prompt. Useful for verifying the inference
    // path is alive without paying the prefill cost of a 1700-token prompt.
    val summaryPrompt = if (System.getenv("STANDAPP_TINY_PROMPT") == "1") {
        "Say hello in three words."
    } else {
        buildSummarisationPrompt(config) ?: run {
            println("No commits found in the last ${config.days} day(s).")
            return
        }
    }

    val summary = runBlocking {
        service.generate(
            prompt = summaryPrompt,
            temperature = LLMService.DEFAULT_TEMPERATURE,
            topP = LLMService.DEFAULT_TOP_P,
        )
    }

    println(summary)
}

private fun buildSummarisationPrompt(config: CliConfig): String? {
    val nowMs = System.currentTimeMillis()
    val now = Instant.fromEpochMilliseconds(nowMs)
    val start = Instant.fromEpochMilliseconds(nowMs - config.days * 86_400_000L)

    val gitInfos = if (config.author != null) {
        commitsByAuthorAndPeriod(config.repoDir, config.author, start, now)
    } else {
        getAllCommitsInPeriod(config.repoDir, start, now)
    }
    if (gitInfos.isEmpty()) return null
    println("Found ${gitInfos.size} commit(s).")
    println()

    val commits = gitInfos.map {
        CommitInfo(
            id = it.id.take(7),
            authorName = it.authorName,
            authorEmail = it.authorEmail,
            date = it.whenDate.toString(),
            message = it.message,
        )
    }
    return PromptBuilder().buildUserPrompt(commits, PromptType.SUMMARY)
}

private data class CliConfig(
    val repoDir: String,
    val author: String? = null,
    val days: Int = 1,
)

private fun parseArgs(args: Array<String>): CliConfig {
    var repoDir = "."
    var author: String? = null
    var days = 1

    val iter = args.iterator()
    while (iter.hasNext()) {
        when (val arg = iter.next()) {
            "--repo", "-r" -> repoDir = iter.next()
            "--author", "-a" -> author = iter.next()
            "--days", "-d" -> days = iter.next().toInt()
            "--help", "-h" -> {
                printUsage()
                kotlin.system.exitProcess(0)
            }
            else -> {
                // First positional argument is repo dir
                if (!arg.startsWith("-")) repoDir = arg
            }
        }
    }

    return CliConfig(repoDir = repoDir, author = author, days = days)
}

private fun printUsage() {
    println("""
        Usage: standapp [OPTIONS] [REPO_DIR]

        Generate a daily standup summary from Git commit history.

        Arguments:
          REPO_DIR              Path to git repository (default: current directory)

        Options:
          -r, --repo DIR        Path to git repository
          -a, --author NAME     Filter commits by author name
          -d, --days N          Number of days to look back (default: 1)
          -h, --help            Show this help message

        Environment variables:
          MCP_LLM_BACKEND       Optional. SKAINET (default, embedded Llama 3.2 1B) or REST_API.
          MCP_LLM_MODEL_PATH    Optional. Override the embedded GGUF (SKAINET only).
          MCP_LLM_REST_BASE_URL REST endpoint (default: http://localhost:11434)
          MCP_LLM_REST_MODEL    Model name for REST API (default: llama3.2:3b)

        Example (uses embedded model):
          standapp --repo /path/to/repo --days 7

        Example (REST backend):
          MCP_LLM_BACKEND=REST_API standapp --repo /path/to/repo --days 7
    """.trimIndent())
}
