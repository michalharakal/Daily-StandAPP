package dev.standapp.engine.control

import dev.standapp.engine.entity.PromptType
import dev.standapp.engine.entity.StandupSummary
import dev.standapp.engine.entity.SummaryItem
import dev.standapp.engine.entity.SummarySection
import dev.standapp.engine.entity.Status
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Raised when LLM output cannot be parsed into a [StandupSummary] in strict mode. */
class SummaryParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses raw LLM output into the structured [StandupSummary] model.
 *
 * Counterpart to the two [DefaultPrompts] templates: [parseMarkdown] expects
 * the `## Yesterday` / `## Today` / `## Blockers` layout demanded by
 * `SUMMARY_USER`, [parseJson] expects the object shape demanded by
 * `JSON_USER` (the same shape `QualityScorer.isJsonSchemaCompliant`
 * validates).
 */
object SummaryParser {

    private val HEADING = Regex("""^##\s+(.+?)\s*$""")
    private val COMMIT_ID = Regex("""\b[0-9a-f]{7,40}\b""")
    private val REQUIRED_SECTIONS = listOf("Yesterday", "Today", "Blockers")

    /**
     * Parse [raw] according to [promptType]. With [strict] = true a
     * malformed output raises [SummaryParseException]; otherwise the raw
     * text is preserved in a sectionless fallback summary.
     */
    fun parse(
        raw: String,
        promptType: PromptType,
        date: String = "",
        author: String = "",
        strict: Boolean = false,
    ): StandupSummary = try {
        when (promptType) {
            PromptType.SUMMARY -> parseMarkdown(raw, date, author)
            PromptType.JSON -> parseJson(raw)
        }
    } catch (e: SummaryParseException) {
        if (strict) throw e
        StandupSummary(raw = raw, date = date, author = author, sections = emptyList(), promptType = promptType)
    }

    /** Parse the three-heading markdown layout. Throws [SummaryParseException] when a required heading is missing. */
    fun parseMarkdown(raw: String, date: String, author: String): StandupSummary {
        val sections = mutableListOf<SummarySection>()
        var currentName: String? = null
        val items = mutableListOf<SummaryItem>()

        fun flush() {
            currentName?.let { sections.add(SummarySection(it, items.toList())) }
            items.clear()
        }

        for (line in raw.lines()) {
            val heading = HEADING.find(line.trim())
            if (heading != null) {
                flush()
                currentName = heading.groupValues[1]
                continue
            }
            val name = currentName ?: continue // preamble before the first heading
            val text = line.trim().removePrefix("- ").removePrefix("* ").trim()
            if (text.isEmpty()) continue
            items.add(
                SummaryItem(
                    commitId = COMMIT_ID.find(text)?.value,
                    text = text,
                    status = statusFor(name),
                )
            )
        }
        flush()

        val missing = REQUIRED_SECTIONS.filter { required ->
            sections.none { it.name.equals(required, ignoreCase = true) }
        }
        if (missing.isNotEmpty()) {
            throw SummaryParseException("missing required heading(s): ${missing.joinToString()}")
        }
        return StandupSummary(raw = raw, date = date, author = author, sections = sections, promptType = PromptType.SUMMARY)
    }

    /** Parse the JSON layout. Throws [SummaryParseException] on unparseable or schema-violating output. */
    fun parseJson(raw: String): StandupSummary {
        val jsonText = extractJsonObjectText(raw)
            ?: throw SummaryParseException("no JSON object found in output")
        val obj = try {
            Json.parseToJsonElement(jsonText) as? JsonObject
                ?: throw SummaryParseException("top-level JSON is not an object")
        } catch (e: SummaryParseException) {
            throw e
        } catch (e: Exception) {
            throw SummaryParseException("output is not valid JSON", e)
        }

        val date = obj.stringField("date")
        val author = obj.stringField("author")
        val categories = obj["categories"]?.jsonArray
            ?: throw SummaryParseException("missing 'categories' array")
        val blockers = obj["blockers"]?.jsonArray
            ?: throw SummaryParseException("missing 'blockers' array")

        val sections = categories.map { category ->
            val cat = category as? JsonObject
                ?: throw SummaryParseException("category is not an object")
            val name = cat.stringField("name")
            val commits = cat["commits"]?.jsonArray
                ?: throw SummaryParseException("category '$name' missing 'commits' array")
            SummarySection(
                name = name,
                items = commits.map { commit ->
                    val c = commit as? JsonObject
                        ?: throw SummaryParseException("commit entry is not an object")
                    SummaryItem(
                        commitId = c.stringField("id"),
                        text = c.stringField("summary"),
                        status = when (c["status"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                            "done" -> Status.DONE
                            "in-progress" -> Status.IN_PROGRESS
                            else -> Status.UNKNOWN
                        },
                    )
                },
            )
        }.toMutableList()

        val blockerItems = blockers.mapNotNull { it.jsonPrimitive.contentOrNull }
            .filter { it.isNotBlank() }
            .map { SummaryItem(text = it, status = Status.UNKNOWN) }
        if (blockerItems.isNotEmpty()) {
            sections.add(SummarySection("Blockers", blockerItems))
        }

        return StandupSummary(raw = raw, date = date, author = author, sections = sections, promptType = PromptType.JSON)
    }

    private fun statusFor(sectionName: String): Status = when {
        sectionName.equals("Yesterday", ignoreCase = true) -> Status.DONE
        sectionName.equals("Today", ignoreCase = true) -> Status.IN_PROGRESS
        else -> Status.UNKNOWN
    }

    /**
     * Small models wrap JSON in markdown fences or prose despite
     * instructions — slice from the first `{` to the last `}`.
     */
    private fun extractJsonObjectText(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    private fun JsonObject.stringField(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull
            ?: throw SummaryParseException("missing '$name' field")
}
