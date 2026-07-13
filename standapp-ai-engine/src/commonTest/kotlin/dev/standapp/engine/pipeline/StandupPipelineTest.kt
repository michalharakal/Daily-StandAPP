package dev.standapp.engine.pipeline

import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.PromptType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class StandupPipelineTest {

    private val commits = listOf(
        CommitInfo("a001b02c3d4e5f6", "Greta Fischer", "greta@jug-da.de", "2025-09-22T08:00:00Z", "Fix broken CI pipeline"),
        CommitInfo("c003d04e5f6a7b8", "Greta Fischer", "greta@jug-da.de", "2025-09-22T09:00:00Z", "Pin JVM toolchain"),
    )

    private val validMarkdown = """
        ## Yesterday
        - Fixed CI (a001b02)
        ## Today
        - Continue toolchain work
        ## Blockers
        - None
    """.trimIndent()

    @Test
    fun happy_path_parses_and_scores() = runTest {
        val pipeline = standupPipeline {
            preprocess { shortIds(7) }
            infer { _, _ -> validMarkdown }
            postprocess { parseSummary(); score() }
        }
        val result = pipeline.run(commits)

        assertEquals(1, result.attempts)
        assertEquals(3, result.result.summary.sections.size)
        assertEquals("Greta Fischer", result.result.summary.author)
        assertEquals("2025-09-22T09:00:00Z", result.result.summary.date)
        val scores = result.result.scores!!
        assertTrue(scores.headingsPresent == true)
        assertTrue(scores.noHallucinatedIds)
        assertEquals(scores.totalChecks, scores.passCount)
    }

    @Test
    fun hallucinated_id_is_flagged_by_scoring() = runTest {
        val hallucinating = """
            ## Yesterday
            - Did something ID: deadbee999
            ## Today
            - More
            ## Blockers
            - None
        """.trimIndent()
        val pipeline = standupPipeline {
            preprocess { shortIds(7) }
            infer { _, _ -> hallucinating }
            postprocess { parseSummary(); score() }
        }
        val result = pipeline.run(commits)
        assertFalse(result.result.scores!!.noHallucinatedIds)
    }

    @Test
    fun retry_on_invalid_reinfers_then_succeeds() = runTest {
        var calls = 0
        val validJson = """
            {"date": "2025-09-22", "author": "Greta Fischer",
             "categories": [{"name": "Fixes", "commits": [{"id": "a001b02", "summary": "CI fix", "status": "done"}]}],
             "blockers": []}
        """.trimIndent()
        val pipeline = standupPipeline {
            prompt { type = PromptType.JSON }
            infer { _, _ -> if (++calls == 1) "garbage, not json" else validJson }
            postprocess { parseSummary(); retryOnInvalid(maxAttempts = 2) }
        }
        val result = pipeline.run(commits)

        assertEquals(2, result.attempts)
        assertEquals("Fixes", result.result.summary.sections.single().name)
    }

    @Test
    fun exhausted_retries_fall_back_to_lenient_raw() = runTest {
        val pipeline = standupPipeline {
            prompt { type = PromptType.JSON }
            infer { _, _ -> "never json" }
            postprocess { parseSummary(); retryOnInvalid(maxAttempts = 2) }
        }
        val result = pipeline.run(commits)

        assertEquals(2, result.attempts)
        assertEquals("never json", result.result.summary.raw)
        assertTrue(result.result.summary.sections.isEmpty())
    }

    @Test
    fun preprocess_steps_shape_the_prompt() = runTest {
        val pipeline = standupPipeline {
            preprocess { shortIds(7); limit(1) }
            infer { _, _ -> validMarkdown }
        }
        val result = pipeline.run(commits)

        assertTrue(result.prompt.contains("ID: a001b02\n"), "prompt should contain the truncated id")
        assertFalse(result.prompt.contains("c003d04"), "limit(1) should drop the second commit")
        assertFalse(result.prompt.contains("a001b02c3d4e5f6"), "full id should be truncated")
    }

    @Test
    fun firstMessageLine_truncates_multiline_bodies_in_prompt() = runTest {
        val verbose = commits.map {
            it.copy(message = it.message + "\n\nLong explanatory body\nwith several lines that must not reach the prompt")
        }
        val pipeline = standupPipeline {
            preprocess { firstMessageLine(20) }
            infer { _, _ -> validMarkdown }
        }
        val result = pipeline.run(verbose)
        assertFalse(result.prompt.contains("Long explanatory body"))
        assertTrue(result.prompt.contains("Message: Fix broken CI pipel\n") || result.prompt.contains("Fix broken CI pipel"))
    }

    @Test
    fun default_postprocess_passes_raw_through() = runTest {
        val pipeline = standupPipeline {
            infer { _, _ -> "anything" }
        }
        val result = pipeline.run(commits)
        assertEquals("anything", result.result.summary.raw)
        assertTrue(result.result.summary.sections.isEmpty())
        assertEquals(null, result.result.scores)
    }

    @Test
    fun missing_infer_stage_fails_at_build_time() {
        assertFailsWith<IllegalStateException> {
            standupPipeline { prompt { type = PromptType.JSON } }
        }
    }

    @Test
    fun infer_exceptions_propagate_untouched() = runTest {
        class BoomException : RuntimeException("boom")
        val pipeline = standupPipeline {
            infer { _, _ -> throw BoomException() }
        }
        assertFailsWith<BoomException> { pipeline.run(commits) }
    }
}
