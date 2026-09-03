package de.jug_da.app.cli

import de.jug_da.standapp.llm.LLMBackendType
import de.jug_da.standapp.llm.LLMService
import kotlinx.datetime.LocalDate

enum class OutputFormat { MD, JSON, TEXT }

/** How the commit list is obtained. */
enum class CommitsMode { QWEN, GIT }

/** Bad command line — message goes to stderr, process exits with code 2. */
class CliUsageException(message: String) : Exception(message)

data class CliArgs(
    val repoDir: String = ".",
    val author: String? = null,
    val days: Int = 1,
    /** Explicit window start; wins over [days] when set. */
    val since: LocalDate? = null,
    /** Explicit window end (inclusive); requires [since]. Defaults to today. */
    val until: LocalDate? = null,
    val format: OutputFormat = OutputFormat.MD,
    /** Overrides MCP_LLM_BACKEND when set. */
    val backend: LLMBackendType? = null,
    /** Local GGUF for the Qwen3 tool-calling stage (overrides STANDAPP_QWEN_MODEL_PATH). */
    val qwenModelPath: String? = null,
    /** Local GGUF for the Llama 3.2 summariser (overrides STANDAPP_LLAMA_MODEL_PATH / MCP_LLM_MODEL_PATH). */
    val llamaModelPath: String? = null,
    /** Qwen tool call (default) or direct git access for the commit list. */
    val commits: CommitsMode = CommitsMode.QWEN,
    /** Summariser preset: `3b` (default, Llama-3.2-3B Q4_K_M) or `1b` (Llama-3.2-1B Q8_0, ~2–3× faster). */
    val summaryModel: String = "3b",
    /** Keep both models resident instead of releasing each after its stage. */
    val keepModels: Boolean = false,
    /** Write the rendered summary to this file instead of stdout. */
    val output: String? = null,
    /** Print a quality report to stderr; failed checks exit with code 5. */
    val score: Boolean = false,
    val maxTokens: Int = LLMService.DEFAULT_MAX_TOKENS,
    val temperature: Float = LLMService.DEFAULT_TEMPERATURE,
    val help: Boolean = false,
) {
    companion object {

        fun parse(args: Array<String>): CliArgs {
            var result = CliArgs()
            val iter = args.iterator()

            fun value(flag: String): String {
                if (!iter.hasNext()) throw CliUsageException("missing value for $flag")
                return iter.next()
            }

            fun intValue(flag: String): Int {
                val raw = value(flag)
                return raw.toIntOrNull()?.takeIf { it > 0 }
                    ?: throw CliUsageException("$flag expects a positive integer, got '$raw'")
            }

            fun dateValue(flag: String): LocalDate {
                val raw = value(flag)
                return try {
                    LocalDate.parse(raw)
                } catch (e: Exception) {
                    throw CliUsageException("$flag expects a date as YYYY-MM-DD, got '$raw'")
                }
            }

            while (iter.hasNext()) {
                when (val arg = iter.next()) {
                    "--repo", "-r" -> result = result.copy(repoDir = value(arg))
                    "--author", "-a" -> result = result.copy(author = value(arg))
                    "--days", "-d" -> result = result.copy(days = intValue(arg))
                    "--since" -> result = result.copy(since = dateValue(arg))
                    "--until" -> result = result.copy(until = dateValue(arg))
                    "--format", "-f" -> {
                        val raw = value(arg)
                        val format = OutputFormat.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                            ?: throw CliUsageException("--format expects md|json|text, got '$raw'")
                        result = result.copy(format = format)
                    }
                    "--backend", "-b" -> {
                        val raw = value(arg)
                        val backend = try {
                            LLMBackendType.parse(raw)
                        } catch (e: Exception) {
                            throw CliUsageException(e.message ?: "invalid --backend '$raw'")
                        }
                        result = result.copy(backend = backend)
                    }
                    "--model", "-m", "--llama-model" -> result = result.copy(llamaModelPath = value(arg))
                    "--qwen-model" -> result = result.copy(qwenModelPath = value(arg))
                    "--commits" -> {
                        val raw = value(arg)
                        val mode = CommitsMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                            ?: throw CliUsageException("--commits expects qwen|git, got '$raw'")
                        result = result.copy(commits = mode)
                    }
                    "--summary-model" -> {
                        val raw = value(arg)
                        if (raw.lowercase() !in setOf("3b", "1b")) throw CliUsageException("--summary-model expects 3b|1b, got '$raw'")
                        result = result.copy(summaryModel = raw.lowercase())
                    }
                    "--keep-models" -> result = result.copy(keepModels = true)
                    "--output", "-o" -> result = result.copy(output = value(arg))
                    "--score" -> result = result.copy(score = true)
                    "--max-tokens" -> result = result.copy(maxTokens = intValue(arg))
                    "--temperature" -> {
                        val raw = value(arg)
                        val temp = raw.toFloatOrNull()?.takeIf { it >= 0f }
                            ?: throw CliUsageException("--temperature expects a non-negative number, got '$raw'")
                        result = result.copy(temperature = temp)
                    }
                    "--help", "-h" -> result = result.copy(help = true)
                    else -> {
                        if (arg.startsWith("-")) throw CliUsageException("unknown option '$arg'")
                        // First positional argument is the repo dir.
                        result = result.copy(repoDir = arg)
                    }
                }
            }

            if (result.until != null && result.since == null) {
                throw CliUsageException("--until requires --since")
            }
            if (result.since != null && result.until != null && result.until < result.since) {
                throw CliUsageException("--until must not be before --since")
            }
            return result
        }
    }
}
