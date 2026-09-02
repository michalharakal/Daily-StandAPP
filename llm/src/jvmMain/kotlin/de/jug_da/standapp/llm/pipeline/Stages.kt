package de.jug_da.standapp.llm.pipeline

import de.jug_da.data.git.GitInfo
import de.jug_da.standapp.llm.GitCommitSource
import de.jug_da.standapp.llm.LLMService
import dev.standapp.engine.control.PromptBuilder
import dev.standapp.engine.pipeline.PreprocessBuilder
import dev.standapp.engine.pipeline.PromptSpec
import sk.ainet.data.source.PipelineStage
import kotlin.time.Clock
import kotlin.time.Instant

/** Fetch commits directly through JGit for the request's window. */
class GitCommitsStage(
    private val source: GitCommitSource = GitCommitSource.jgit(),
    private val clock: () -> Instant = { Clock.System.now() },
    private val commitSource: CommitSource = CommitSource.DIRECT,
) : PipelineStage<StandupRequest, CommitBatch> {
    override val name: String = "git-commits"

    override suspend fun process(input: StandupRequest): CommitBatch =
        CommitBatch(input, fetch(input).map { it.toCommitInfo() }, commitSource)

    fun fetch(request: StandupRequest): List<GitInfo> {
        val (start, end) = window(request)
        StageLog.stage("GIT", "repo=${request.repoDir} author=${request.author ?: "*"} window=$start .. $end")
        return try {
            source.fetch(request.repoDir, request.author, start, end)
        } catch (t: Throwable) {
            throw GitAccessException("git access failed for ${request.repoDir}: ${t.message}", t)
        }
    }

    private fun window(request: StandupRequest): Pair<Instant, Instant> {
        val since = request.since
        if (since != null) return since to (request.until ?: clock())
        val now = clock()
        return Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - request.days * 86_400_000L) to now
    }
}

/** Fail the pipeline early with a clear message when the window is empty. */
class RequireCommitsStage : PipelineStage<CommitBatch, CommitBatch> {
    override val name: String = "require-commits"

    override suspend fun process(input: CommitBatch): CommitBatch {
        if (input.commits.isEmpty()) {
            val r = input.request
            val window = if (r.since != null) "${r.since} .. ${r.until ?: "now"}" else "last ${r.days} day(s)"
            throw NoCommitsException(
                "No commits found in ${r.repoDir} for $window" + (r.author?.let { " by $it" } ?: "") +
                    " (source=${input.source})"
            )
        }
        StageLog.stage("COMMITS", "${input.commits.size} commit(s) via ${input.source}")
        return input
    }
}

/** Commit-list transformations declared with the engine's `preprocess { }` vocabulary. */
class PreprocessStage(block: PreprocessBuilder.() -> Unit) : PipelineStage<CommitBatch, CommitBatch> {
    private val fn = PreprocessBuilder().apply(block).build()
    override val name: String = "preprocess"

    override suspend fun process(input: CommitBatch): CommitBatch = input.copy(commits = fn(input.commits))
}

/** Render system + user prompts from the engine's templates. */
class PromptStage(private val spec: PromptSpec) : PipelineStage<CommitBatch, PromptedBatch> {
    override val name: String = "prompt"

    override suspend fun process(input: CommitBatch): PromptedBatch {
        val builder = PromptBuilder(spec.system, spec.summaryTemplate, spec.jsonTemplate)
        val pair = PromptPair(
            system = builder.buildSystemPrompt(),
            user = builder.buildUserPrompt(input.commits, input.request.promptType),
        )
        StageLog.stage("PROMPT", "type=${input.request.promptType} system=${pair.system.length}ch user=${pair.user.length}ch")
        return PromptedBatch(input, pair)
    }
}

/** Generation through any [LLMService] (REST backend, test fakes). */
class LLMServiceGenerateStage(
    private val service: LLMService,
    override val name: String = "llm-service",
) : PipelineStage<PromptedBatch, RawOutput> {
    private var attempt = 0

    override suspend fun process(input: PromptedBatch): RawOutput {
        attempt++
        val r = input.batch.request
        val raw = service.generate(input.prompt.user, r.maxTokens, r.temperature, r.topP)
        return RawOutput(input, raw, attempt)
    }
}
