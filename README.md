# dAily-StandAPP

[![JavaLand 2026 Banner](https://www.javaland.eu/fileadmin/Event/JavaLand/Banner/2026/JL_26-Banner-512x256px_Speaker.jpg)](https://meine.doag.org/events/javaland/2026/agenda/#agendaId.7511)

## Standapp CLI

Generate a daily standup summary from a git repo with two local models, wired
together as a SKaiNET data pipeline (`de.jug_da.standapp.llm.pipeline.standupFlow`):

```
StandupRequest → qwen-commits → require-commits → preprocess → summarize[prompt → llama → parse] → result
```

1. **Qwen3 0.6B** (`Qwen3-0.6B-Q8_0.gguf`) reads the request and calls the
   `get_recent_commits` tool. The tool records the structured commit list;
   Qwen's prose is never used. If the model does not produce a valid call the
   stage falls back to direct git access and says so on stderr.
2. **Llama 3.2 3B Instruct** (`Llama-3.2-3B-Instruct-Q4_K_M.gguf`) writes the
   summary from the engine's prompt templates; the output is parsed, scored and
   retried on invalid JSON.

Both GGUFs are downloaded on first run (~0.6 GB + ~2.0 GB) through SKaiNET's
`hf://` data-source resolver into `~/.cache/standapp/models` and reused afterwards.
Requires JDK 21+ to run Gradle; compilation uses a JDK 25 toolchain (auto-provisioned).

```bash
# Default: markdown summary, streamed tokens, Qwen tool call + Llama summary.
./gradlew :StandAPP-cli:run --args="--repo /path/to/repo --days 7"

# Structured JSON (parsed + validated, retries once on invalid output),
# quality report on stderr, summary written to a file:
./gradlew :StandAPP-cli:run --args="--repo . --days 7 --format json -o standup.json --score"

# Explicit date window (direct git access, no tool call), plain-text output:
./gradlew :StandAPP-cli:run --args="--repo . --since 2026-07-01 --until 2026-07-11 --format text"

# Self-contained fat JAR (~20 MB; models still come from the cache):
./gradlew :StandAPP-cli:shadowJar
java --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Xmx12g \
  -jar StandAPP-cli/build/libs/standapp-1.0-SNAPSHOT-all.jar --repo /path/to/repo --days 7 --score
```

Key flags: `--format md|json|text`, `--since/--until` (win over `--days`),
`--backend skainet|rest_api`, `--commits qwen|git`, `--qwen-model <gguf>`,
`--llama-model <gguf>` (alias `--model`), `--keep-models`, `--output <file>`,
`--score`, `--max-tokens`, `--temperature`. Run `--help` for the full list.
Exit codes: `0` ok, `2` usage, `3` git error/no commits, `4` LLM/model error,
`5` failed quality checks or unparseable JSON. Machine-readable output stays
clean: status and `[STAGE/...]` logs go to stderr.

### Models and environment

| Variable | Purpose |
|----------|---------|
| `STANDAPP_QWEN_MODEL_PATH` | Local GGUF for the tool-calling stage (skips the download) |
| `STANDAPP_LLAMA_MODEL_PATH` | Local GGUF for the summariser (`MCP_LLM_MODEL_PATH` is honoured as a legacy alias) |
| `STANDAPP_MODEL_CACHE_DIR` | Download cache, default `~/.cache/standapp/models` |
| `STANDAPP_OFFLINE=1` | Never touch the network; fail fast when the cache is cold |
| `HF_TOKEN` | Optional Hugging Face token for the download |
| `MCP_LLM_BACKEND=REST_API` | Use an OpenAI-compatible endpoint for the summary instead (`MCP_LLM_REST_BASE_URL`, `MCP_LLM_REST_MODEL`) |
| `STANDAPP_TINY_PROMPT=1` | Send a one-line prompt to the summariser only (download + inference smoke test) |

Either override may also be an `hf://org/repo@rev/file.gguf` URI, resolved through the same cache.

### Extending the pipeline

Every step is a `sk.ainet.data.source.PipelineStage`, so the flow is glue you can
extend from Kotlin without touching the built-ins:

```kotlin
val flow = standupFlow(models) {
    commits = qwenToolCall(tools = listOf(GetOpenIssuesTool(repo)))   // extra tools for Qwen
    preprocess { shortIds(7); firstMessageLine(120); limit(60) }
    transform("drop-merges") { b -> b.copy(commits = b.commits.filterNot { it.message.startsWith("Merge") }) }
    prompt { type = PromptType.JSON }
    summarize(llama())                                                // or llmService(RestApiLLMService(...))
    postprocess { parseSummary(); score(); retryOnInvalid(2) }
    finish("archive") { r -> File("last.md").writeText(r.raw); r }
}
println(flow.describe())
val result = flow.execute(StandupRequest(repoDir = ".", days = 7))
```

Stage logs (`[STAGE/QWEN RENDERED PROMPT]`, `[STAGE/TOOL CALLS]`,
`[STAGE/FALLBACK]`, `[STAGE/PREFILL DONE]`, `[STAGE/FINAL ANSWER]`, …) go to
stderr — redirect with `2>stages.log` to inspect what each model saw and produced.

## MCP Server

### Step 1: Build the MCP Server

```bash
# Build the MCP server module
./gradlew :mcp-server:jvmJar

# Verify the JAR was created
ls -la mcp-server/build/libs/mcp-server-jvm.jar
```

Expected output: You should see the `mcp-server-jvm.jar` file with a recent timestamp.

### Step 2: Start the MCP Server

```bash
# Start the server on localhost:8080
java -jar mcp-server/build/libs/mcp-server-jvm.jar --host localhost --port 8080
```

```bash
$ java -jar mcp-server/build/libs/mcp-server-jvm.jar --host localhost --port 8080
[main] INFO io.modelcontextprotocol.kotlin.sdk.server.Server - Registering tool: get_commits_by_author
[main] INFO io.modelcontextprotocol.kotlin.sdk.server.Server - Registering tool: get_all_commits
```

## Running the Benchmark

The `:benchmark` module evaluates local LLM backends for standup summary generation. It runs 15 test cases (covering normal, edge, and stress scenarios) against each backend, collecting quality scores and operational metrics.

### Prerequisites

- JDK 25+
- At least one backend available:
  - **LM Studio** or **Ollama** running locally or on a remote machine, or
  - **SKAINET** (Llama 3.2 3B Q4_K_M, downloaded on first use or pointed at via `MCP_LLM_MODEL_PATH`)
- Optional benchmark-only alternative engines:
  - **Deliverance** — pure-Java JVM inference. Requires `./scripts/setup-bench-engines.sh` (clones the repo + `mvn install` into `~/.m2`), then build with `-Pdeliverance.enabled=true`. In-process; auto-downloads HuggingFace models.
  - **qxotic** — JVM-native LLM toolkit. Same setup script, then build with `-Pqxotic.enabled=true`. Subprocess-launched (the runnable inference lives in qxotic's `examples` module's `Llama32CliQ8_0` CLI; we shell out to it once per generate call). Slower than in-process — model is reloaded per call — but proves the abstraction holds across very different engine designs.

### Unit Tests

Run the benchmark scoring, metrics, and formatting unit tests (no backend required):

```bash
./gradlew :benchmark:jvmTest
```

### Running the Benchmark

#### 1. Build the fat JAR

```bash
./gradlew :benchmark:jvmJar
```

The JAR is written to `benchmark/build/libs/benchmark-jvm.jar`.

#### 2. Run against a local LLM server

The benchmark connects to any **OpenAI-compatible** `/v1/chat/completions` endpoint (LM Studio, Ollama, llama.cpp server, vLLM, etc.).

**LM Studio** (default port 1234):

```bash
BENCH_BACKENDS=REST_API \
BENCH_LOCAL_URL=http://localhost:1234 \
BENCH_LOCAL_MODEL=tinyllama-1.1b-chat-v1.0 \
BENCH_RUNS=3 \
java --add-modules jdk.incubator.vector -jar benchmark/build/libs/benchmark-jvm.jar
```

**Ollama** (default port 11434):

```bash
BENCH_BACKENDS=REST_API \
BENCH_LOCAL_URL=http://localhost:11434 \
BENCH_LOCAL_MODEL=llama3.2:3b \
BENCH_RUNS=3 \
java --add-modules jdk.incubator.vector -jar benchmark/build/libs/benchmark-jvm.jar
```

**Remote machine** (e.g., LM Studio on another computer):

```bash
BENCH_BACKENDS=REST_API \
BENCH_LOCAL_URL=http://192.168.1.100:1234 \
BENCH_LOCAL_MODEL=tinyllama-1.1b-chat-v1.0 \
java --add-modules jdk.incubator.vector -jar benchmark/build/libs/benchmark-jvm.jar
```

#### 3. Run with a cloud baseline

Add a cloud endpoint for quality comparison:

```bash
BENCH_BACKENDS=REST_API \
BENCH_LOCAL_URL=http://localhost:1234 \
BENCH_LOCAL_MODEL=tinyllama-1.1b-chat-v1.0 \
BENCH_CLOUD_URL=https://api.openai.com/v1 \
BENCH_CLOUD_MODEL=gpt-4o-mini \
BENCH_CLOUD_API_KEY=$OPENAI_API_KEY \
java --add-modules jdk.incubator.vector -jar benchmark/build/libs/benchmark-jvm.jar
```

#### 4. Run all backends

```bash
STANDAPP_LLAMA_MODEL_PATH=/path/to/Llama-3.2-3B-Instruct-Q4_K_M.gguf \
BENCH_LOCAL_URL=http://localhost:1234 \
BENCH_LOCAL_MODEL=tinyllama-1.1b-chat-v1.0 \
BENCH_CLOUD_URL=https://api.openai.com/v1 \
BENCH_CLOUD_API_KEY=$OPENAI_API_KEY \
java --add-modules jdk.incubator.vector -jar benchmark/build/libs/benchmark-jvm.jar
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BENCH_DIR` | `./bench` | Directory containing `case-XX.json` test files |
| `BENCH_BACKENDS` | all | Comma-separated list: `SKAINET`, `REST_API` |
| `BENCH_RUNS` | `5` | Number of measured runs per case (for determinism scoring) |
| `BENCH_WARMUP` | `0` | Discarded warm-up runs before measurement (cold-start JIT/class-load bias) |
| `BENCH_CASES` | all | Comma-separated case IDs, e.g. `case-01,case-08` |
| `BENCH_PROMPTS` | both | Comma-separated prompt types: `SUMMARY`, `JSON` |
| `BENCH_LOCAL_URL` | `http://localhost:1234` | Local REST endpoint URL (LM Studio, Ollama, etc.) |
| `BENCH_LOCAL_MODEL` | `tinyllama-1.1b-chat-v1.0` | Model name for the local endpoint |
| `BENCH_LOCAL_API_KEY` | _(none)_ | Optional Bearer token for local REST endpoint |
| `BENCH_CLOUD_URL` | _(none)_ | OpenAI-compatible endpoint URL for cloud baseline |
| `BENCH_CLOUD_MODEL` | `gpt-4o-mini` | Model name for the cloud endpoint |
| `BENCH_CLOUD_API_KEY` | `OPENAI_API_KEY` | Optional cloud Bearer token (falls back to `OPENAI_API_KEY`) |
| `BENCH_OUTPUT_DIR` | `./benchmark-results` | Where reports are written |
| `MCP_LLM_MODEL_PATH` | _(download)_ | Local GGUF for the SKAINET summariser (Llama 3.2 3B); downloaded into the model cache when unset |

Example focused run (single case, single prompt, single run):

```bash
BENCH_BACKENDS=REST_API \
BENCH_LOCAL_URL=http://localhost:1234 \
BENCH_LOCAL_MODEL=tinyllama-1.1b-chat-v1.0 \
BENCH_CASES=case-01 \
BENCH_PROMPTS=SUMMARY \
BENCH_RUNS=1 \
java --add-modules jdk.incubator.vector -jar benchmark/build/libs/benchmark-jvm.jar
```

### Output

After a run, find these in `benchmark-results/`:

- **`benchmark-report.md`** — Markdown comparison table with pass/fail thresholds and cloud-vs-local delta analysis
- **`benchmark-results.csv`** — Per-case, per-backend, per-run raw data for further analysis

### Test Cases

The `bench/` directory contains 15 cases:

| Case | Tests |
|------|-------|
| 01-02 | Normal multi-commit days |
| 03 | Empty input (zero commits) |
| 04 | Single commit |
| 05 | Conflicting/contradictory messages |
| 06 | Ambiguous issue references |
| 07 | Noisy messages (typos, mixed languages, emoji) |
| 08 | Large volume (30+ commits) |
| 09 | Multiple authors |
| 10 | Merge-heavy history |
| 11 | Pure refactoring (no user-facing changes) |
| 12 | CI/config only |
| 13 | Mixed English/German messages |
| 14 | Long multi-paragraph messages |
| 15 | Minimal terse messages ("fix", "wip") |

## Model Selection Best Practices

### What the benchmark measures

Each backend is scored on two axes:

1. **Quality** — automated checks (JSON parseability, schema compliance, heading presence, hallucination detection) plus a human rubric (faithfulness, completeness, structure, actionability, clarity — 0-2 each, 10 total)
2. **Performance** — latency (p50/p95), throughput (chars/sec), determinism, memory/CPU usage, and stability (timeouts/crashes)

### Decision framework

Pick the model that passes quality thresholds while meeting your operational constraints:

| Priority | Choose | When |
|----------|--------|------|
| Offline/privacy first | SKAINET | No network dependency, data stays on device |
| Lowest latency | SKAINET | Kotlin-native inference, no HTTP overhead |
| Easiest setup | REST_API (Ollama) | Single `ollama pull` command, broad model catalog |
| Best quality ceiling | REST_API (cloud) | Acceptable latency/cost trade-off, internet available |

### Quality thresholds ("good enough" for workshop use)

These are the minimum bars from the scoring rubric:

- **Faithfulness >= 1.5/2 average** — the model must not hallucinate commits or details
- **Structure compliance near-perfect** — JSON must parse, headings must be present (auto-checks fail on <90% of cases = FAIL)
- **Latency < 8s median** for summary generation on laptop hardware (WARN at 8s, FAIL at 15s)

### Practical tips for local models

- **Prefer structured prompts** (JSON output, explicit headings). Small models perform significantly better with constrained output formats than open-ended generation.
- **Keep context short.** Summarize or group commits by issue before feeding to the LLM. Dumping 200 raw commits degrades quality on all local models.
- **Use low temperature** (0.1) for reproducible output. The benchmark uses `temperature=0.1, topP=0.9, maxTokens=512` as fixed parameters.
- **"If unknown, say unknown"** — include this instruction in prompts. Local models are more prone to filling gaps with plausible-sounding but invented details.
- **Run the determinism check.** If the same input produces wildly different outputs across 5 runs (determinism score < 0.5), lower the temperature further or switch models.
- **3B parameter models** (e.g., `llama3.2:3b`) are a good starting point for standup summaries. They run fast on CPU and handle the structured output well. Scale up to 7B+ only if quality thresholds aren't met.
- **Compare against the cloud baseline** to calibrate expectations. Local models trade some language polish for latency, cost, and privacy — the benchmark report quantifies exactly how much.
