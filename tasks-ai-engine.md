# Tasks: StandAPP AI Engine Library Implementation

## Phase 1 — Entity Layer + Core Control

- [x] T1.1 Create `standapp-ai-engine` KMP module (build.gradle.kts, settings.gradle.kts)
- [x] T1.2 Define entity types in commonMain (CommitInfo, StandupSummary, SummarySection, SummaryItem, ScoredResult, QualityScores, GenerationConfig, PromptType, Status)
- [x] T1.3 Implement CommitFormatter (port from benchmark module)
- [x] T1.4 Implement PromptBuilder with built-in templates (port from benchmark PromptTemplates)
- [x] T1.5 Implement OutputParser (JSON parsing + heading extraction)
- [x] T1.6 Implement QualityScorer (port T11-T15 from benchmark Scoring)
- [x] T1.7 Unit tests for entity serialization
- [x] T1.8 Unit tests for CommitFormatter, PromptBuilder, OutputParser, QualityScorer

## Phase 2 — Boundary Layer + REST Backend

- [x] T2.1 Define LLMBackend interface in commonMain
- [x] T2.2 Implement RestLLMBackend with Ktor CIO (jvmMain)
- [x] T2.3 Implement SummaryEngineBuilder DSL
- [x] T2.4 Implement SummaryEngine orchestrator (wire control + boundary)
- [x] T2.5 Integration test: real Ollama call on JVM (gated by OLLAMA_INTEGRATION_TEST env var)

## Phase 3 — Quality Scoring + JVM Backends (addon modules)

- [x] T3.1 Wire QualityScorer into SummaryEngine (ScoredResult flow)
- [x] T3.2 Create `standapp-ai-engine-skainet` addon module
- [x] T3.3 Create `standapp-ai-engine-jlama` addon module
- [x] T3.4 Tests for scoring with known good/bad outputs

## Phase 4 — Publishing Pipeline

- [x] T4.1 Configure Gradle publishing (POM, signing, maven-publish for all 3 artifacts)
- [x] T4.2 GitHub Actions CI: build + test (updated build.yml)
- [x] T4.3 GitHub Actions CD: publish on version tag (publish.yml)

## Phase 5 — Migrate Daily-StandAPP

- [x] T5.1 Add CommitEntry.toCommitInfo() and GitInfo.toCommitInfo() mappers
- [x] T5.2 Replace benchmark prompt logic with library's PromptBuilder
- [x] T5.3 Delete superseded Scoring.kt, PromptTemplates.kt, CommitFormatter.kt from benchmark
- [x] T5.4 Update benchmark module to use library's QualityScorer
- [x] T5.5 Verify all tests pass (benchmark + engine: all green)
