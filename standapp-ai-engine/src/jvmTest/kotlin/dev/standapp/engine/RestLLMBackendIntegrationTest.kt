package dev.standapp.engine

import dev.standapp.engine.boundary.RestLLMBackend
import dev.standapp.engine.boundary.StandupEngine
import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.PromptType
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Integration test that calls a real Ollama endpoint.
 *
 * Skipped unless the `OLLAMA_INTEGRATION_TEST` environment variable is set to "true".
 * Run with: OLLAMA_INTEGRATION_TEST=true ./gradlew :standapp-ai-engine:jvmTest
 */
class RestLLMBackendIntegrationTest {

    private val ollamaUrl = System.getenv("BENCH_LOCAL_URL") ?: "http://localhost:11434"
    private val ollamaModel = System.getenv("BENCH_LOCAL_MODEL") ?: "llama3.2:3b"

    private val commits = listOf(
        CommitInfo("abc1234", "Alice", "alice@example.com", "2025-01-15T10:30:00Z", "Fix login bug in OAuth flow"),
        CommitInfo("def5678", "Alice", "alice@example.com", "2025-01-15T14:00:00Z", "Add unit tests for auth module"),
    )

    @Test
    fun summarizeSummaryModeWithOllama() = runTest {
        assumeTrue(
            "Skipped: set OLLAMA_INTEGRATION_TEST=true to run",
            System.getenv("OLLAMA_INTEGRATION_TEST") == "true",
        )

        val engine = StandupEngine {
            backend = RestLLMBackend(baseUrl = ollamaUrl, model = ollamaModel)
            maxTokens = 256
        }

        val result = engine.summarize(commits, PromptType.SUMMARY)
        assertTrue(result.raw.isNotBlank(), "LLM should return non-empty output")
        println("=== SUMMARY OUTPUT ===\n${result.raw}")
    }

    @Test
    fun summarizeAndScoreJsonModeWithOllama() = runTest {
        assumeTrue(
            "Skipped: set OLLAMA_INTEGRATION_TEST=true to run",
            System.getenv("OLLAMA_INTEGRATION_TEST") == "true",
        )

        val engine = StandupEngine {
            backend = RestLLMBackend(baseUrl = ollamaUrl, model = ollamaModel)
            maxTokens = 512
        }

        val scored = engine.summarizeAndScore(commits, PromptType.JSON)
        assertTrue(scored.summary.raw.isNotBlank(), "LLM should return non-empty output")
        println("=== JSON OUTPUT ===\n${scored.summary.raw}")
        println("=== SCORES ===\n${scored.scores}")
    }
}
