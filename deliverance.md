# Deliverance Benchmark Integration

**Related: Issue #27**

## What is Deliverance?

[Deliverance](https://github.com/teknek/deliverance) is a Java-native LLM inference engine that runs transformer models directly on the JVM. It provides an OpenAI-compatible REST API (`POST /chat/completions`) via a Spring Boot web module.

This integration benchmarks Deliverance against the existing backends (SKAINET, REST_API) for standup summary generation.

## Prerequisites

- **jenv** — manages multiple JDK versions
- **Java 25** — required by Deliverance (`/usr/lib/jvm/java-25-openjdk-amd64`)
- **Java 21** — required by Daily-StandAPP
- **Maven** — builds Deliverance (`mvn --version`)

Register Java 25 in jenv:

```bash
jenv add /usr/lib/jvm/java-25-openjdk-amd64
jenv versions   # verify "25" appears
```

## Build & Install to Local Maven

```bash
cd deliverance/
jenv local 25
JAVA_HOME=$(jenv prefix) mvn install -DskipTests=true -Dmaven.test.skip=true -Dgpg.skip=true -pl \!vibrant-maven-plugin

# Verify
ls ~/.m2/repository/io/teknek/deliverance/core/0.0.4-SNAPSHOT/
```

## Config.java Fix for Local Use

The `web/src/main/java/net/deliverance/http/Config.java` static initializer has been updated to only load the native SIMD library when `deliverance.tensor.operations.type=simd` (the default). For local development, use `jvector` mode which doesn't require the native lib:

```bash
java -Ddeliverance.tensor.operations.type=jvector ...
```

## Running the Benchmark

### E2E Script (recommended)

```bash
bash scripts/run-deliverance-benchmark.sh
```

The script:
1. Builds Deliverance with Java 25
2. Starts the Deliverance server on port 8080
3. Runs the benchmark suite with Java 21
4. Outputs results to `benchmark-results/`

### Manual

Start Deliverance:

```bash
cd deliverance/
jenv local 25
JAVA_HOME=$(jenv prefix) java \
  -XX:-UseCompactObjectHeaders \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-modules jdk.incubator.vector \
  -Ddeliverance.tensor.operations.type=jvector \
  -Dserver.port=8080 -Xmx3G \
  -jar web/target/web-0.0.4-SNAPSHOT.jar
```

Run benchmark:

```bash
cd Daily-StandAPP/
jenv local 21
BENCH_BACKENDS=DELIVERANCE \
BENCH_DELIVERANCE_URL=http://localhost:8080 \
BENCH_DELIVERANCE_MODEL=TinyLlama-1.1B-Chat-v1.0-Jlama-Q4 \
BENCH_RUNS=3 \
./gradlew :benchmark:jvmRun
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BENCH_DELIVERANCE_URL` | `http://localhost:8080` | Deliverance server URL |
| `BENCH_DELIVERANCE_MODEL` | `TinyLlama-1.1B-Chat-v1.0-Jlama-Q4` | Model name |
| `BENCH_BACKENDS` | all | Set to `DELIVERANCE` to run only Deliverance |
| `BENCH_RUNS` | `5` | Runs per test case |
| `BENCH_CASES` | all | Comma-separated case IDs to run |
| `BENCH_PROMPTS` | `SUMMARY,JSON` | Prompt types to test |
| `PORT` | `8080` | Deliverance server port (E2E script) |
| `MODEL` | `TinyLlama-1.1B-Chat-v1.0-Jlama-Q4` | Model name (E2E script) |

## Model Selection

Default: `TinyLlama-1.1B-Chat-v1.0-Jlama-Q4` — small and fast for benchmarking.

Deliverance supports any model available through its `ModelFetcher`. Configure via `BENCH_DELIVERANCE_MODEL`.

## Architecture

```
Daily-StandAPP (Java 21, Gradle)          Deliverance (Java 25, Maven)
┌──────────────────────────────┐           ┌───────────────────────────┐
│  :benchmark module           │    HTTP   │  web module (Spring Boot) │
│  BenchmarkRunner             │ ───────>  │  POST /chat/completions   │
│    → RestApiLLMService       │  :8080    │    → Generator.generate   │
└──────────────────────────────┘           └───────────────────────────┘
```

The integration uses Deliverance's OpenAI-compatible REST API to avoid JVM version conflicts (Java 25 vs Java 21).
