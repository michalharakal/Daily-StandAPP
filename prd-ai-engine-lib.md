# PRD: StandAPP AI Engine — Distributable KMP Library

## 1. Problem Statement

The Daily-StandAPP project contains a capable AI engine (LLM abstraction, prompt templates, scoring, commit formatting) tightly coupled to the application. Reusing this engine in other software requires copying modules and wiring dependencies manually. There is no standalone, versioned artifact that other Kotlin/Java/Android/iOS projects can consume from Maven Central.

**Goal:** Extract the AI engine + prompt system into a self-contained Kotlin Multiplatform library (`standapp-ai-engine`) published to Maven Central, so any project can add a single dependency and generate standup summaries from structured input.

---

## 2. Current Architecture (As-Is)

```
Daily-StandAPP monorepo
├── composeApp/       UI layer (Compose Multiplatform)
├── shared/           Cross-platform shared code
├── data/             Git data access (JGit)          ← tightly coupled to Git
├── domain/           Domain models (GitInfo, etc.)
├── llm/              LLM backends (REST, SKaiNET, JLama)
├── mcp-server/       MCP tool server
├── benchmark/        Benchmark runner + scoring
└── StandAPP-cli/     CLI entry point
```

### Key Coupling Points

| Concern | Current Location | Problem |
|---------|-----------------|---------|
| `GitInfo` data class | `data` module | Tied to JGit; consumers shouldn't need JGit |
| `LLMService` interface | `llm` (jvmMain) | JVM-only; no common API |
| `LLMServiceFactory` | `llm` (jvmMain) | Reads env vars directly; not library-friendly |
| Prompt templates | `benchmark` module | Buried in benchmark code; not reusable |
| Commit formatting | `benchmark` module | Duplicated from `mcp-server` |
| Scoring logic | `benchmark` module | Useful for validation but mixed with CLI runner |
| `LLMSummarizer` | `llm` (commonMain) | Right idea (`expect/actual`) but thin and incomplete |

---

## 3. Target Architecture (To-Be) — BCE Design

Following the [BCE (Boundary-Control-Entity)](https://bce.design/) pattern, the library is organized into three layers within a single Gradle module published as one artifact:

```
standapp-ai-engine (KMP library)
│
├── entity/           Domain models — pure data, no platform deps
│   ├── CommitInfo          Input: commit data (replaces GitInfo dependency)
│   ├── StandupSummary      Output: structured summary result
│   ├── SummaryCategory     Output: category with commits
│   ├── ScoredResult        Output: summary + quality scores
│   ├── EngineConfig         Configuration: backend, model, params
│   └── PromptType          Enum: SUMMARY, JSON
│
├── control/          Business logic — stateless orchestration
│   ├── SummaryEngine        Main orchestrator: input → formatted prompt → LLM → parsed output → scored result
│   ├── PromptBuilder        Assembles system + user prompts from templates + CommitInfo
│   ├── OutputParser         Parses LLM raw text into StandupSummary (JSON or heading-based)
│   ├── QualityScorer        Automated checks (T11-T15 from benchmark)
│   └── CommitFormatter      Serializes CommitInfo list into prompt-ready text
│
├── boundary/         External integration points
│   ├── LLMBackend           Interface: suspend fun generate(prompt, config) → String
│   ├── RestLLMBackend       Impl: Ktor HTTP to OpenAI-compatible API
│   ├── BuiltInBackends      Registry/factory for bundled backends
│   └── SummaryEngineBuilder DSL builder: the main public API entry point
│
└── Platform source sets
    ├── commonMain/    Entity + Control + Boundary interfaces
    ├── jvmMain/       RestLLMBackend (Ktor CIO), SKaiNET, JLama backends
    ├── androidMain/   RestLLMBackend (Ktor OkHttp)
    ├── iosMain/       RestLLMBackend (Ktor Darwin)
    └── wasmJsMain/    RestLLMBackend (Ktor JS)
```

### BCE Interaction Rules

```
Consumer App
     │
     ▼
 Boundary (SummaryEngineBuilder / LLMBackend)
     │
     ▼
 Control  (SummaryEngine → PromptBuilder → LLM call → OutputParser → QualityScorer)
     │
     ▼
 Entity   (CommitInfo, StandupSummary, ScoredResult — pure data, flows up)
```

- **Boundary → Control → Entity**: Strict downward dependency flow
- **Entity**: Zero dependencies, pure `@Serializable` data classes
- **Control**: Depends only on Entity; receives Boundary interfaces via constructor injection
- **Boundary**: Depends on Control + Entity; owns platform-specific implementations

---

## 4. Public API Surface

### 4.1 Entry Point — Builder DSL

```kotlin
// Minimal usage
val engine = StandupEngine {
    backend = RestLLMBackend("http://localhost:11434", model = "llama3.2:3b")
}

val result: StandupSummary = engine.summarize(commits, PromptType.SUMMARY)
```

```kotlin
// Full configuration
val engine = StandupEngine {
    backend = RestLLMBackend(
        baseUrl = "https://api.openai.com/v1",
        model = "gpt-4o-mini",
        apiKey = "sk-..."
    )
    maxTokens = 512
    temperature = 0.1f
    topP = 0.9f
    scoring = true  // attach quality scores to output
}

// Structured JSON output
val scored: ScoredResult = engine.summarizeAndScore(commits, PromptType.JSON)
```

```kotlin
// Custom backend (consumer-provided)
val engine = StandupEngine {
    backend = object : LLMBackend {
        override suspend fun generate(prompt: String, config: GenerationConfig): String {
            return myCustomLLM.infer(prompt)
        }
    }
}
```

### 4.2 Entity Types (commonMain)

```kotlin
@Serializable
data class CommitInfo(
    val id: String,
    val authorName: String,
    val authorEmail: String,
    val date: String,       // ISO-8601
    val message: String
)

@Serializable
data class StandupSummary(
    val raw: String,                          // Raw LLM output
    val date: String,                         // YYYY-MM-DD
    val author: String,
    val sections: List<SummarySection>,        // Parsed sections (Yesterday/Today/Blockers or categories)
    val promptType: PromptType
)

@Serializable
data class SummarySection(
    val name: String,                          // "Yesterday", "Bug Fixes", etc.
    val items: List<SummaryItem>
)

@Serializable
data class SummaryItem(
    val commitId: String? = null,
    val text: String,
    val status: Status = Status.UNKNOWN
)

enum class Status { DONE, IN_PROGRESS, UNKNOWN }
enum class PromptType { SUMMARY, JSON }

@Serializable
data class ScoredResult(
    val summary: StandupSummary,
    val scores: QualityScores
)

@Serializable
data class QualityScores(
    val jsonParseable: Boolean? = null,        // JSON prompt only
    val schemaCompliant: Boolean? = null,      // JSON prompt only
    val headingsPresent: Boolean? = null,      // Summary prompt only
    val allIdsValid: Boolean,
    val noHallucinatedIds: Boolean,
    val passCount: Int,                         // out of applicable checks
    val totalChecks: Int
)

data class GenerationConfig(
    val maxTokens: Int = 512,
    val temperature: Float = 0.1f,
    val topP: Float = 0.9f
)
```

### 4.3 Boundary Interface

```kotlin
// The single interface consumers implement for custom backends
interface LLMBackend {
    suspend fun generate(prompt: String, config: GenerationConfig): String
}
```

### 4.4 Built-in Backends

| Backend | Artifact | Platforms | Transport |
|---------|----------|-----------|-----------|
| `RestLLMBackend` | `standapp-ai-engine` (core) | All (common) | Ktor multiplatform HTTP |
| `SKaiNetBackend` | `standapp-ai-engine-skainet` | JVM | SKaiNET KLlama |
| `JLamaBackend` | `standapp-ai-engine-jlama` | JVM | JLama native |

Heavy JVM-only backends are **optional separate artifacts** to keep the core library lightweight.

---

## 5. Module & Artifact Structure

```
standapp-ai-engine/                    ← NEW top-level module
├── build.gradle.kts                   KMP setup + Maven Central publishing
├── src/
│   ├── commonMain/kotlin/
│   │   └── dev/standapp/engine/
│   │       ├── entity/                Pure data classes
│   │       ├── control/               Business logic
│   │       └── boundary/              LLMBackend interface + builder
│   ├── commonTest/kotlin/             Shared tests
│   ├── jvmMain/kotlin/                Ktor CIO client
│   ├── androidMain/kotlin/            Ktor OkHttp client
│   ├── iosMain/kotlin/                Ktor Darwin client
│   └── wasmJsMain/kotlin/             Ktor JS client
│
standapp-ai-engine-skainet/            ← Optional JVM-only addon
├── build.gradle.kts
└── src/jvmMain/kotlin/
    └── dev/standapp/engine/backend/
        └── SKaiNetBackend.kt
│
standapp-ai-engine-jlama/             ← Optional JVM-only addon
├── build.gradle.kts
└── src/jvmMain/kotlin/
    └── dev/standapp/engine/backend/
        └── JLamaBackend.kt
```

### Maven Coordinates

```
Group:    dev.standapp
Artifacts:
  dev.standapp:standapp-ai-engine:<version>           Core (all platforms)
  dev.standapp:standapp-ai-engine-skainet:<version>   SKaiNET backend (JVM)
  dev.standapp:standapp-ai-engine-jlama:<version>     JLama backend (JVM)
```

### Consumer Dependency (example)

```kotlin
// build.gradle.kts — any KMP or Android or JVM project
dependencies {
    implementation("dev.standapp:standapp-ai-engine:1.0.0")

    // Optional: local inference backends (JVM only)
    implementation("dev.standapp:standapp-ai-engine-skainet:1.0.0")
    implementation("dev.standapp:standapp-ai-engine-jlama:1.0.0")
}
```

---

## 6. What Moves Where (Migration Map)

| Current Location | Destination in Library | Action |
|-----------------|----------------------|--------|
| `domain/../GitInfo.kt` | `entity/CommitInfo.kt` | New class; GitInfo stays in app, mapped at boundary |
| `llm/../LLMService.kt` | `boundary/LLMBackend.kt` | Simplified interface; old interface stays for app compat |
| `llm/../RestApiLLMService.kt` | `boundary/RestLLMBackend.kt` | Extract + make multiplatform via Ktor engines |
| `llm/../SKaiNetLLMService.kt` | `standapp-ai-engine-skainet` | Move to addon artifact |
| `llm/../JLamaService.kt` | `standapp-ai-engine-jlama` | Move to addon artifact |
| `llm/../LLMServiceFactory.kt` | `boundary/SummaryEngineBuilder.kt` | Replace factory with DSL builder |
| `benchmark/../PromptTemplates.kt` | `control/PromptBuilder.kt` | Extract prompts as reusable templates |
| `benchmark/../CommitFormatter.kt` | `control/CommitFormatter.kt` | Move as-is |
| `benchmark/../Scoring.kt` | `control/QualityScorer.kt` | Extract automated checks (T11-T15) |
| `llm/../LLMSummarizer.kt` | Superseded by `SummaryEngine` | Deprecate in app; lib replaces it |

### App-Side Changes

The Daily-StandAPP monorepo becomes a **consumer** of its own library:

```kotlin
// mcp-server after migration
val engine = StandupEngine {
    backend = RestLLMBackend(ollamaUrl, model = ollamaModel)
}

// In tool handler:
val commits = gitClient.getCommits().map { it.toCommitInfo() }
val summary = engine.summarize(commits, PromptType.SUMMARY)
return summary.raw
```

---

## 7. Platform Support Matrix

| Platform | Core Engine | REST Backend | SKaiNET | JLama |
|----------|------------|-------------|---------|-------|
| JVM (Desktop/Server) | Yes | Ktor CIO | Yes | Yes |
| Android | Yes | Ktor OkHttp | No | No |
| iOS (arm64/simulator) | Yes | Ktor Darwin | No | No |
| WASM/JS (Browser) | Yes | Ktor JS | No | No |
| Native (Linux/macOS) | Yes | Ktor CUrl | No | No |

Core engine (entity + control) is pure `commonMain` Kotlin with zero platform dependencies. Only `RestLLMBackend` uses `expect/actual` for platform-specific Ktor HTTP engines.

---

## 8. Publishing to Maven Central

### Requirements

| Requirement | Implementation |
|-------------|---------------|
| Group ID | `dev.standapp` (register on Sonatype OSSRH / Central Portal) |
| Signing | GPG key for artifact signing |
| POM metadata | License (Apache 2.0), SCM URL, developer info |
| Javadoc | Dokka-generated for all public API |
| Sources JAR | Kotlin sources for all source sets |
| CI/CD | GitHub Actions: build → test → sign → publish on tag |

### Gradle Publishing Setup

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish") // or maven-publish + signing
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set("StandAPP AI Engine")
        description.set("KMP library for generating developer standup summaries from commit data using local or cloud LLMs")
        url.set("https://github.com/niclas/Daily-StandAPP")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
    }
}
```

### Versioning

Semantic versioning: `MAJOR.MINOR.PATCH`
- `0.x.y` during initial development
- `1.0.0` when public API stabilizes

---

## 9. Prompt Architecture (Shipped with Library)

The library ships with built-in prompt templates as Kotlin string constants. Consumers can override them.

### Built-in Templates

```kotlin
object DefaultPrompts {
    val SYSTEM = "You are a developer assistant that creates concise standup summaries from Git commits."

    val SUMMARY_USER = """
        Summarize the following Git commits as a daily standup update.
        Use exactly these markdown headings:
        ## Yesterday
        ## Today
        ## Blockers

        Reference commit IDs where relevant. Be concise and actionable.

        Commits:
        {{commits}}
    """.trimIndent()

    val JSON_USER = """
        Analyze the following Git commits and produce a JSON object with this exact schema:
        {"date":"YYYY-MM-DD","author":"name","categories":[{"name":"...","commits":[{"id":"...","summary":"...","status":"done|in-progress|unknown"}]}],"blockers":["..."]}

        Return ONLY valid JSON, no markdown fences, no explanation.

        Commits:
        {{commits}}
    """.trimIndent()
}
```

### Custom Prompts

```kotlin
val engine = StandupEngine {
    backend = RestLLMBackend(...)
    prompts {
        system = "You are a project manager assistant..."
        user(PromptType.SUMMARY) = "Create a weekly report from: {{commits}}"
    }
}
```

---

## 10. Non-Functional Requirements

| NFR | Target |
|-----|--------|
| **Binary size** | Core artifact < 500 KB (excluding Ktor transitive deps) |
| **Min API level** | Android API 24+, iOS 15+, JVM 11+ |
| **Thread safety** | `SummaryEngine` is stateless and safe for concurrent use |
| **Coroutines** | All I/O operations are `suspend` functions; no blocking |
| **Serialization** | All entity types are `@Serializable` (kotlinx.serialization) |
| **Logging** | SLF4J on JVM, `NSLog` on iOS, `console` on WASM — via expect/actual |
| **No globals** | No singletons, no env var reads; everything via constructor/builder |
| **Testability** | `LLMBackend` is an interface — easily mocked in consumer tests |

---

## 11. Implementation Phases

### Phase 1 — Entity Layer + Core Control (Week 1)

- [ ] Create `standapp-ai-engine` KMP module in the monorepo
- [ ] Define all entity types in `commonMain` (`CommitInfo`, `StandupSummary`, etc.)
- [ ] Implement `CommitFormatter` (port from benchmark)
- [ ] Implement `PromptBuilder` with built-in templates
- [ ] Implement `OutputParser` (JSON parsing + heading extraction)
- [ ] Unit tests for all control logic with mock data

### Phase 2 — Boundary Layer + REST Backend (Week 2)

- [ ] Define `LLMBackend` interface in `commonMain`
- [ ] Implement `RestLLMBackend` with `expect/actual` Ktor engines per platform
- [ ] Implement `SummaryEngineBuilder` DSL
- [ ] Implement `SummaryEngine` orchestrator (wire control + boundary)
- [ ] Integration test: real Ollama call on JVM

### Phase 3 — Quality Scoring + JVM Backends (Week 3)

- [ ] Port `QualityScorer` (automated checks T11-T15) to `commonMain`
- [ ] Implement `ScoredResult` flow in `SummaryEngine`
- [ ] Create `standapp-ai-engine-skainet` addon module
- [ ] Create `standapp-ai-engine-jlama` addon module
- [ ] Tests for scoring with known good/bad outputs

### Phase 4 — Publishing Pipeline (Week 4)

- [ ] Register `dev.standapp` group on Maven Central (Sonatype)
- [ ] Configure Gradle publishing (POM, signing, Dokka)
- [ ] GitHub Actions CI: build + test all platforms
- [ ] GitHub Actions CD: publish on version tag
- [ ] Publish `0.1.0` snapshot for validation

### Phase 5 — Migrate Daily-StandAPP (Week 5)

- [ ] Replace `:llm` + `:benchmark` prompt logic with library dependency
- [ ] Add `GitInfo.toCommitInfo()` mapper in app's `:data` module
- [ ] Update MCP server to use `SummaryEngine`
- [ ] Update benchmark module to use library's `QualityScorer`
- [ ] Verify all existing benchmark cases pass unchanged

---

## 12. Success Criteria

| Criterion | Measure |
|-----------|---------|
| Library compiles for all KMP targets | `./gradlew :standapp-ai-engine:build` succeeds |
| Published to Maven Central | Artifact resolvable via `implementation("dev.standapp:...")` |
| Integration in 3 lines | Consumer needs: 1 dependency, 1 engine config, 1 `summarize()` call |
| No app regression | All 15 benchmark cases produce identical scores after migration |
| API documentation | KDoc on all public types; Dokka site generated |
| Custom backend works | Consumer can implement `LLMBackend` and use with engine |

---

## Appendix A: Dependency Graph

```
standapp-ai-engine (core)
├── kotlinx-serialization-json
├── kotlinx-coroutines-core
├── ktor-client-core
│   ├── ktor-client-cio         (jvmMain)
│   ├── ktor-client-okhttp      (androidMain)
│   ├── ktor-client-darwin       (iosMain)
│   └── ktor-client-js           (wasmJsMain)
└── ktor-client-content-negotiation
    └── ktor-serialization-kotlinx-json

standapp-ai-engine-skainet
├── standapp-ai-engine (core)
└── io.github.niclas:skainet-kllama:0.13.0

standapp-ai-engine-jlama
├── standapp-ai-engine (core)
└── com.github.tjake:jlama-core:0.8.4
```

## Appendix B: BCE Layer Rules

| Rule | Enforcement |
|------|-------------|
| Entity has no imports from control or boundary | Detekt / custom lint rule |
| Control imports only from entity | Package dependency check |
| Boundary may import control + entity | Normal |
| No circular dependencies | Gradle module boundaries (addons) |
| Cross-component communication via Boundary | `LLMBackend` interface is the only external integration point |
