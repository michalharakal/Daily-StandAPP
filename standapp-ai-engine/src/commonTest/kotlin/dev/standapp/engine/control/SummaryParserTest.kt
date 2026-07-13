package dev.standapp.engine.control

import dev.standapp.engine.entity.PromptType
import dev.standapp.engine.entity.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SummaryParserTest {

    private val markdown = """
        ## Yesterday
        - Fixed CI pipeline (a001b02)
        - ID: c003d04 pinned JVM toolchain

        ## Today
        - Continue work on ktor migration

        ## Blockers
        - None
    """.trimIndent()

    @Test
    fun markdown_happy_path_produces_three_sections() {
        val summary = SummaryParser.parseMarkdown(markdown, date = "2025-09-22", author = "Greta")
        assertEquals(listOf("Yesterday", "Today", "Blockers"), summary.sections.map { it.name })
        assertEquals("2025-09-22", summary.date)
        assertEquals("Greta", summary.author)

        val yesterday = summary.sections[0]
        assertEquals(2, yesterday.items.size)
        assertEquals("a001b02", yesterday.items[0].commitId)
        assertEquals(Status.DONE, yesterday.items[0].status)
        assertEquals("c003d04", yesterday.items[1].commitId)

        assertEquals(Status.IN_PROGRESS, summary.sections[1].items.single().status)
        assertNull(summary.sections[1].items.single().commitId)
        assertEquals(Status.UNKNOWN, summary.sections[2].items.single().status)
    }

    @Test
    fun markdown_missing_heading_throws() {
        val noBlockers = markdown.substringBefore("## Blockers")
        assertFailsWith<SummaryParseException> {
            SummaryParser.parseMarkdown(noBlockers, date = "", author = "")
        }
    }

    @Test
    fun lenient_parse_falls_back_to_raw() {
        val garbage = "the model rambled with no headings at all"
        val summary = SummaryParser.parse(garbage, PromptType.SUMMARY, date = "d", author = "a", strict = false)
        assertEquals(garbage, summary.raw)
        assertTrue(summary.sections.isEmpty())
        assertEquals("d", summary.date)
    }

    @Test
    fun strict_parse_propagates_exception() {
        assertFailsWith<SummaryParseException> {
            SummaryParser.parse("no headings", PromptType.SUMMARY, strict = true)
        }
    }

    private val json = """
        {
          "date": "2025-09-22",
          "author": "Greta Fischer",
          "categories": [
            {
              "name": "Bug Fixes",
              "commits": [
                {"id": "a001b02", "summary": "Fix broken CI pipeline", "status": "done"},
                {"id": "c003d04", "summary": "Pin JVM toolchain", "status": "in-progress"}
              ]
            }
          ],
          "blockers": ["Waiting on upstream release"]
        }
    """.trimIndent()

    @Test
    fun json_happy_path() {
        val summary = SummaryParser.parseJson(json)
        assertEquals("2025-09-22", summary.date)
        assertEquals("Greta Fischer", summary.author)
        assertEquals(2, summary.sections.size) // Bug Fixes + Blockers

        val bugFixes = summary.sections[0]
        assertEquals("Bug Fixes", bugFixes.name)
        assertEquals(Status.DONE, bugFixes.items[0].status)
        assertEquals(Status.IN_PROGRESS, bugFixes.items[1].status)
        assertEquals("a001b02", bugFixes.items[0].commitId)

        assertEquals("Blockers", summary.sections[1].name)
        assertEquals("Waiting on upstream release", summary.sections[1].items.single().text)
    }

    @Test
    fun json_wrapped_in_markdown_fences_still_parses() {
        val fenced = "```json\n$json\n```"
        val summary = SummaryParser.parseJson(fenced)
        assertEquals("2025-09-22", summary.date)
    }

    @Test
    fun json_with_prose_prefix_still_parses() {
        val chatty = "Here is your standup data:\n$json\nHope this helps!"
        val summary = SummaryParser.parseJson(chatty)
        assertEquals("Greta Fischer", summary.author)
    }

    @Test
    fun malformed_json_throws() {
        assertFailsWith<SummaryParseException> {
            SummaryParser.parseJson("{ definitely not json ]")
        }
    }

    @Test
    fun schema_violating_json_throws() {
        assertFailsWith<SummaryParseException> {
            SummaryParser.parseJson("""{"date": "2025-09-22"}""")
        }
    }

    @Test
    fun empty_blockers_array_produces_no_blockers_section() {
        val noBlockers = json.replace("""["Waiting on upstream release"]""", "[]")
        val summary = SummaryParser.parseJson(noBlockers)
        assertEquals(listOf("Bug Fixes"), summary.sections.map { it.name })
    }
}
