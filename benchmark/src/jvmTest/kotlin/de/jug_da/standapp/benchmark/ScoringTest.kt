package de.jug_da.standapp.benchmark

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class ScoringTest {

    // ── JSON Parseability (T11) ─────────────────────────────────────

    @Test
    fun `valid JSON is parseable`() {
        val json = """{"date":"2025-01-15","author":"Alice","categories":[],"blockers":[]}"""
        assertTrue(Scoring.isJsonParseable(json))
    }

    @Test
    fun `invalid JSON is not parseable`() {
        assertFalse(Scoring.isJsonParseable("This is not JSON at all"))
        assertFalse(Scoring.isJsonParseable("{broken"))
    }

    // ── JSON Schema Compliance (T12) ────────────────────────────────

    @Test
    fun `compliant JSON passes schema check`() {
        val json = """
        {
          "date": "2025-01-15",
          "author": "Alice",
          "categories": [
            {
              "name": "Features",
              "commits": [
                {"id": "abc1234", "summary": "Add login"}
              ]
            }
          ],
          "blockers": []
        }
        """.trimIndent()
        assertTrue(Scoring.isJsonSchemaCompliant(json))
    }

    @Test
    fun `JSON missing required fields fails schema check`() {
        val missingAuthor = """{"date":"2025-01-15","categories":[],"blockers":[]}"""
        assertFalse(Scoring.isJsonSchemaCompliant(missingAuthor))

        val missingCategories = """{"date":"2025-01-15","author":"Alice","blockers":[]}"""
        assertFalse(Scoring.isJsonSchemaCompliant(missingCategories))
    }

    @Test
    fun `JSON with malformed categories fails schema check`() {
        val noCommitsInCategory = """
        {"date":"2025-01-15","author":"Alice","categories":[{"name":"Fixes"}],"blockers":[]}
        """.trimIndent()
        assertFalse(Scoring.isJsonSchemaCompliant(noCommitsInCategory))
    }

    // ── Heading Presence (T13) ──────────────────────────────────────

    @Test
    fun `output with all headings passes`() {
        val output = """
            ## Yesterday
            Worked on feature X.
            ## Today
            Continue feature X.
            ## Blockers
            None
        """.trimIndent()
        assertTrue(Scoring.hasRequiredHeadings(output))
    }

    @Test
    fun `output missing a heading fails`() {
        val output = """
            ## Yesterday
            Worked on feature X.
            ## Blockers
            None
        """.trimIndent()
        assertFalse(Scoring.hasRequiredHeadings(output))
    }

    // ── Commit Hash Validation (T14) ────────────────────────────────

    @Test
    fun `valid commit hashes pass`() {
        val output = """ID: abc1234 and ID: def5678"""
        assertTrue(Scoring.allReferencedIdsValid(output))
    }

    // ── Hallucination Detection (T15) ────────────────────────────────

    @Test
    fun `no hallucinated IDs when all match input`() {
        val output = """{"id": "abc1234", "summary": "test"}"""
        val inputIds = setOf("abc1234", "def5678")
        val hallucinated = Scoring.findHallucinatedIds(output, inputIds)
        assertTrue(hallucinated.isEmpty())
    }

    @Test
    fun `detects hallucinated IDs`() {
        val output = """{"id": "abc1234", "summary": "test"}, {"id": "bad9999", "summary": "fake"}"""
        val inputIds = setOf("abc1234")
        val hallucinated = Scoring.findHallucinatedIds(output, inputIds)
        assertEquals(setOf("bad9999"), hallucinated)
    }

    // ── Full Score (combined) ───────────────────────────────────────

    @Test
    fun `summary scoring runs all applicable checks`() {
        val output = """
            ## Yesterday
            Completed ID: abc1234 login feature.
            ## Today
            Continue with ID: def5678.
            ## Blockers
            None
        """.trimIndent()
        val result = Scoring.score(output, PromptType.SUMMARY, setOf("abc1234", "def5678"))
        assertTrue(result.headingsPresent!!)
        assertTrue(result.allIdsValid)
        assertTrue(result.noHallucinatedIds)
        assertTrue(result.allPassed)
    }

    @Test
    fun `json scoring runs all applicable checks`() {
        val output = """
        {
          "date": "2025-01-15",
          "author": "Alice",
          "categories": [{"name": "Features", "commits": [{"id": "abc1234", "summary": "Add login"}]}],
          "blockers": []
        }
        """.trimIndent()
        val result = Scoring.score(output, PromptType.JSON, setOf("abc1234"))
        assertTrue(result.jsonParseable!!)
        assertTrue(result.jsonSchemaCompliant!!)
        assertTrue(result.allIdsValid)
        assertTrue(result.noHallucinatedIds)
        assertTrue(result.allPassed)
    }
}
