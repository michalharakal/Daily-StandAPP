package dev.standapp.engine.control

import dev.standapp.engine.entity.PromptType
import dev.standapp.engine.entity.StandupSummary
import dev.standapp.engine.entity.SummaryItem
import dev.standapp.engine.entity.SummarySection
import dev.standapp.engine.entity.Status
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object OutputParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, promptType: PromptType): StandupSummary =
        when (promptType) {
            PromptType.SUMMARY -> parseSummary(raw)
            PromptType.JSON -> parseJson(raw)
        }

    private fun parseSummary(raw: String): StandupSummary {
        val sections = mutableListOf<SummarySection>()
        var currentHeading: String? = null
        var currentItems = mutableListOf<SummaryItem>()

        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("## ")) {
                if (currentHeading != null) {
                    sections.add(SummarySection(currentHeading, currentItems.toList()))
                }
                currentHeading = trimmed.removePrefix("## ").trim()
                currentItems = mutableListOf()
            } else if (currentHeading != null && trimmed.isNotEmpty()) {
                val text = trimmed.removePrefix("- ").removePrefix("* ").trim()
                if (text.isNotEmpty()) {
                    currentItems.add(SummaryItem(text = text))
                }
            }
        }
        if (currentHeading != null) {
            sections.add(SummarySection(currentHeading, currentItems.toList()))
        }

        return StandupSummary(
            raw = raw,
            date = "",
            author = "",
            sections = sections,
            promptType = PromptType.SUMMARY,
        )
    }

    private fun parseJson(raw: String): StandupSummary {
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            val date = obj["date"]?.jsonPrimitive?.content ?: ""
            val author = obj["author"]?.jsonPrimitive?.content ?: ""

            val sections = obj["categories"]?.jsonArray?.map { cat ->
                val catObj = cat.jsonObject
                val name = catObj["name"]?.jsonPrimitive?.content ?: ""
                val commits = catObj["commits"]?.jsonArray?.map { commit ->
                    val c = commit.jsonObject
                    SummaryItem(
                        commitId = c["id"]?.jsonPrimitive?.content,
                        text = c["summary"]?.jsonPrimitive?.content ?: "",
                        status = parseStatus(c["status"]?.jsonPrimitive?.content),
                    )
                } ?: emptyList()
                SummarySection(name, commits)
            } ?: emptyList()

            val blockers = obj["blockers"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val allSections = if (blockers.isNotEmpty()) {
                sections + SummarySection("Blockers", blockers.map { SummaryItem(text = it) })
            } else {
                sections
            }

            StandupSummary(
                raw = raw,
                date = date,
                author = author,
                sections = allSections,
                promptType = PromptType.JSON,
            )
        } catch (_: Exception) {
            StandupSummary(
                raw = raw,
                date = "",
                author = "",
                sections = emptyList(),
                promptType = PromptType.JSON,
            )
        }
    }

    private fun parseStatus(value: String?): Status = when (value?.trim()?.lowercase()) {
        "done" -> Status.DONE
        "in-progress", "in_progress" -> Status.IN_PROGRESS
        else -> Status.UNKNOWN
    }
}
