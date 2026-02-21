package dev.standapp.engine.control

import dev.standapp.engine.entity.PromptType
import dev.standapp.engine.entity.QualityScores
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

object QualityScorer {

    private val COMMIT_HASH_REGEX = Regex("[0-9a-f]{7,40}")
    private val REQUIRED_HEADINGS = listOf("## Yesterday", "## Today", "## Blockers")

    fun score(output: String, promptType: PromptType, inputIds: Set<String>): QualityScores {
        val hallucinatedIds = findHallucinatedIds(output, inputIds)

        val jsonParseable: Boolean?
        val jsonSchemaCompliant: Boolean?
        val headingsPresent: Boolean?

        when (promptType) {
            PromptType.SUMMARY -> {
                jsonParseable = null
                jsonSchemaCompliant = null
                headingsPresent = hasRequiredHeadings(output)
            }
            PromptType.JSON -> {
                jsonParseable = isJsonParseable(output)
                jsonSchemaCompliant = isJsonSchemaCompliant(output)
                headingsPresent = null
            }
        }

        val allIdsValid = allReferencedIdsValid(output)
        val noHallucinated = hallucinatedIds.isEmpty()

        val checks = listOfNotNull(jsonParseable, jsonSchemaCompliant, headingsPresent) +
            listOf(allIdsValid, noHallucinated)

        return QualityScores(
            jsonParseable = jsonParseable,
            jsonSchemaCompliant = jsonSchemaCompliant,
            headingsPresent = headingsPresent,
            allIdsValid = allIdsValid,
            noHallucinatedIds = noHallucinated,
            passCount = checks.count { it },
            totalChecks = checks.size,
        )
    }

    fun isJsonParseable(output: String): Boolean = try {
        Json.parseToJsonElement(output)
        true
    } catch (_: Exception) {
        false
    }

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

    fun hasRequiredHeadings(output: String): Boolean =
        REQUIRED_HEADINGS.all { heading ->
            output.lines().any { it.trim().equals(heading, ignoreCase = true) }
        }

    fun allReferencedIdsValid(output: String): Boolean {
        val ids = extractIds(output)
        return ids.isEmpty() || ids.all { COMMIT_HASH_REGEX.matches(it) }
    }

    fun findHallucinatedIds(output: String, inputIds: Set<String>): Set<String> {
        val outputIds = extractIds(output)
        return outputIds - inputIds
    }

    private fun extractIds(output: String): Set<String> {
        val idFieldRegex = Regex(
            """(?:"id"\s*:\s*"([0-9a-f]{7,40})")|(?:ID:\s*([0-9a-f]{7,40}))""",
            RegexOption.IGNORE_CASE,
        )
        return idFieldRegex.findAll(output)
            .flatMap { match -> match.groupValues.drop(1).filter { it.isNotEmpty() } }
            .toSet()
    }
}
