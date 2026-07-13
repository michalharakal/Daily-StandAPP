package de.jug_da.app.cli

import de.jug_da.standapp.llm.LLMBackendType
import de.jug_da.standapp.llm.LLMService
import kotlinx.datetime.LocalDate

enum class OutputFormat { MD, JSON, TEXT }

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
    /** Overrides MCP_LLM_MODEL_PATH when set (SKAINET only). */
    val modelPath: String? = null,
    /** Write the rendered summary to this file instead of stdout. */
    val output: String? = null,
    /** Print a quality report to stderr; failed checks exit with code 5. */
    val score: Boolean = false,
    val maxTokens: Int = LLMService.DEFAULT_MAX_TOKENS,
    val temperature: Float = LLMService.DEFAULT_TEMPERATURE,
    /** Drive the model through the tool-calling agent loop (SKAINET only). */
    val toolCalling: Boolean = false,
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
                    "--model", "-m" -> result = result.copy(modelPath = value(arg))
                    "--output", "-o" -> result = result.copy(output = value(arg))
                    "--score" -> result = result.copy(score = true)
                    "--max-tokens" -> result = result.copy(maxTokens = intValue(arg))
                    "--temperature" -> {
                        val raw = value(arg)
                        val temp = raw.toFloatOrNull()?.takeIf { it >= 0f }
                            ?: throw CliUsageException("--temperature expects a non-negative number, got '$raw'")
                        result = result.copy(temperature = temp)
                    }
                    "--tool-calling" -> result = result.copy(toolCalling = true)
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
