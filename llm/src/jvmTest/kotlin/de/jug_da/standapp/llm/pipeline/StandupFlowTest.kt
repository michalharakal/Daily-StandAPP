package de.jug_da.standapp.llm.pipeline

import de.jug_da.data.git.GitInfo
import de.jug_da.standapp.llm.GitCommitSource
import de.jug_da.standapp.llm.LLMService
import de.jug_da.standapp.llm.model.LocalModel
import de.jug_da.standapp.llm.model.ModelProvider
import de.jug_da.standapp.llm.model.ModelSpec
import dev.standapp.engine.entity.PromptType
import kotlinx.coroutines.test.runTest
import sk.ainet.data.source.DataPipelineException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class StandupFlowTest {

    private val gitInfos = listOf(
        GitInfo("a001b02c3d4e5f6", "Greta Fischer", "greta@jug-da.de", Instant.parse("2025-09-22T08:00:00Z"), "Fix broken CI pipeline"),
        GitInfo("c003d04e5f6a7b8", "Greta Fischer", "greta@jug-da.de", Instant.parse("2025-09-22T09:00:00Z"), "Pin JVM toolchain"),
        GitInfo("e005f06a7b8c9d0", "Greta Fischer", "greta@jug-da.de", Instant.parse("2025-09-22T10:00:00Z"), "Merge branch 'develop'"),
    )

    private val validMarkdown = """
        ## Yesterday
        - Fixed CI (a001b02)
        ## Today
        - Continue toolchain work
        ## Blockers
        - None
    """.trimIndent()

    private val validJson = """
        {"date": "2025-09-22", "author": "Greta Fischer",
         "categories": [{"name": "Fixes", "commits": [{"id": "a001b02", "summary": "CI fix", "status": "done"}]}],
         "blockers": []}
    """.trimIndent()

    /** Tests never touch a real model. */
    private val noModels = object : ModelProvider {
        override suspend fun <R> withModel(spec: ModelSpec, block: suspend (LocalModel) -> R): R =
            error("no models available in unit tests (${spec.id})")

        override fun close() {}
    }

    private class RecordingSource(private val result: List<GitInfo>) : GitCommitSource {
        val calls = mutableListOf<Triple<String, String?, Pair<Instant, Instant>>>()
        override fun fetch(repoDir: String, author: String?, start: Instant, end: Instant): List<GitInfo> {
            calls += Triple(repoDir, author, start to end)
            return result
        }
    }

    private class FakeService(private val responses: List<String>) : LLMService {
        val prompts = mutableListOf<String>()
        override suspend fun generate(prompt: String, maxTokens: Int, temperature: Float, topP: Float): String {
            prompts += prompt
            return responses[minOf(prompts.size, responses.size) - 1]
        }
    }

    @Test
    fun describe_lists_the_stage_chain_in_order() {
        val flow = standupFlow(noModels) {
            commits = gitCommits(RecordingSource(gitInfos))
            preprocess { shortIds(7) }
            transform("drop-merges") { it }
            summarize(llmService(FakeService(listOf(validMarkdown)), name = "fake-llm"))
            postprocess { parseSummary(); retryOnInvalid(2) }
        }
        assertEquals(
            "git-commits -> require-commits -> preprocess -> drop-merges -> summarize[prompt -> fake-llm -> parse x2]",
            flow.describe(),
        )
    }

    @Test
    fun happy_path_parses_and_scores() = runTest {
        val service = FakeService(listOf(validMarkdown))
        var seen: CommitBatch? = null
        val flow = standupFlow(noModels) {
            commits = gitCommits(RecordingSource(gitInfos))
            preprocess { shortIds(7); firstMessageLine(120) }
            transform("capture") { seen = it; it }
            summarize(llmService(service))
            postprocess { parseSummary(); score() }
        }
        val result = flow.execute(StandupRequest(repoDir = "/repo", days = 3))

        assertEquals(1, result.attempts)
        assertEquals(3, result.result.summary.sections.size)
        assertEquals("Greta Fischer", result.result.summary.author)
        assertEquals(CommitSource.DIRECT, seen?.source)
        assertEquals(listOf("a001b02", "c003d04", "e005f06"), seen?.commits?.map { it.id })
        val scores = result.result.scores!!
        assertEquals(scores.totalChecks, scores.passCount)
        assertTrue(service.prompts.single().contains("ID: a001b02"))
    }

    @Test
    fun retry_on_invalid_json_reinfers_then_succeeds() = runTest {
        val service = FakeService(listOf("garbage, not json", validJson))
        val flow = standupFlow(noModels) {
            commits = gitCommits(RecordingSource(gitInfos))
            summarize(llmService(service))
            postprocess { parseSummary(); retryOnInvalid(maxAttempts = 2) }
        }
        val result = flow.execute(StandupRequest(repoDir = "/repo", promptType = PromptType.JSON))

        assertEquals(2, result.attempts)
        assertEquals(2, service.prompts.size)
        assertEquals("Fixes", result.result.summary.sections.single().name)
    }

    @Test
    fun custom_transform_shapes_the_prompt() = runTest {
        val service = FakeService(listOf(validMarkdown))
        val flow = standupFlow(noModels) {
            commits = gitCommits(RecordingSource(gitInfos))
            transform("drop-merges") { b -> b.copy(commits = b.commits.filterNot { it.message.startsWith("Merge") }) }
            summarize(llmService(service))
        }
        val result = flow.execute(StandupRequest(repoDir = "/repo"))
        assertFalse(result.prompt.contains("Merge branch"))
        assertTrue(result.prompt.contains("Pin JVM toolchain"))
    }

    @Test
    fun empty_window_fails_with_no_commits() = runTest {
        val flow = standupFlow(noModels) {
            commits = gitCommits(RecordingSource(emptyList()))
            summarize(llmService(FakeService(listOf(validMarkdown))))
        }
        val e = assertFailsWith<NoCommitsException> { flow.execute(StandupRequest(repoDir = "/repo", days = 2, author = "Greta")) }
        assertTrue(e.message!!.contains("last 2 day(s)") && e.message!!.contains("Greta"))
    }

    @Test
    fun explicit_window_reaches_the_git_source() = runTest {
        val source = RecordingSource(gitInfos)
        val since = Instant.parse("2025-09-01T00:00:00Z")
        val until = Instant.parse("2025-09-30T00:00:00Z")
        val flow = standupFlow(noModels) {
            commits = gitCommits(source)
            summarize(llmService(FakeService(listOf(validMarkdown))))
        }
        flow.execute(StandupRequest(repoDir = "/repo", author = "Greta", since = since, until = until))
        val (repo, author, window) = source.calls.single()
        assertEquals("/repo", repo)
        assertEquals("Greta", author)
        assertEquals(since to until, window)
    }

    @Test
    fun qwen_stage_falls_back_to_direct_git_when_the_model_fails() = runTest {
        val source = RecordingSource(gitInfos)
        var seen: CommitBatch? = null
        val flow = standupFlow(noModels) {
            commits = qwenToolCall(source = source)
            transform("capture") { seen = it; it }
            summarize(llmService(FakeService(listOf(validMarkdown))))
            postprocess { parseSummary() }
        }
        val result = flow.execute(StandupRequest(repoDir = "/repo", days = 5))

        assertEquals(CommitSource.DIRECT_FALLBACK, seen?.source)
        assertEquals(3, seen?.commits?.size)
        assertEquals(3, result.result.summary.sections.size)
        assertEquals(1, source.calls.size)
    }

    @Test
    fun qwen_stage_rejects_explicit_windows() = runTest {
        val flow = standupFlow(noModels) {
            commits = qwenToolCall(source = RecordingSource(gitInfos))
            summarize(llmService(FakeService(listOf(validMarkdown))))
        }
        assertFailsWith<DataPipelineException> {
            flow.execute(StandupRequest(repoDir = "/repo", since = Instant.parse("2025-09-01T00:00:00Z")))
        }
    }

    @Test
    fun finish_hooks_see_the_result() = runTest {
        var attempts = -1
        val flow = standupFlow(noModels) {
            commits = gitCommits(RecordingSource(gitInfos))
            summarize(llmService(FakeService(listOf(validMarkdown))))
            finish("record") { attempts = it.attempts; it }
        }
        flow.execute(StandupRequest(repoDir = "/repo"))
        assertEquals(1, attempts)
    }
}
