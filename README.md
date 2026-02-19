# dAily-StandAPP

[![JavaLand 2026 Banner](https://www.javaland.eu/fileadmin/Event/JavaLand/Banner/2026/JL_26-Banner-512x256px_Speaker.jpg)](https://meine.doag.org/events/javaland/2026/agenda/#agendaId.7511)

## Running the Benchmark

The `:benchmark` module evaluates local LLM backends for standup summary generation. It runs 15 test cases (covering normal, edge, and stress scenarios) against each backend, collecting quality scores and operational metrics.

### Prerequisites

- JDK 21+
- At least one backend available:
  - **LM Studio** or **Ollama** running locally or on a remote machine, or
  - **SKAINET** with a GGUF model file on disk, or
  - **JLama** (downloads model automatically on first run)

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
MCP_LLM_MODEL_PATH=/path/to/model.gguf \
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
| `BENCH_BACKENDS` | all | Comma-separated list: `SKAINET`, `JLAMA`, `REST_API` |
| `BENCH_RUNS` | `5` | Number of repeated runs per case (for determinism scoring) |
| `BENCH_CASES` | all | Comma-separated case IDs, e.g. `case-01,case-08` |
| `BENCH_PROMPTS` | both | Comma-separated prompt types: `SUMMARY`, `JSON` |
| `BENCH_LOCAL_URL` | `http://localhost:1234` | Local REST endpoint URL (LM Studio, Ollama, etc.) |
| `BENCH_LOCAL_MODEL` | `tinyllama-1.1b-chat-v1.0` | Model name for the local endpoint |
| `BENCH_LOCAL_API_KEY` | _(none)_ | Optional Bearer token for local REST endpoint |
| `BENCH_CLOUD_URL` | _(none)_ | OpenAI-compatible endpoint URL for cloud baseline |
| `BENCH_CLOUD_MODEL` | `gpt-4o-mini` | Model name for the cloud endpoint |
| `BENCH_CLOUD_API_KEY` | `OPENAI_API_KEY` | Optional cloud Bearer token (falls back to `OPENAI_API_KEY`) |
| `BENCH_OUTPUT_DIR` | `./benchmark-results` | Where reports are written |
| `MCP_LLM_MODEL_PATH` | _(none)_ | Path to GGUF model file (required for SKAINET) |

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
| Offline/privacy first | SKAINET or JLama | No network dependency, data stays on device |
| Lowest latency | SKAINET (KLlama) | Kotlin-native inference, no HTTP overhead |
| Easiest setup | REST_API (Ollama) | Single `ollama pull` command, broad model catalog |
| Best quality ceiling | REST_API (cloud) | Acceptable latency/cost trade-off, internet available |
| Pure Java / no native libs | JLama | Environments where JNI/native binaries are restricted |

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
