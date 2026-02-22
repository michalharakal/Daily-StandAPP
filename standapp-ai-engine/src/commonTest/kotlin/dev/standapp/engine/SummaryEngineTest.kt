package dev.standapp.engine

import dev.standapp.engine.boundary.LLMBackend
import dev.standapp.engine.boundary.StandupEngine
import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.GenerationConfig
import dev.standapp.engine.entity.PromptType
import dev.standapp.engine.entity.SummaryProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SummaryEngineTest {

    private val mockSummaryOutput = """
        ## Yesterday
        - Fixed login bug (abc1234)

        ## Today
        - Continue authentication work

        ## Blockers
        - None
    """.trimIndent()

    private val mockJsonOutput = """{"date":"2025-01-15","author":"Alice","categories":[{"name":"Bug Fixes","commits":[{"id":"abc1234","summary":"Fixed login bug","status":"done"}]}],"blockers":[]}"""

    private val commits = listOf(
        CommitInfo("abc1234", "Alice", "alice@example.com", "2025-01-15T10:30:00Z", "Fix login bug"),
    )

    private fun mockBackend(output: String) = object : LLMBackend {
        override suspend fun generate(prompt: String, config: GenerationConfig): String = output
    }

    private fun streamingMockBackend(vararg tokens: String) = object : LLMBackend {
        override suspend fun generate(prompt: String, config: GenerationConfig): String =
            tokens.joinToString("")

        override fun generateStream(prompt: String, config: GenerationConfig): Flow<String> = flow {
            for (token in tokens) emit(token)
        }
    }

    @Test
    fun summarizeSummaryMode() = runTest {
        val engine = StandupEngine {
            backend = mockBackend(mockSummaryOutput)
        }
        val result = engine.summarize(commits, PromptType.SUMMARY)
        assertEquals(PromptType.SUMMARY, result.promptType)
        assertEquals(3, result.sections.size)
        assertTrue(result.raw.contains("## Yesterday"))
    }

    @Test
    fun summarizeJsonMode() = runTest {
        val engine = StandupEngine {
            backend = mockBackend(mockJsonOutput)
        }
        val result = engine.summarize(commits, PromptType.JSON)
        assertEquals(PromptType.JSON, result.promptType)
        assertEquals("Alice", result.author)
        assertEquals("2025-01-15", result.date)
    }

    @Test
    fun summarizeAndScore() = runTest {
        val engine = StandupEngine {
            backend = mockBackend(mockJsonOutput)
            scoring = true
        }
        val scored = engine.summarizeAndScore(commits, PromptType.JSON)
        assertTrue(scored.scores?.jsonParseable == true)
        assertTrue(scored.scores?.jsonSchemaCompliant == true)
        assertTrue(scored.scores!!.allIdsValid)
        assertTrue(scored.scores!!.noHallucinatedIds)
    }

    @Test
    fun customPrompts() = runTest {
        var receivedPrompt = ""
        val engine = StandupEngine {
            backend = object : LLMBackend {
                override suspend fun generate(prompt: String, config: GenerationConfig): String {
                    receivedPrompt = prompt
                    return mockSummaryOutput
                }
            }
            prompts {
                system = "Custom system"
            }
        }
        engine.summarize(commits, PromptType.SUMMARY)
        assertTrue(receivedPrompt.contains("Custom system"))
    }

    @Test
    fun progressEmitsCorrectSequenceWithoutScoring() = runTest {
        val engine = StandupEngine {
            backend = mockBackend(mockSummaryOutput)
            scoring = false
        }
        val events = engine.summarizeWithProgress(commits, PromptType.SUMMARY).toList()

        assertIs<SummaryProgress.BuildingPrompt>(events[0])
        assertIs<SummaryProgress.Generating>(events[1])
        assertIs<SummaryProgress.Streaming>(events[2])
        assertIs<SummaryProgress.Parsing>(events[3])

        val complete = events[4]
        assertIs<SummaryProgress.Complete>(complete)
        assertNull(complete.result.scores)
        assertEquals(PromptType.SUMMARY, complete.result.summary.promptType)
    }

    @Test
    fun progressEmitsCorrectSequenceWithScoring() = runTest {
        val engine = StandupEngine {
            backend = mockBackend(mockJsonOutput)
            scoring = true
        }
        val events = engine.summarizeWithProgress(commits, PromptType.JSON).toList()

        assertIs<SummaryProgress.BuildingPrompt>(events[0])
        assertIs<SummaryProgress.Generating>(events[1])
        assertIs<SummaryProgress.Streaming>(events[2])
        assertIs<SummaryProgress.Parsing>(events[3])
        assertIs<SummaryProgress.Scoring>(events[4])

        val complete = events[5]
        assertIs<SummaryProgress.Complete>(complete)
        assertNotNull(complete.result.scores)
        assertTrue(complete.result.scores!!.noHallucinatedIds)
    }

    @Test
    fun progressStreamsTokenByToken() = runTest {
        val engine = StandupEngine {
            backend = streamingMockBackend("## Yesterday\n", "- Fixed bug\n", "## Today\n", "- Work\n", "## Blockers\n", "- None\n")
            scoring = false
        }
        val events = engine.summarizeWithProgress(commits, PromptType.SUMMARY).toList()

        val streamingEvents = events.filterIsInstance<SummaryProgress.Streaming>()
        assertEquals(6, streamingEvents.size)
        assertEquals("## Yesterday\n", streamingEvents[0].tokenDelta)
        assertEquals("## Yesterday\n- Fixed bug\n", streamingEvents[1].accumulated)

        val complete = events.last()
        assertIs<SummaryProgress.Complete>(complete)
        assertEquals(3, complete.result.summary.sections.size)
    }

    @Test
    fun progressEmitsFailedOnError() = runTest {
        val engine = StandupEngine {
            backend = object : LLMBackend {
                override suspend fun generate(prompt: String, config: GenerationConfig): String =
                    throw RuntimeException("LLM unavailable")
            }
        }
        val events = engine.summarizeWithProgress(commits, PromptType.SUMMARY).toList()

        assertIs<SummaryProgress.BuildingPrompt>(events[0])
        assertIs<SummaryProgress.Generating>(events[1])

        val failed = events.last()
        assertIs<SummaryProgress.Failed>(failed)
        assertEquals("LLM unavailable", failed.error.message)
    }
}
