package de.jug_da.standapp.benchmark

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A single benchmark test case loaded from `bench/case-XX.json`.
 */
@Serializable
data class BenchmarkCase(
    val id: String,
    val description: String,
    val commits: List<CommitEntry>,
    val expectations: Expectations,
)

@Serializable
data class CommitEntry(
    val id: String,
    val authorName: String,
    val authorEmail: String,
    val whenDate: String,
    val message: String,
)

@Serializable
data class Expectations(
    val summary: SummaryExpectations = SummaryExpectations(),
    val json: JsonExpectations = JsonExpectations(),
)

@Serializable
data class SummaryExpectations(
    val requiredHeadings: List<String> = listOf("## Yesterday", "## Today", "## Blockers"),
    val mustMentionIds: List<String> = emptyList(),
    val forbiddenIds: List<String> = emptyList(),
    val notes: String = "",
)

@Serializable
data class JsonExpectations(
    val mustParseAsJson: Boolean = true,
    val expectedCategories: List<String> = emptyList(),
    val expectedCommitCount: Int = -1,
    val notes: String = "",
)

/**
 * Loads benchmark cases from a directory of JSON files.
 */
object BenchmarkCaseLoader {

    private val json = Json { ignoreUnknownKeys = true }

    fun loadAll(benchDir: File): List<BenchmarkCase> {
        require(benchDir.isDirectory) { "Bench directory does not exist: ${benchDir.absolutePath}" }
        return benchDir.listFiles { f -> f.extension == "json" && f.name.startsWith("case-") }
            ?.sortedBy { it.name }
            ?.map { load(it) }
            ?: emptyList()
    }

    fun load(file: File): BenchmarkCase {
        return json.decodeFromString<BenchmarkCase>(file.readText())
    }
}
