# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Daily-StandAPP is a Kotlin Multiplatform application that generates daily standup summaries from Git commit history using local LLM inference. Built for a JavaLand 2026 talk by JUG Darmstadt. Privacy-first: all data processing happens locally.

## Build Commands

Requires **JDK 21+** to run Gradle; modules compile with a **JDK 25 toolchain** (auto-provisioned via the foojay resolver). Uses Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`).

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
├── llm           # LLM inference: SKaiNET two-model pipeline + REST_API backend
│                   model/    ModelCatalog, ModelResolver (hf:// download + cache), LocalModel, ModelProvider
│                   pipeline/ standupFlow DSL over sk.ainet.data.source.DataPipeline:
│                             QwenCommitsStage (tool call) → preprocess → RetryingSummarizeStage (LlamaGenerateStage)
│                   Key: LLMService interface, LLMServiceFactory, LlamaChatLLMService, RecordingGitCommitsTool
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

**Data flow**: CLI request → Qwen3 0.6B calls `get_recent_commits` (JGit via `data`) → commits → Llama 3.2 3B summary → parse/score → CLI output. MCP server exposes the git tools directly.

## Key Patterns

- **Kotlin Multiplatform expect/actual**: Common interfaces in `commonMain`, JVM implementations in `jvmMain` (`domain` additionally targets wasmJs)
- **LLM backend selection**: Factory pattern via `LLMServiceFactory`. Backend chosen by `MCP_LLM_BACKEND` env var (SKAINET, REST_API)
- **Pipeline DSL**: `standupFlow { commits = qwenToolCall(); preprocess {}; prompt {}; summarize(llama()); postprocess {} }` — every stage is a SKaiNET `PipelineStage`; add `transform(name) {}` / `finish(name) {}` for custom steps
- **Serialization**: kotlinx.serialization with `@Serializable` and `@SerialName` annotations for OpenAI-compatible JSON
- **Package prefix**: `de.jug_da.*`

## Environment Variables

| Variable | Purpose |
|----------|---------|
| `MCP_LLM_BACKEND` | Backend selection: SKAINET, REST_API |
| `STANDAPP_QWEN_MODEL_PATH` / `STANDAPP_LLAMA_MODEL_PATH` | Local GGUFs (skip download); `MCP_LLM_MODEL_PATH` = legacy alias for the Llama one |
| `STANDAPP_MODEL_CACHE_DIR` | Download cache (default `~/.cache/standapp/models`); `STANDAPP_OFFLINE=1` forbids network |
| `STANDAPP_SCHEDULE` / `STANDAPP_KV_CACHE` | Attention/SDPA schedule (`hardware`, `sequential` or a task count) and KV cache (`append`, `positional`); SKEEP-005 branches only |
| `MCP_LLM_REST_BASE_URL` | REST endpoint URL |
| `MCP_LLM_REST_MODEL` | Model name for REST endpoint |
| `BENCH_*` | Benchmark configuration (see README.md for full list) |

## Key Dependencies

- **Kotlin** 2.4.10, **Ktor** 3.5.2, **Kotlinx** Coroutines/Serialization/DateTime/IO
- **SKaiNET** 0.53.0 + **SKaiNET-transformers** 0.53.0 (Kotlin-native LLM inference, GGUF models, agent loop, data pipeline; BOM-managed; local checkout via `-PuseLocalTransformers=true`)
- **Eclipse JGit** 7.7.0 (Git repository access)
- **MCP Kotlin SDK** 0.8.3 (Model Context Protocol)
- **Koog** 1.0.0 (cloud-api:agent only), **Deliverance** 0.0.11-SNAPSHOT (benchmark-only, mavenLocal)
- **Models**: Qwen3-0.6B Q8_0 (tool calling, greedy, thinking off) and Llama-3.2-3B-Instruct Q4_K_M (summary); parameters temperature=0.1, topP=0.9, maxTokens=512
