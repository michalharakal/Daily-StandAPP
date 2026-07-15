# Benchmark Results

Cases: 15
Runs per case: 3

## Comparison Table

| Backend | Faithfulness (avg) | Completeness (avg) | Structure | Auto-checks pass% | Latency p50 | Latency p95 | Throughput | Determinism |
|---------|--------------------|---------------------|-----------|--------------------|-------------|-------------|------------|-------------|
| REST_API (local) | 0,00 | 0,00 | 0,00 | 4,8% | 4506ms | 41206ms | 302,9 c/s | 0,111 |
| DELIVERANCE | 0,00 | 0,00 | 0,00 | 0,0% | 30975ms | 88648ms | 36,2 c/s | 0,075 |

## Pass/Fail Thresholds

### REST_API (local)
- [FAIL] Faithfulness: 0,00 (threshold: 1,50)
- [FAIL] Structure (auto pass rate): 0,05 (threshold: 0,90)
- [PASS] Latency p50 (ms): 4506,00 (threshold: 8000,00)

### DELIVERANCE
- [FAIL] Faithfulness: 0,00 (threshold: 1,50)
- [FAIL] Structure (auto pass rate): 0,00 (threshold: 0,90)
- [FAIL] Latency p50 (ms): 30975,00 (threshold: 8000,00)

