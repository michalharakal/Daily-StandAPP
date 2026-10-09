# Benchmark Results

Cases: 15
Runs per case: 1

## Comparison Table

| Backend | Faithfulness (avg) | Completeness (avg) | Structure | Auto-checks pass% | Latency p50 | Latency p95 | Throughput | Determinism |
|---------|--------------------|---------------------|-----------|--------------------|-------------|-------------|------------|-------------|
| SKAINET | 0.00 | 0.00 | 0.00 | 86.7% | 60543ms | 124192ms | 10.9 c/s | 0.104 |

## Pass/Fail Thresholds

### SKAINET
- [FAIL] Faithfulness: 0.00 (threshold: 1.50)
- [FAIL] Structure (auto pass rate): 0.87 (threshold: 0.90)
- [FAIL] Latency p50 (ms): 60543.00 (threshold: 8000.00)

