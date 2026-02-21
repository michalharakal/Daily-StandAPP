package dev.standapp.engine

import dev.standapp.engine.control.OutputParser
import dev.standapp.engine.entity.PromptType
import dev.standapp.engine.entity.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutputParserTest {

    @Test
    fun parseSummaryWithHeadings() {
        val raw = """
            ## Yesterday
            - Fixed login bug (abc1234)
            - Updated tests

            ## Today
            - Continue work on authentication

            ## Blockers
            - None
        """.trimIndent()

        val result = OutputParser.parse(raw, PromptType.SUMMARY)
        assertEquals(PromptType.SUMMARY, result.promptType)
        assertEquals(3, result.sections.size)
        assertEquals("Yesterday", result.sections[0].name)
        assertEquals("Today", result.sections[1].name)
        assertEquals("Blockers", result.sections[2].name)
        assertEquals(2, result.sections[0].items.size)
    }

    @Test
    fun parseJsonOutput() {
        val raw = """
            {"date":"2025-01-15","author":"Alice","categories":[{"name":"Bug Fixes","commits":[{"id":"abc1234","summary":"Fixed login bug","status":"done"}]}],"blockers":[]}
        """.trimIndent()

        val result = OutputParser.parse(raw, PromptType.JSON)
        assertEquals(PromptType.JSON, result.promptType)
        assertEquals("2025-01-15", result.date)
        assertEquals("Alice", result.author)
        assertEquals(1, result.sections.size)
        assertEquals("Bug Fixes", result.sections[0].name)
        assertEquals("abc1234", result.sections[0].items[0].commitId)
        assertEquals(Status.DONE, result.sections[0].items[0].status)
    }

    @Test
    fun parseInvalidJsonReturnsEmptySummary() {
        val result = OutputParser.parse("not json at all", PromptType.JSON)
        assertEquals(PromptType.JSON, result.promptType)
        assertTrue(result.sections.isEmpty())
    }

    @Test
    fun parseJsonWithBlockers() {
        val raw = """
            {"date":"2025-01-15","author":"Alice","categories":[],"blockers":["Waiting on API key"]}
        """.trimIndent()

        val result = OutputParser.parse(raw, PromptType.JSON)
        val blockerSection = result.sections.find { it.name == "Blockers" }
        assertTrue(blockerSection != null)
        assertEquals("Waiting on API key", blockerSection.items[0].text)
    }
}
