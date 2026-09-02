package de.jug_da.standapp.llm.pipeline

import de.jug_da.data.git.GitInfo
import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.PromptType
import dev.standapp.engine.pipeline.PipelineResult
import kotlin.time.Instant

/**
 * Input of the standup flow: what to summarise and how.
 *
 * Either [since]/[until] (explicit window, `until` exclusive) or [days]
 * (look back from now) selects the commits. The Qwen tool stage understands
 * only day-based windows; explicit windows go through the direct git stage.
 */
data class StandupRequest(
    val repoDir: String,
    val author: String? = null,
    val days: Int = 1,
    val since: Instant? = null,
    val until: Instant? = null,
    val promptType: PromptType = PromptType.SUMMARY,
    val maxTokens: Int = 512,
    val temperature: Float = 0.1f,
    val topP: Float = 0.9f,
)

/** How a [CommitBatch] was obtained. */
enum class CommitSource {
    /** Qwen called `get_recent_commits` with valid arguments. */
    QWEN_TOOL_CALL,

    /** Direct JGit access (explicit window, REST backend, or `--commits git`). */
    DIRECT,

    /** Qwen did not produce a usable tool call; commits were fetched directly instead. */
    DIRECT_FALLBACK,
}

data class CommitBatch(
    val request: StandupRequest,
    val commits: List<CommitInfo>,
    val source: CommitSource,
)

data class PromptPair(val system: String, val user: String)

data class PromptedBatch(val batch: CommitBatch, val prompt: PromptPair)

data class RawOutput(val prompted: PromptedBatch, val raw: String, val attempt: Int)

/** Final pipeline output; re-exported so callers need only this package. */
typealias StandupResult = PipelineResult

/** Git access failed (bad repository, JGit error, tool failure). CLI exit code 3. */
open class GitAccessException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** The selected window contains no commits. CLI exit code 3. */
class NoCommitsException(message: String) : GitAccessException(message)

fun GitInfo.toCommitInfo(): CommitInfo = CommitInfo(
    id = id,
    authorName = authorName,
    authorEmail = authorEmail,
    date = whenDate.toString(),
    message = message,
)
