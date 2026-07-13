# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Daily-StandAPP is a Kotlin Multiplatform application that generates daily standup summaries from Git commit history using local LLM inference. Built for a JavaLand 2026 talk by JUG Darmstadt. Privacy-first: all data processing happens locally.

## Build Commands

Requires **JDK 21+**. Uses Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`).

```bash
./gradlew assemble                    # Build all modules
./gradlew :mcp-server:jvmJar          # Build MCP server JAR
./gradlew :benchmark:jvmJar           # Build benchmark fat JAR
./gradlew test                        # Run all tests
./gradlew :benchmark:jvmTest          # Run benchmark unit tests only
./gradlew :data:jvmTest               # Run data module tests only
```

The benchmark JAR requires `--add-modules jdk.incubator.vector` at runtime:
```bash
java --add-modules jdk.incubator.vector -jar benchmark/build/libs/benchmark-jvm.jar
```

## Module Architecture

```
Daily-StandAPP
├── data          # Git repository access (JGit on JVM, expect/actual pattern)
├── domain        # Shared domain models (GitInfo, etc.)
├── llm           # LLM inference abstraction with pluggable backends
│                   Backends: SKaiNET (kllama), REST_API (OpenAI-compatible)
│                   Key: LLMService interface, LLMServiceFactory, LLMSummarizer
├── standapp-ai-engine  # Pure prompt/parse/score logic (dev.standapp.engine.*)
│                   PromptBuilder, CommitFormatter, QualityScorer, StandupSummary
├── mcp-server    # MCP server exposing Git tools via JSON-RPC (stdio transport)
│                   Tools: get_commits_by_author, get_all_commits
├── benchmark     # LLM backend evaluation (15 test cases in bench/ directory)
│                   Optional engines via -Pdeliverance.enabled / -Pqxotic.enabled
├── StandAPP-cli  # CLI entry point (Kotlin/JVM, self-contained shadow JAR)
└── cloud-api/    # OpenAI-compatible REST API
    ├── model     # Shared DTOs (@Serializable, @SerialName for JSON mapping)
    ├── server    # Ktor server (/v1/chat/completions, /v1/models)
    ├── client    # HTTP client for the API
    └── agent     # Koog AIAgent framework integration
```

**Data flow**: Git repo → `data` module (JGit) → `GitInfo` models → `llm` module (summarization) → MCP server / CLI / REST API

## Key Patterns

- **Kotlin Multiplatform expect/actual**: Common interfaces in `commonMain`, platform implementations in `jvmMain`/`wasmJsMain`/`nativeMain`
- **LLM backend selection**: Factory pattern via `LLMServiceFactory`. Backend chosen by `MCP_LLM_BACKEND` env var (SKAINET, REST_API)
- **Serialization**: kotlinx.serialization with `@Serializable` and `@SerialName` annotations for OpenAI-compatible JSON
- **Package prefix**: `de.jug_da.*`

## Environment Variables

| Variable | Purpose |
|----------|---------|
| `MCP_LLM_BACKEND` | Backend selection: SKAINET, REST_API |
| `MCP_LLM_MODEL_PATH` | Path to GGUF model file (SKaiNET) |
| `MCP_LLM_REST_BASE_URL` | REST endpoint URL |
| `MCP_LLM_REST_MODEL` | Model name for REST endpoint |
| `BENCH_*` | Benchmark configuration (see README.md for full list) |

## Key Dependencies

- **Kotlin** 2.4.0, **Ktor** 3.5.1, **Kotlinx** Coroutines/Serialization/DateTime/IO
- **SKaiNET** 0.36.0 (Kotlin-native LLM inference, GGUF models; BOM-managed)
- **Eclipse JGit** 7.7.0 (Git repository access)
- **MCP Kotlin SDK** 0.8.3 (Model Context Protocol)
- **Koog** 1.0.0 (cloud-api:agent only), **Deliverance** 0.0.11-SNAPSHOT (benchmark-only, mavenLocal)
- **LLM parameters**: temperature=0.1, topP=0.9, maxTokens=512 (SKAINET clamps temperature to >= 0.6)
