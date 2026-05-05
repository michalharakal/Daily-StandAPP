package de.jug_da.standapp.benchmark.engines

import de.jug_da.standapp.llm.LLMService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Benchmark-only LLM backend that subprocess-launches qxotic's
 * `Llama32CliQ8_0` example as a generate-once-and-exit CLI
 * (https://github.com/qxoticai/qxotic).
 *
 * Why subprocess and not in-process: qxotic's runnable inference is private to
 * the example class (`LoadedModel`, `Llama32Model`, `Sampler` are
 * `private static final` nested in the CLI). The public `jota`/`gguf`/
 * `toknroll-*` libraries are too low-level to wrap directly without
 * reimplementing the example's ~3.8 KLOC of glue code. Spawning the CLI as a
 * process gets us real text generation today; the per-call cost (~5–15 s of
 * JVM + GGUF load on top of inference) is honestly accounted for in the
 * benchmark's latency and throughput numbers.
 *
 * Each `generate()` call launches one fresh JVM. There is no model-warm
 * persistence — that would require maintaining a stdin-fed daemon process,
 * which qxotic's CLI doesn't expose cleanly.
 *
 * Setup: run `scripts/setup-bench-engines.sh` first. It clones qxotic into
 * `external/qxotic`, runs `mvn install`, and writes the runtime classpath
 * to `external/qxotic-classpath.txt`. This service reads that file at
 * construction time.
 */
class QxoticLLMService private constructor(
    private val classpath: String,
    private val modelGgufPath: String,
    private val javaBinary: String,
    private val processTimeoutMs: Long,
) : LLMService {

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
    ): String = withContext(Dispatchers.IO) {
        // qxotic's CLI requires --enable-native-access for FFM and
        // --add-modules=jdk.incubator.vector for Panama kernels. These are
        // the same flags `:StandAPP-cli` already uses for its run task.
        val cmd = listOf(
            javaBinary,
            "--enable-native-access=ALL-UNNAMED",
            "--add-modules=jdk.incubator.vector",
            "-cp", classpath,
            "com.qxotic.jota.examples.llama.Llama32CliQ8_0",
            "--model", modelGgufPath,
            "--system-prompt", LLMService.SYSTEM_PROMPT,
            "--prompt", prompt,
            "--max-tokens", maxTokens.toString(),
            "--temperature", "%.3f".format(temperature),
            "--top-p", "%.3f".format(topP),
            "--seed", "42",
        )

        val process = ProcessBuilder(cmd)
            .redirectErrorStream(false)
            .start()

        val stdoutText = StringBuilder()
        val stderrText = StringBuilder()
        val stdoutReader = Thread {
            process.inputStream.bufferedReader().forEachLine { stdoutText.appendLine(it) }
        }
        val stderrReader = Thread {
            process.errorStream.bufferedReader().forEachLine { stderrText.appendLine(it) }
        }
        stdoutReader.start()
        stderrReader.start()

        val finished = process.waitFor(processTimeoutMs, TimeUnit.MILLISECONDS)
        stdoutReader.join(2_000)
        stderrReader.join(2_000)
        if (!finished) {
            process.destroyForcibly()
            throw RuntimeException("[QxoticLLMService] process timed out after ${processTimeoutMs}ms")
        }
        if (process.exitValue() != 0) {
            throw RuntimeException(
                "[QxoticLLMService] exit=${process.exitValue()}\n" +
                    "stderr (last 2000 chars):\n${stderrText.toString().takeLast(2000)}"
            )
        }

        extractModelOutput(stdoutText.toString())
    }

    /**
     * Strip qxotic's framework-level stdout (timing summaries, kernel-miss
     * counts, sampling stats) and return only the model's generated text.
     *
     * The CLI prints all of these to stdout in non-stream mode:
     *   tokens/s: 0.23 (14 tokens in 60.881s)
     *   timing[instruct]: wall=...
     *   timing_detail[instruct]: gemvPrep=... attnNorm=... ...
     *   gemv_miss[instruct]: fallback=... dtype=... ...
     *   sampling[instruct]: total=... calls=... avg=... max=...
     *   <model output via format.echo(out)>
     *
     * The model output is whatever doesn't match a timing-line shape. Joined
     * with newlines preserved so multi-line generations stay readable.
     */
    private fun extractModelOutput(stdout: String): String {
        val timingPrefix = Regex("""^(tokens/s:|timing(?:_detail)?\[|gemv_miss\[|sampling\[|benchmark |TRACE )""")
        return stdout.lineSequence()
            .filterNot { timingPrefix.containsMatchIn(it) }
            .joinToString("\n")
            .trim()
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 600_000L // 10 min — model load alone can be 15s

        /**
         * Reflectively-invoked from [BenchmarkEngineRegistry.qxoticFactory].
         * `modelSpec` is the absolute path to a Llama 3.2 Q8_0 GGUF file. If
         * the value is the literal string `embedded`, falls back to the
         * `:llm` module's already-extracted cached GGUF.
         */
        @JvmStatic
        fun create(modelSpec: String): QxoticLLMService {
            val classpathFile = File("external/qxotic-classpath.txt")
            require(classpathFile.exists()) {
                "external/qxotic-classpath.txt missing — run scripts/setup-bench-engines.sh first"
            }
            val classpath = classpathFile.readText().trim()

            val resolved = if (modelSpec == "embedded") {
                File(System.getProperty("user.home"), ".cache/standapp/models/Llama-3.2-1B-Instruct-Q8_0.gguf")
            } else {
                File(modelSpec)
            }
            require(resolved.isFile) {
                "qxotic model GGUF not found at ${resolved.absolutePath}. Either set BENCH_QXOTIC_MODEL " +
                    "to a local Llama-3.2 Q8_0 GGUF or run the daily-standapp CLI once to extract the embedded one."
            }

            val javaBinary = "${System.getProperty("java.home")}/bin/java"
            require(File(javaBinary).canExecute()) {
                "java binary not found/executable at $javaBinary"
            }

            val timeout = System.getenv("BENCH_QXOTIC_TIMEOUT_MS")?.toLongOrNull() ?: DEFAULT_TIMEOUT_MS
            return QxoticLLMService(classpath, resolved.absolutePath, javaBinary, timeout)
        }
    }
}
