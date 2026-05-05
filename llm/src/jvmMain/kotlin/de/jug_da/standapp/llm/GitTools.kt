package de.jug_da.standapp.llm

import de.jug_da.data.git.commitsByAuthorAndPeriod
import de.jug_da.data.git.getAllCommitsInPeriod
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import sk.ainet.apps.kllama.chat.Tool
import sk.ainet.apps.kllama.chat.ToolDefinition

/**
 * `get_recent_commits` tool exposed to the LLM agent loop.
 *
 * Wraps the JGit-backed [getAllCommitsInPeriod] / [commitsByAuthorAndPeriod]
 * helpers from `:data` so the model can pull commits on demand instead of
 * receiving the full list pre-baked into the user prompt.
 *
 * Why: with a 1B model the whole-prompt summarisation path tends to truncate
 * or drift on long inputs. Tool-calling lets the model ask only for what it
 * needs, and the prompt the model sees stays small.
 */
class GetRecentCommitsTool(private val repoDir: String) : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = "get_recent_commits",
        description = "List git commits in the configured repository within the last N days. " +
            "Returns a compact text summary with id, author, date, and message for each commit.",
        parameters = Json.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "days": {
                  "type": "integer",
                  "description": "Number of days to look back from now (positive integer, e.g. 7)."
                },
                "author": {
                  "type": "string",
                  "description": "Optional. Author name filter; omit or pass empty string for all authors."
                }
              },
              "required": ["days"]
            }
            """.trimIndent()
        ) as JsonObject,
    )

    override fun execute(arguments: JsonObject): String {
        val days = (arguments["days"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
            ?: return "error: 'days' is required and must be an integer"
        if (days <= 0) return "error: 'days' must be positive"
        val author = (arguments["author"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

        System.err.println("[GitTool] get_recent_commits(repo=$repoDir, days=$days, author=${author ?: "*"})")

        val nowMs = System.currentTimeMillis()
        val now = Instant.fromEpochMilliseconds(nowMs)
        val start = Instant.fromEpochMilliseconds(nowMs - days * 86_400_000L)

        val commits = if (author != null) {
            commitsByAuthorAndPeriod(repoDir, author, start, now)
        } else {
            getAllCommitsInPeriod(repoDir, start, now)
        }
        if (commits.isEmpty()) {
            System.err.println("[GitTool] result: 0 commits")
            return "no commits found in the last $days day(s)" +
                if (author != null) " for author $author" else ""
        }
        // Cap at 50 commits — keeps the tool result well within the model's
        // context budget on a 1B; the model can re-invoke with a smaller
        // window if it needs more recent ones.
        val capped = commits.take(50)
        val text = buildString {
            appendLine("count=${capped.size}${if (commits.size > capped.size) " (truncated from ${commits.size})" else ""}")
            capped.forEach { c ->
                appendLine("- ${c.id.take(7)} ${c.whenDate} ${c.authorName}: ${c.message.lineSequence().first().take(120)}")
            }
        }.trimEnd()
        System.err.println("[GitTool] result: ${capped.size} commits, ${text.length} chars")
        return text
    }
}
