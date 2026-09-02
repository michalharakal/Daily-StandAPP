package de.jug_da.standapp.llm

import de.jug_da.data.git.GitInfo
import de.jug_da.data.git.commitsByAuthorAndPeriod
import de.jug_da.data.git.getAllCommitsInPeriod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import sk.ainet.apps.kllama.chat.Tool
import sk.ainet.apps.kllama.chat.ToolDefinition
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Commit lookup behind a tiny interface so pipeline stages and tests can
 * swap the JGit implementation from `:data` for a fake.
 */
fun interface GitCommitSource {
    fun fetch(repoDir: String, author: String?, start: Instant, end: Instant): List<GitInfo>

    companion object {
        /** JGit-backed source from the `:data` module. */
        fun jgit(): GitCommitSource = GitCommitSource { repoDir, author, start, end ->
            if (author != null) commitsByAuthorAndPeriod(repoDir, author, start, end)
            else getAllCommitsInPeriod(repoDir, start, end)
        }
    }
}

/**
 * `get_recent_commits` tool exposed to the Qwen tool-calling stage.
 *
 * The model's job is only to translate the user's request into a well-formed
 * call (`days` as a number, optional `author`). The tool records the
 * structured commit list in [recorded] — that list, not the model's prose,
 * is what the pipeline consumes downstream. The returned string is a short
 * acknowledgement because the agent loop runs a single tool round and never
 * feeds the result back to the model.
 */
class RecordingGitCommitsTool(
    private val repoDir: String,
    private val source: GitCommitSource = GitCommitSource.jgit(),
    private val clock: () -> Instant = { Clock.System.now() },
    private val log: (String) -> Unit = System.err::println,
) : Tool {

    @Volatile
    var recorded: List<GitInfo>? = null
        private set

    @Volatile
    var recordedDays: Int? = null
        private set

    @Volatile
    var recordedAuthor: String? = null
        private set

    @Volatile
    var failure: Throwable? = null
        private set

    override val definition: ToolDefinition = ToolDefinition(
        name = TOOL_NAME,
        description = "List the git commits of the repository from the last N days. " +
            "Returns the number of commits found; the commit details are handed to the summariser.",
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
                  "description": "Optional author name filter. Omit for all authors."
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

        log("[GitTool] $TOOL_NAME(repo=$repoDir, days=$days, author=${author ?: "*"})")
        return try {
            val now = clock()
            val start = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - days * 86_400_000L)
            val commits = source.fetch(repoDir, author, start, now)
            recorded = commits
            recordedDays = days
            recordedAuthor = author
            log("[GitTool] result: ${commits.size} commit(s) recorded")
            "ok: ${commits.size} commit(s) found in the last $days day(s)" +
                (author?.let { " by $it" } ?: "")
        } catch (t: Throwable) {
            // ToolRegistry.execute would swallow this into a string; keep the
            // cause so the stage can surface it as a git error.
            failure = t
            "error: ${t.message}"
        }
    }

    companion object {
        const val TOOL_NAME = "get_recent_commits"
    }
}
