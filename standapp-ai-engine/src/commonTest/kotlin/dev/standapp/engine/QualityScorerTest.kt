package dev.standapp.engine

import dev.standapp.engine.control.QualityScorer
import dev.standapp.engine.entity.PromptType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QualityScorerTest {

    // T11: JSON parseable
    @Test
    fun isJsonParseableValid() {
        assertTrue(QualityScorer.isJsonParseable("""{"key":"value"}"""))
    }

    @Test
    fun isJsonParseableInvalid() {
        assertFalse(QualityScorer.isJsonParseable("not json"))
    }

    // T12: JSON schema compliant
    @Test
    fun isJsonSchemaCompliantValid() {
        val json = """{"date":"2025-01-15","author":"Alice","categories":[{"name":"Fixes","commits":[{"id":"abc1234","summary":"fix"}]}],"blockers":[]}"""
        assertTrue(QualityScorer.isJsonSchemaCompliant(json))
    }

    @Test
    fun isJsonSchemaCompliantMissingFields() {
        assertFalse(QualityScorer.isJsonSchemaCompliant("""{"date":"2025-01-15"}"""))
    }

    // T13: Required headings
    @Test
    fun hasRequiredHeadingsAllPresent() {
        val output = "## Yesterday\nDid stuff\n## Today\nWill do stuff\n## Blockers\nNone"
        assertTrue(QualityScorer.hasRequiredHeadings(output))
    }

    @Test
    fun hasRequiredHeadingsMissing() {
        val output = "## Yesterday\nDid stuff\n## Today\nWill do stuff"
        assertFalse(QualityScorer.hasRequiredHeadings(output))
    }

    // T14: All IDs valid
    @Test
    fun allReferencedIdsValidTrue() {
        val output = """ID: abc1234\nID: def5678"""
        assertTrue(QualityScorer.allReferencedIdsValid(output))
    }

    // T15: Hallucination detection
    @Test
    fun findHallucinatedIdsNone() {
        val output = """{"id":"abc1234"}"""
        val found = QualityScorer.findHallucinatedIds(output, setOf("abc1234"))
        assertTrue(found.isEmpty())
    }

    @Test
    fun findHallucinatedIdsDetected() {
        val output = """{"id":"abc1234"} {"id":"def9999"}"""
        val found = QualityScorer.findHallucinatedIds(output, setOf("abc1234"))
        assertEquals(setOf("def9999"), found)
    }

    // Full score - summary
    @Test
    fun scoreSummaryMode() {
        val output = "## Yesterday\nFixed bug (abc1234)\n## Today\nContinue\n## Blockers\nNone"
        val result = QualityScorer.score(output, PromptType.SUMMARY, setOf("abc1234"))
        assertNull(result.jsonParseable)
        assertNull(result.jsonSchemaCompliant)
        assertTrue(result.headingsPresent == true)
        assertTrue(result.allIdsValid)
        assertTrue(result.noHallucinatedIds)
    }

    // Full score - JSON
    @Test
    fun scoreJsonMode() {
        val json = """{"date":"2025-01-15","author":"Alice","categories":[{"name":"Fixes","commits":[{"id":"abc1234","summary":"fix"}]}],"blockers":[]}"""
        val result = QualityScorer.score(json, PromptType.JSON, setOf("abc1234"))
        assertTrue(result.jsonParseable == true)
        assertTrue(result.jsonSchemaCompliant == true)
        assertNull(result.headingsPresent)
        assertTrue(result.allIdsValid)
        assertTrue(result.noHallucinatedIds)
    }
}
