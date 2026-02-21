package dev.standapp.engine

import dev.standapp.engine.boundary.LLMBackend
import dev.standapp.engine.boundary.StandupEngine
import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.GenerationConfig
import dev.standapp.engine.entity.PromptType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertTrue(scored.scores.jsonParseable == true)
        assertTrue(scored.scores.jsonSchemaCompliant == true)
        assertTrue(scored.scores.allIdsValid)
        assertTrue(scored.scores.noHallucinatedIds)
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
}
