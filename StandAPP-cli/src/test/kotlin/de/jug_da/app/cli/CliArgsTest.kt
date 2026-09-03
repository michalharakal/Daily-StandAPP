package de.jug_da.app.cli

import de.jug_da.standapp.llm.LLMBackendType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CliArgsTest {

    @Test
    fun defaults() {
        val args = CliArgs.parse(emptyArray())
        assertEquals(".", args.repoDir)
        assertNull(args.author)
        assertEquals(1, args.days)
        assertEquals(OutputFormat.MD, args.format)
        assertNull(args.backend)
        assertEquals(512, args.maxTokens)
        assertTrue(!args.score && !args.keepModels && !args.help)
        assertEquals(CommitsMode.QWEN, args.commits)
        assertNull(args.qwenModelPath)
        assertNull(args.llamaModelPath)
    }

    @Test
    fun all_flags_parse() {
        val args = CliArgs.parse(
            arrayOf(
                "--repo", "/tmp/repo", "--author", "Greta", "--days", "7",
                "--since", "2026-07-01", "--until", "2026-07-11",
                "--format", "json", "--backend", "rest_api", "--model", "/models/x.gguf",
                "--output", "out.json", "--score", "--max-tokens", "1024",
                "--temperature", "0.7", "--commits", "git", "--qwen-model", "/models/q.gguf", "--keep-models",
            )
        )
        assertEquals("/tmp/repo", args.repoDir)
        assertEquals("Greta", args.author)
        assertEquals(7, args.days)
        assertEquals(LocalDate(2026, 7, 1), args.since)
        assertEquals(LocalDate(2026, 7, 11), args.until)
        assertEquals(OutputFormat.JSON, args.format)
        assertEquals(LLMBackendType.REST_API, args.backend)
        assertEquals("/models/x.gguf", args.llamaModelPath)
        assertEquals("/models/q.gguf", args.qwenModelPath)
        assertEquals("out.json", args.output)
        assertTrue(args.score)
        assertEquals(1024, args.maxTokens)
        assertEquals(0.7f, args.temperature)
        assertEquals(CommitsMode.GIT, args.commits)
        assertTrue(args.keepModels)
    }

    @Test
    fun short_flags_and_positional_repo() {
        val args = CliArgs.parse(arrayOf("-a", "Greta", "-d", "3", "-f", "TEXT", "/some/repo"))
        assertEquals("/some/repo", args.repoDir)
        assertEquals(OutputFormat.TEXT, args.format)
        assertEquals(3, args.days)
    }

    @Test
    fun unknown_flag_is_usage_error() {
        assertFailsWith<CliUsageException> { CliArgs.parse(arrayOf("--bogus-flag")) }
    }

    @Test
    fun missing_value_is_usage_error() {
        assertFailsWith<CliUsageException> { CliArgs.parse(arrayOf("--repo")) }
    }

    @Test
    fun bad_number_is_usage_error() {
        assertFailsWith<CliUsageException> { CliArgs.parse(arrayOf("--days", "many")) }
        assertFailsWith<CliUsageException> { CliArgs.parse(arrayOf("--days", "0")) }
        assertFailsWith<CliUsageException> { CliArgs.parse(arrayOf("--temperature", "hot")) }
    }

    @Test
    fun bad_date_is_usage_error() {
        assertFailsWith<CliUsageException> { CliArgs.parse(arrayOf("--since", "last tuesday")) }
    }

    @Test
    fun until_without_since_is_usage_error() {
        assertFailsWith<CliUsageException> { CliArgs.parse(arrayOf("--until", "2026-07-11")) }
    }

    @Test
    fun until_before_since_is_usage_error() {
        assertFailsWith<CliUsageException> {
            CliArgs.parse(arrayOf("--since", "2026-07-11", "--until", "2026-07-01"))
        }
    }

    @Test
    fun bad_backend_is_usage_error() {
        assertFailsWith<CliUsageException> { CliArgs.parse(arrayOf("--backend", "gpt9")) }
    }

    @Test
    fun llama_model_alias_flags() {
        assertEquals("/m.gguf", CliArgs.parse(arrayOf("--llama-model", "/m.gguf")).llamaModelPath)
        assertEquals("/m.gguf", CliArgs.parse(arrayOf("-m", "/m.gguf")).llamaModelPath)
    }

    @Test
    fun summary_model_preset() {
        assertEquals("3b", CliArgs.parse(emptyArray()).summaryModel)
        assertEquals("1b", CliArgs.parse(arrayOf("--summary-model", "1B")).summaryModel)
        assertFailsWith<CliUsageException> { CliArgs.parse(arrayOf("--summary-model", "7b")) }
    }

    @Test
    fun bad_commits_mode_is_usage_error() {
        assertFailsWith<CliUsageException> { CliArgs.parse(arrayOf("--commits", "magic")) }
    }

    @Test
    fun bad_format_is_usage_error() {
        assertFailsWith<CliUsageException> { CliArgs.parse(arrayOf("--format", "yaml")) }
    }
}
