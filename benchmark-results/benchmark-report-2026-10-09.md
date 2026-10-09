# Daily-StandAPP LLM Engine Benchmark — Re-run (2026-10-09)

Re-run of the July report (`benchmark-report-final.md`) against the current
engine releases, for issue #27. Fully local on one Linux laptop
(Intel i7-9750H, 6C/12T, AVX2, 31 GB RAM, OpenJDK 25.0.4). 15 cases
(`bench/case-*.json`), prompt types SUMMARY and JSON, temperature 0.1,
top-p 0.9, max 512 tokens, 1 measured run per case, no warm-up,
`BENCH_TIMEOUT_MS=600000`. Quality = `QualityScorer` structural checks.

## Engines

| Engine | Version | Model | Notes |
|---|---|---|---|
| DELIVERANCE | 0.0.16 (Maven Central) | TinyLlama 1.1B Chat v1.0, F32 / I8 | pure JVM, in-process |
| SKAINET | 0.57.0 + transformers 0.57.1 | Llama 3.2 1B Instruct Q8_0 | Kotlin + native FFM row-major kernels, in-process |

Both lanes ran sequentially on an otherwise idle machine. The models differ
(no Llama 3.2 safetensors in the Deliverance cache, no TinyLlama GGUF in
the catalog), so quality numbers compare *engine + model*, not engines alone.

## Results

| | DELIVERANCE SUMMARY | DELIVERANCE JSON | SKAINET SUMMARY | SKAINET JSON |
|---|---|---|---|---|
| Generations | 14 | 14 | 15 | 15 |
| Latency p50 | 72.9 s | 92.5 s | **45.6 s** | **80.9 s** |
| Latency p95 | 92.4 s | 111.4 s | 151.4 s | 160.2 s |
| Latency max | 97.5 s | 120.3 s | 302.6 s (case-08) | 244.1 s (case-08) |
| Throughput (median) | 13 chars/s | 14 chars/s | 10 chars/s | 12 chars/s |
| Headings present | 0/14 | — | **15/15** | — |
| JSON parseable / schema | — | 0/14 | — | **12/15** |
| All commit ids valid | 14/14 | 14/14 | 15/15 | 15/15 |
| No hallucinated ids | 12/14 | 13/14 | 15/15 | 14/15 |
| Auto-checks pass rate | 0.0 % | 0.0 % | **100 %** | **73 %** |
| Errors | 1 (case-08: prompt exceeds ntokens) | 1 (same) | 0 | 0 |
| Timeouts | 0 | 0 | 0 | 0 |

## Findings

1. **SKaiNET is now a full-matrix engine.** In July (0.36.0) the SKAINET lane
   was aborted at 225–450 s per generation; 0.57.0 with the native Q8_0
   row-major kernels finishes every case, p50 46 s (SUMMARY) / 81 s (JSON),
   including case-08 (≈ 2.5 K prompt tokens) that TinyLlama's 2 K context
   cannot take at all.
2. **Deliverance 0.0.16 runs the whole matrix without timeouts.** July had
   three case-10/JSON timeouts and a 21-minute outlier; this run's worst
   generation is 120 s. On this CPU it is ≈ 2.5× slower than on the July
   M-series machine (p50 73/93 s vs 28/36 s), as expected for F32 weights
   on AVX2.
3. **Structure follows the model, not the engine.** Llama 3.2 1B Instruct
   follows the heading / JSON contract in 26 of 30 generations; TinyLlama
   1.1B Chat in 0 of 28 — the same 0 % it scored in July on every engine.
   Commit-id fidelity is high on both (no invalid ids, 1–3 hallucinated-id
   cases each).
4. **Context budgeting** still decides case-08: a 2 K-context model fails
   it deterministically; the 8 K-context Llama 3.2 handles it at ≈ 5 min.

## Reproduce

```bash
./gradlew :benchmark:jvmJar -Pdeliverance.enabled=true   # Deliverance comes from Maven Central
JAVA_FLAGS="--add-modules jdk.incubator.vector --add-opens java.base/java.nio=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xmx16g"
export BENCH_RUNS=1 BENCH_WARMUP=0 BENCH_TIMEOUT_MS=600000
BENCH_BACKENDS=DELIVERANCE BENCH_DELIVERANCE_MODEL=TinyLlama/TinyLlama-1.1B-Chat-v1.0 \
BENCH_OUTPUT_DIR=benchmark-results/2026-10-09-deliverance-0.0.16 \
java $JAVA_FLAGS -jar benchmark/build/libs/benchmark-jvm.jar
BENCH_BACKENDS=SKAINET MCP_LLM_MODEL_PATH=~/.cache/standapp/models/Llama-3.2-1B-Instruct-Q8_0.gguf \
BENCH_OUTPUT_DIR=benchmark-results/2026-10-09-skainet-0.57.0 \
java $JAVA_FLAGS -jar benchmark/build/libs/benchmark-jvm.jar
```

Raw data: `benchmark-results/2026-10-09-*/benchmark-results.csv` (per-run)
and the generated `benchmark-report.md` next to each.
