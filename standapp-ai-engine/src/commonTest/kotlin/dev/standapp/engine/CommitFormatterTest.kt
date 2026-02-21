package dev.standapp.engine

import dev.standapp.engine.control.CommitFormatter
import dev.standapp.engine.entity.CommitInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommitFormatterTest {

    @Test
    fun formatSingleCommit() {
        val commits = listOf(
            CommitInfo("abc1234", "Alice", "alice@example.com", "2025-01-15T10:30:00Z", "Fix login bug")
        )
        val result = CommitFormatter.format(commits)
        assertTrue(result.contains("ID: abc1234"))
        assertTrue(result.contains("Author: Alice <alice@example.com>"))
        assertTrue(result.contains("Date: 2025-01-15T10:30:00Z"))
        assertTrue(result.contains("Message: Fix login bug"))
        assertTrue(result.contains("---"))
    }

    @Test
    fun formatMultipleCommits() {
        val commits = listOf(
            CommitInfo("abc1234", "Alice", "alice@example.com", "2025-01-15T10:30:00Z", "Fix login bug"),
            CommitInfo("def5678", "Bob", "bob@example.com", "2025-01-15T11:00:00Z", "Add tests"),
        )
        val result = CommitFormatter.format(commits)
        assertTrue(result.contains("ID: abc1234"))
        assertTrue(result.contains("ID: def5678"))
        assertEquals(2, result.split("---").size - 1) // two separators... but actually split count
    }

    @Test
    fun formatEmptyList() {
        val result = CommitFormatter.format(emptyList())
        assertEquals("", result)
    }
}
