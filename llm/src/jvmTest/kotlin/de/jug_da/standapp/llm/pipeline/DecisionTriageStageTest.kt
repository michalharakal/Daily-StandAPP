package de.jug_da.standapp.llm.pipeline

import de.jug_da.data.git.GitInfo
import de.jug_da.standapp.llm.GitCommitSource
import de.jug_da.standapp.llm.LLMService
import de.jug_da.standapp.llm.model.LocalModel
import de.jug_da.standapp.llm.model.ModelProvider
import de.jug_da.standapp.llm.model.ModelSpec
import kotlinx.coroutines.test.runTest
import sk.ainet.decisions.AdapterCapabilities
import sk.ainet.decisions.AdapterOutcome
import sk.ainet.decisions.BooleanProbabilityQuestion
import sk.ainet.decisions.CalibrationStatus
import sk.ainet.decisions.ChoiceQuestion
import sk.ainet.decisions.Confidence
import sk.ainet.decisions.ConfidenceDefinition
import sk.ainet.decisions.DecisionAdapter
import sk.ainet.decisions.DecisionQuestion
import sk.ainet.decisions.DecisionState
import sk.ainet.decisions.InferencePolicy
import sk.ainet.decisions.LanguageSupport
import sk.ainet.decisions.OptionProbability
import sk.ainet.decisions.Prediction
import sk.ainet.decisions.Primitive
import sk.ainet.decisions.QuestionOutcome
import sk.ainet.decisions.QuestionTrace
import sk.ainet.decisions.ScoreQuestion
import sk.ainet.decisions.boolean
import sk.ainet.decisions.probabilityTrue
import sk.ainet.decisions.dsl.decisionSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class DecisionTriageStageTest {

    private val gitInfos = listOf(
        GitInfo("a001b02c3d4e5f6", "Greta Fischer", "greta@jug-da.de", Instant.parse("2025-09-22T08:00:00Z"), "Fix broken CI pipeline"),
        GitInfo("c003d04e5f6a7b8", "Greta Fischer", "greta@jug-da.de", Instant.parse("2025-09-22T09:00:00Z"), "Pin JVM toolchain"),
        GitInfo("e005f06a7b8c9d0", "Greta Fischer", "greta@jug-da.de", Instant.parse("2025-09-22T10:00:00Z"), "Merge branch 'develop'"),
    )

    private val noModels = object : ModelProvider {
        override suspend fun <R> withModel(spec: ModelSpec, block: suspend (LocalModel) -> R): R =
            error("no models available in unit tests (${spec.id})")

        override fun close() {}
    }

    private class FixedSource(private val result: List<GitInfo>) : GitCommitSource {
        override fun fetch(repoDir: String, author: String?, start: Instant, end: Instant): List<GitInfo> = result
    }

    private class FakeService(private val response: String) : LLMService {
        val prompts = mutableListOf<String>()
        override suspend fun generate(prompt: String, maxTokens: Int, temperature: Float, topP: Float): String {
            prompts += prompt
            return response
        }
    }

    /**
     * A decision model that reads the answer off the commit message: `Fix` is a confident
     * bug fix worth a stand-up, `Merge` is not, `Pin` leaves every question undecided.
     */
    private class ScriptedAdapter : DecisionAdapter {
        val states = mutableListOf<String>()
        var closed = false

        override val capabilities = AdapterCapabilities(
            modelId = "scripted",
            primitives = setOf(Primitive.CHOICE, Primitive.SCORE, Primitive.BOOLEAN_PROBABILITY),
            languages = LanguageSupport.Unrestricted,
            maxContextTokens = 1024,
            maxOptionTokens = 48,
            maxQuestionsPerRequest = 8,
            maxOptions = 6,
            batchSupport = false,
            backend = "test",
            precision = "fp32",
            calibration = CalibrationStatus.UNVERIFIED,
            calibrationIdentity = "none",
            preprocessingVersion = "test/1",
        )

        override fun predict(state: DecisionState, questions: List<DecisionQuestion>, inference: InferencePolicy): List<AdapterOutcome> {
            val text = (state as DecisionState.Text).text
            states += text
            val sure = "Fix" in text
            val no = "Merge" in text
            return questions.map { q ->
                val prediction: Prediction = when (q) {
                    is ChoiceQuestion -> {
                        val top = if (sure) 0.8 else if (no) 0.7 else 0.3
                        val rest = (1.0 - top) / (q.options.size - 1)
                        val selected = if (no) "chore" else "bugfix"
                        Prediction.Choice(
                            selectedOptionId = selected,
                            probabilities = q.options.map { OptionProbability(it.id, if (it.id == selected) top else rest) },
                            confidence = Confidence(if (sure || no) 0.6 else 0.05, ConfidenceDefinition.NORMALIZED_ENTROPY),
                        )
                    }
                    is ScoreQuestion -> {
                        val dist = if (sure) listOf(0.1, 0.6, 0.2, 0.05, 0.05) else List(q.levels.size) { 1.0 / q.levels.size }
                        Prediction.Score(
                            expected = dist.withIndex().sumOf { (i, p) -> (i + 1) * p },
                            selectedLevel = dist.indices.maxBy { dist[it] },
                            distribution = dist,
                            confidence = Confidence(if (sure) 0.5 else 0.0, ConfidenceDefinition.NORMALIZED_ENTROPY),
                        )
                    }
                    is BooleanProbabilityQuestion -> {
                        val p = when {
                            q.id == "standup" && sure -> 0.9
                            q.id == "standup" && no -> 0.1
                            else -> 0.5
                        }
                        Prediction.BooleanProbability(p, Confidence(maxOf(p, 1 - p), ConfidenceDefinition.TWO_WAY_MAX))
                    }
                }
                AdapterOutcome.Candidate(q.id, prediction, QuestionTrace(q.id, 32, 20, 0))
            }
        }

        override fun close() {
            closed = true
        }
    }

    private fun flowWith(adapter: ScriptedAdapter, service: FakeService) = standupFlow(noModels) {
        commits = gitCommits(FixedSource(gitInfos))
        preprocess { shortIds(7) }
        decide("triage", decisionModel("scripted") { adapter }) {
            choice("kind", "What kind of change is this commit?", "feature", "bugfix", "refactor", "docs", "test", "chore")
            score("risk", "How risky is this change?", "trivial", "low", "moderate", "high", "critical")
            probability("standup", "Is this commit worth mentioning in the stand-up?")
            probability("adr", "Does this commit carry an architecture decision?")
        }
        dropIrrelevant("standup")
        annotateTriage()
        summarize(llmService(service, name = "fake-llm"))
    }

    @Test
    fun triage_stages_appear_in_the_chain() {
        val flow = flowWith(ScriptedAdapter(), FakeService("## Yesterday\n- x\n## Today\n- y\n## Blockers\n- None"))
        assertEquals(
            "git-commits -> require-commits -> preprocess -> triage -> relevant -> annotate-triage -> summarize[prompt -> fake-llm -> parse x1]",
            flow.describe(),
        )
    }

    @Test
    fun decisions_filter_and_annotate_the_prompt() = runTest {
        val adapter = ScriptedAdapter()
        val service = FakeService("## Yesterday\n- x\n## Today\n- y\n## Blockers\n- None")
        flowWith(adapter, service).execute(StandupRequest(repoDir = "/repo", days = 1))

        assertEquals(3, adapter.states.size, "every commit is asked")
        assertTrue(adapter.states[0].startsWith("Git commit a001b02 by Greta Fischer on 2025-09-22"), adapter.states[0])
        assertTrue(adapter.closed, "the adapter is released after the batch")

        val prompt = service.prompts.single()
        assertTrue("[kind=bugfix risk=1 standup=true adr=?] Fix broken CI pipeline" in prompt, prompt)
        assertTrue("[kind=? risk=? standup=? adr=?] Pin JVM toolchain" in prompt, "abstentions stay, marked ?: $prompt")
        assertFalse("Merge branch" in prompt, "standup=no leaves the prompt: $prompt")
    }

    @Test
    fun triage_results_are_readable_from_the_batch() = runTest {
        val adapter = ScriptedAdapter()
        var seen: CommitBatch? = null
        val flow = standupFlow(noModels) {
            commits = gitCommits(FixedSource(gitInfos))
            decide("triage", decisionModel("scripted") { adapter }) {
                probability("standup", "Worth the stand-up?")
            }
            transform("capture") { seen = it; it }
            summarize(llmService(FakeService("## Yesterday\n- x\n## Today\n- y\n## Blockers\n- None"), name = "fake-llm"))
        }
        flow.execute(StandupRequest(repoDir = "/repo", days = 1))
        val batch = seen ?: error("transform did not run")
        assertEquals(true, batch.triage.getValue("a001b02c3d4e5f6").boolean("standup"))
        assertEquals(0.9, batch.triage.getValue("a001b02c3d4e5f6").probabilityTrue("standup"))
        assertTrue(batch.outcome("c003d04e5f6a7b8", "standup") is QuestionOutcome.Abstained)
        assertEquals(null, batch.triage.getValue("c003d04e5f6a7b8").boolean("standup"))
        assertEquals(0.5, batch.triage.getValue("c003d04e5f6a7b8").probabilityTrue("standup"))
        assertEquals(2, batch.dropIrrelevant("standup").commits.size)
        val onlyMerges = batch.copy(commits = batch.commits.filter { it.message.startsWith("Merge") })
        assertEquals(1, onlyMerges.dropIrrelevant("standup").commits.size, "a batch is never emptied by the filter")
    }

    @Test
    fun a_shared_schema_brings_its_own_policy() = runTest {
        // Stricter than the default: a boolean needs P(true) >= 0.95 to be accepted as true.
        val strict = decisionSchema("strict-triage") {
            probability("standup", "Worth the stand-up?")
            policy(version = "test") { boolean { abstainBetween(0.05, 0.95) } }
        }
        var seen: CommitBatch? = null
        val flow = standupFlow(noModels) {
            commits = gitCommits(FixedSource(gitInfos))
            decide("triage", decisionModel("scripted") { ScriptedAdapter() }, strict)
            transform("capture") { seen = it; it }
            summarize(llmService(FakeService("## Yesterday\n- x\n## Today\n- y\n## Blockers\n- None"), name = "fake-llm"))
        }
        flow.execute(StandupRequest(repoDir = "/repo", days = 1))
        val batch = seen ?: error("transform did not run")
        val fix = batch.triage.getValue("a001b02c3d4e5f6")
        assertEquals("strict-triage", fix.provenance.policyId)
        assertEquals(null, fix.boolean("standup"), "0.9 is below the strict 0.95 threshold")
        assertEquals(3, batch.dropIrrelevant("standup").commits.size, "nothing accepted as no, nothing dropped")
    }
}
