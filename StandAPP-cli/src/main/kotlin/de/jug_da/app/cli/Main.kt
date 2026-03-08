@file:Suppress("DEPRECATION")

package de.jug_da.app.cli

import de.jug_da.data.git.commitsByAuthorAndPeriod
import de.jug_da.data.git.getAllCommitsInPeriod
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

    // 1. Get commits from git repo
    val nowMs = System.currentTimeMillis()
    val now = Instant.fromEpochMilliseconds(nowMs)
    val start = Instant.fromEpochMilliseconds(nowMs - config.days * 86_400_000L)

    val gitInfos = if (config.author != null) {
        commitsByAuthorAndPeriod(config.repoDir, config.author, start, now)
    } else {
        getAllCommitsInPeriod(config.repoDir, start, now)
    }

    if (gitInfos.isEmpty()) {
        println("No commits found in the last ${config.days} day(s).")
        return
    }

    println("Found ${gitInfos.size} commit(s).")
    println()

    // 2. Map GitInfo -> CommitInfo for the prompt builder
    val commits = gitInfos.map { git ->
        CommitInfo(
            id = git.id.take(7),
            authorName = git.authorName,
            authorEmail = git.authorEmail,
            date = git.whenDate.toString(),
            message = git.message,
        )
    }

    // 3. Build prompt
    val promptBuilder = PromptBuilder()
    val prompt = promptBuilder.buildUserPrompt(commits, PromptType.SUMMARY)

    // 4. Call LLM
    println("Generating standup summary (backend: ${System.getenv("MCP_LLM_BACKEND") ?: "not set"})...")
    println()

    val service = try {
        LLMServiceFactory.create()
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        System.err.println()
        System.err.println("Set MCP_LLM_BACKEND to one of: SKAINET, DELIVERANCE, REST_API")
        System.err.println("  SKAINET:     also set MCP_LLM_MODEL_PATH=/path/to/model.gguf")
        System.err.println("  DELIVERANCE: optionally set MCP_LLM_DELIVERANCE_OWNER, MCP_LLM_DELIVERANCE_MODEL")
        System.err.println("  REST_API:    optionally set MCP_LLM_REST_BASE_URL, MCP_LLM_REST_MODEL")
        return
    }

    val summary = runBlocking {
        service.generate(
            prompt = prompt,
            temperature = LLMService.DEFAULT_TEMPERATURE,
            topP = LLMService.DEFAULT_TOP_P,
        )
    }

    println(summary)
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
          MCP_LLM_BACKEND       Required. One of: SKAINET, DELIVERANCE, REST_API
          MCP_LLM_MODEL_PATH    GGUF model path (SKAINET backend)
          MCP_LLM_REST_BASE_URL REST API endpoint (default: http://localhost:11434)
          MCP_LLM_REST_MODEL    Model name for REST API (default: llama3.2:3b)

        Example:
          MCP_LLM_BACKEND=REST_API standapp --repo /path/to/repo --days 7
    """.trimIndent())
}
