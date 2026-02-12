package de.jug_da.standapp.benchmark

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Automated scoring checks (binary pass/fail) from PRD Section 5.
 */
object Scoring {

    private val COMMIT_HASH_REGEX = Regex("[0-9a-f]{7,40}")

    /**
     * Result of all automated checks for a single LLM output.
     */
    data class AutoScoreResult(
        val jsonParseable: Boolean?,
        val jsonSchemaCompliant: Boolean?,
        val headingsPresent: Boolean?,
        val allIdsValid: Boolean,
        val noHallucinatedIds: Boolean,
    ) {
        val passCount: Int get() = listOfNotNull(
            jsonParseable, jsonSchemaCompliant, headingsPresent, allIdsValid.takeIf { true }, noHallucinatedIds.takeIf { true }
        ).count { it }

        val allPassed: Boolean get() = listOfNotNull(
            jsonParseable, jsonSchemaCompliant, headingsPresent
        ).all { it } && allIdsValid && noHallucinatedIds
    }

    // ── JSON Checks ────────────────────────────────────────────────

    /** T11: Can the output be parsed as JSON? */
    fun isJsonParseable(output: String): Boolean = try {
        Json.parseToJsonElement(output)
        true
    } catch (_: Exception) {
        false
    }

    /** T12: Does the JSON conform to the PRD output schema? */
    fun isJsonSchemaCompliant(output: String): Boolean = try {
        val obj = Json.parseToJsonElement(output).jsonObject
        val hasDate = obj.containsKey("date")
        val hasAuthor = obj.containsKey("author")
        val hasBlockers = obj["blockers"]?.jsonArray != null
        val hasCategories = obj["categories"]?.jsonArray?.all { cat ->
            val catObj = cat.jsonObject
            catObj.containsKey("name") && catObj["commits"]?.jsonArray?.all { commit ->
                val c = commit.jsonObject
                c.containsKey("id") && c.containsKey("summary")
            } ?: false
        } ?: false
        hasDate && hasAuthor && hasBlockers && hasCategories
    } catch (_: Exception) {
        false
    }

    // ── Summary Checks ─────────────────────────────────────────────

    private val REQUIRED_HEADINGS = listOf("## Yesterday", "## Today", "## Blockers")

    /** T13: Are all required headings present in summary output? */
    fun hasRequiredHeadings(output: String): Boolean =
        REQUIRED_HEADINGS.all { heading ->
            output.lines().any { it.trim().equals(heading, ignoreCase = true) }
        }

    // ── Common Checks ──────────────────────────────────────────────

    /** T14: Do all IDs referenced in output match the commit hash pattern? */
    fun allReferencedIdsValid(output: String): Boolean {
        val ids = extractIds(output)
        return ids.isEmpty() || ids.all { COMMIT_HASH_REGEX.matches(it) }
    }

    /** T15: Are there any hallucinated IDs (present in output but not in input)? */
    fun findHallucinatedIds(output: String, inputIds: Set<String>): Set<String> {
        val outputIds = extractIds(output)
        return outputIds - inputIds
    }

    /**
     * Run all applicable checks for a given prompt type.
     */
    fun score(
        output: String,
        promptType: PromptType,
        inputIds: Set<String>,
    ): AutoScoreResult {
        val hallucinatedIds = findHallucinatedIds(output, inputIds)
        return when (promptType) {
            PromptType.SUMMARY -> AutoScoreResult(
                jsonParseable = null,
                jsonSchemaCompliant = null,
                headingsPresent = hasRequiredHeadings(output),
                allIdsValid = allReferencedIdsValid(output),
                noHallucinatedIds = hallucinatedIds.isEmpty(),
            )
            PromptType.JSON -> AutoScoreResult(
                jsonParseable = isJsonParseable(output),
                jsonSchemaCompliant = isJsonSchemaCompliant(output),
                headingsPresent = null,
                allIdsValid = allReferencedIdsValid(output),
                noHallucinatedIds = hallucinatedIds.isEmpty(),
            )
        }
    }

    /**
     * Extract tokens that look like commit hashes from LLM output.
     * Looks for hex strings of 7-40 chars that appear after "ID:" labels
     * or standalone hex tokens.
     */
    private fun extractIds(output: String): Set<String> {
        // Match IDs that follow "id":" or "ID:" patterns, plus bare hex tokens
        val idFieldRegex = Regex("""(?:"id"\s*:\s*"([0-9a-f]{7,40})")|(?:ID:\s*([0-9a-f]{7,40}))""", RegexOption.IGNORE_CASE)
        return idFieldRegex.findAll(output)
            .flatMap { match -> match.groupValues.drop(1).filter { it.isNotEmpty() } }
            .toSet()
    }
}

enum class PromptType { SUMMARY, JSON }
