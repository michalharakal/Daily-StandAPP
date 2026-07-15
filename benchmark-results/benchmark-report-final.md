# Daily-StandAPP LLM Engine Benchmark — Final Report (2026-07-15)

All engines run fully local on one M-series macOS machine. 15 cases
(`bench/case-*.json`), prompt types SUMMARY and JSON, temperature 0.1,
top-p 0.9, max 512 tokens, via the shared `standupPipeline` (identical
prompt bytes across engines). Quality = `QualityScorer` structural checks
(headings/JSON schema/commit-id validity/no hallucinated ids).

## Coverage per engine

| Engine | Model | Coverage |
|---|---|---|
| REST_API (LM Studio, Metal GPU) | TinyLlama 1.1B Chat Q8_0 | full matrix, 90 runs |
| DELIVERANCE 0.0.11 (pure JVM) | TinyLlama 1.1B Chat (F32) | full matrix, 90 runs |
| SKAINET 0.36.0 (Kotlin CPU, SIMD) | Llama 3.2 1B Instruct Q8_0 | latency-only partial, 11 generations |
| QXOTIC 0.1.0 (JVM + native C kernels) | Llama 3.2 1B Instruct Q8_0 | excluded — CLI-level characterization only |

## Comparison (full-matrix engines)

| | REST_API (GPU) | DELIVERANCE (JVM) |
|---|---|---|
| Latency p50 | **4.5 s** | 31.0 s |
| Latency p95 | 41.2 s | 88.6 s |
| Throughput (median) | **303 chars/s** | 36 chars/s |
| Auto-checks pass rate | 4.8% | 0.0% |
| Determinism (pairwise Jaccard) | 0.111 | 0.075 |
| Errors | 6 (case-08 context) | 6 (case-08 context) |
| Timeouts (180 s) | 0 | 3 (case-10/JSON, 186–207 s) |

## Partial / characterized engines

**SKAINET** (aborted lane, 11 measured generations): 225–298 s per SUMMARY
generation, 333–450 s per JSON generation — every run exceeded a 180 s
budget; a 600 s-budget lane was cancelled as uninformative. Root cause is
autoregressive prefill (one forward pass per prompt token on CPU);
[SKaiNET-transformers#226](https://github.com/SKaiNET-developers/SKaiNET-transformers/issues/226)
threads the existing batched prefill (`PrefillStrategy.Batched`) through
the agent/session path to address exactly this.

**QXOTIC**: subprocess-per-generation design. CLI-level: 2.8 tok/s with
natively built `libjota_c` kernels, 0.42 tok/s on the pure-JVM fallback.
Benchmark-scale prompts (≈1–2.5 K tokens) exceeded 9–10 min per generation;
all sample-lane attempts timed out or returned empty within a 600 s budget.

## Findings

1. **Runtime, not model, dominates latency.** Same TinyLlama weights: GPU
   (Metal) p50 4.5 s vs pure-JVM 31 s (~7×), with CPU Kotlin/SIMD and
   JVM+C-kernel engines further behind on comparable prompts.
2. **Model capacity, not engine, dominates quality.** Structural contract
   adherence is <5% for 1–1.1 B models on every engine. The only clean
   sweep was case-06 (small, tidy input) on REST_API. Engine choice moved
   speed; it never moved correctness.
3. **Context budgeting is a real failure mode.** case-08 (32 commits,
   ≈2.4–2.6 K prompt tokens) failed identically on both full-matrix
   engines (2 K context). Mitigation shipped: the pipeline's
   `firstMessageLine()` preprocess step.
4. **`BENCH_TIMEOUT_MS` is advisory for in-process engines.** Blocking
   generate loops are not coroutine-cancellable; over-budget runs record
   their true latency (up to 21.6 min observed on one DELIVERANCE
   case-05/JSON run). Subprocess engines (QXOTIC) hard-timeout correctly.
5. **Determinism ≈ 0.08–0.11 at temperature 0.1** — output wording is
   effectively non-repeatable at the word level on these engines/models.
6. **Ops footnote:** every original QXOTIC failure was a default-locale
   `"%.3f"` rendering `0,100` on a de_DE machine — not the missing GPU
   backends its warnings suggested. Fixed with `Locale.ROOT`.

## Reproduce

```bash
scripts/setup-bench-engines.sh    # Deliverance + qxotic into ~/.m2
./gradlew :benchmark:jvmJar -Pdeliverance.enabled=true -Pqxotic.enabled=true
BENCH_BACKENDS=REST_API,DELIVERANCE BENCH_RUNS=3 BENCH_WARMUP=1 \
BENCH_TIMEOUT_MS=180000 BENCH_LOCAL_URL=http://localhost:1234 \
BENCH_LOCAL_MODEL=tinyllama-1.1b-chat-v1.0 \
BENCH_DELIVERANCE_MODEL=TinyLlama/TinyLlama-1.1B-Chat-v1.0 \
java --add-modules jdk.incubator.vector \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --enable-native-access=ALL-UNNAMED -Xmx16g \
     -jar benchmark/build/libs/benchmark-jvm.jar
```

Raw data: `benchmark-results/benchmark-results.csv` (per-run),
`benchmark-results/benchmark-report.md` (generated tables), lane logs
`benchmark-results-*.log`.
