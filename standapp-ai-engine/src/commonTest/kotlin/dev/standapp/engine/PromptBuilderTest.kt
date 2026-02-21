package dev.standapp.engine

import dev.standapp.engine.control.DefaultPrompts
import dev.standapp.engine.control.PromptBuilder
import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.PromptType
import kotlin.test.Test
import kotlin.test.assertTrue

class PromptBuilderTest {

    private val builder = PromptBuilder()

    private val commits = listOf(
        CommitInfo("abc1234", "Alice", "alice@example.com", "2025-01-15T10:30:00Z", "Fix login bug"),
    )

    @Test
    fun summaryPromptContainsCommits() {
        val prompt = builder.buildUserPrompt(commits, PromptType.SUMMARY)
        assertTrue(prompt.contains("abc1234"))
        assertTrue(prompt.contains("## Yesterday"))
        assertTrue(prompt.contains("## Today"))
        assertTrue(prompt.contains("## Blockers"))
    }

    @Test
    fun jsonPromptContainsCommits() {
        val prompt = builder.buildUserPrompt(commits, PromptType.JSON)
        assertTrue(prompt.contains("abc1234"))
        assertTrue(prompt.contains("JSON"))
        assertTrue(prompt.contains("categories"))
    }

    @Test
    fun systemPromptIsDefault() {
        val system = builder.buildSystemPrompt()
        assertTrue(system == DefaultPrompts.SYSTEM)
    }

    @Test
    fun customSystemPrompt() {
        val custom = PromptBuilder(systemPrompt = "Custom system prompt")
        assertTrue(custom.buildSystemPrompt() == "Custom system prompt")
    }
}
