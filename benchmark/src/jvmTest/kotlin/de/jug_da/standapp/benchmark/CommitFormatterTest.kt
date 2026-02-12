package de.jug_da.standapp.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals

class CommitFormatterTest {

    @Test
    fun `formats single commit in production format`() {
        val commits = listOf(
            CommitEntry(
                id = "abc1234",
                authorName = "Alice",
                authorEmail = "alice@example.com",
                whenDate = "2025-01-15T10:30:00Z",
                message = "Fix login bug",
            )
        )
        val expected = "ID: abc1234\nAuthor: Alice <alice@example.com>\nDate: 2025-01-15T10:30:00Z\nMessage: Fix login bug\n---"
        assertEquals(expected, CommitFormatter.format(commits))
    }

    @Test
    fun `formats multiple commits joined by newline`() {
        val commits = listOf(
            CommitEntry("aaa1111", "Alice", "a@x.com", "2025-01-15T10:00:00Z", "First"),
            CommitEntry("bbb2222", "Bob", "b@x.com", "2025-01-15T11:00:00Z", "Second"),
        )
        val result = CommitFormatter.format(commits)
        assertEquals(2, result.split("---").filter { it.isNotBlank() }.size)
        assert(result.contains("ID: aaa1111"))
        assert(result.contains("ID: bbb2222"))
    }

    @Test
    fun `empty commit list returns empty string`() {
        assertEquals("", CommitFormatter.format(emptyList()))
    }
}
