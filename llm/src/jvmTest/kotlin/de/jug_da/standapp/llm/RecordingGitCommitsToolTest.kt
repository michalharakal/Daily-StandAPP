package de.jug_da.standapp.llm

import de.jug_da.data.git.GitInfo
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecordingGitCommitsToolTest {

    private val now = Instant.parse("2026-09-02T12:00:00Z")
    private val commit = GitInfo("abc1234def", "Greta", "g@x", Instant.parse("2026-09-01T12:00:00Z"), "Fix it")

    @Test
    fun records_commits_for_integer_days() {
        var window: Pair<Instant, Instant>? = null
        val tool = RecordingGitCommitsTool("/repo", { _, _, start, end -> window = start to end; listOf(commit) }, { now }, log = {})
        val reply = tool.execute(buildJsonObject { put("days", 7); put("author", "") })

        assertTrue(reply.startsWith("ok: 1 commit(s)"), reply)
        assertEquals(listOf(commit), tool.recorded)
        assertEquals(7, tool.recordedDays)
        assertNull(tool.recordedAuthor)
        assertEquals(Instant.parse("2026-08-26T12:00:00Z") to now, window)
    }

    @Test
    fun accepts_days_as_string_and_author_filter() {
        var seenAuthor: String? = null
        val tool = RecordingGitCommitsTool("/repo", { _, author, _, _ -> seenAuthor = author; emptyList() }, { now }, log = {})
        val reply = tool.execute(buildJsonObject { put("days", JsonPrimitive("3")); put("author", "Greta") })

        assertTrue(reply.startsWith("ok: 0 commit(s)"), reply)
        assertEquals("Greta", seenAuthor)
        assertEquals(3, tool.recordedDays)
        assertNotNull(tool.recorded)
    }

    @Test
    fun rejects_missing_or_non_positive_days_without_recording() {
        val tool = RecordingGitCommitsTool("/repo", { _, _, _, _ -> listOf(commit) }, { now }, log = {})
        assertTrue(tool.execute(buildJsonObject { }).startsWith("error:"))
        assertTrue(tool.execute(buildJsonObject { put("days", 0) }).startsWith("error:"))
        assertNull(tool.recorded)
    }

    @Test
    fun source_failure_is_captured_not_swallowed() {
        val tool = RecordingGitCommitsTool("/repo", { _, _, _, _ -> throw IllegalStateException("no repo") }, { now }, log = {})
        val reply = tool.execute(buildJsonObject { put("days", 1) })
        assertTrue(reply.startsWith("error: no repo"))
        assertNotNull(tool.failure)
        assertNull(tool.recorded)
    }
}

class RecordingGitCommitsToolRepeatTest {
    @Test
    fun repeated_identical_call_reuses_the_recorded_result() {
        var fetches = 0
        val now = Instant.parse("2026-09-02T12:00:00Z")
        val tool = RecordingGitCommitsTool("/repo", { _, _, _, _ -> fetches++; emptyList() }, { now }, log = {})
        val first = tool.execute(buildJsonObject { put("days", 30) })
        val second = tool.execute(buildJsonObject { put("days", 30) })
        assertEquals(first, second)
        assertEquals(1, fetches)
        // A different window is a new lookup.
        tool.execute(buildJsonObject { put("days", 7) })
        assertEquals(2, fetches)
    }
}
