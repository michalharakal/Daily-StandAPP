package de.jug_da.standapp.llm.pipeline

import de.jug_da.standapp.llm.model.LayaCheckpointLoader
import de.jug_da.standapp.llm.model.LayaCheckpointResolver
import de.jug_da.standapp.llm.model.LayaCheckpointSpec
import de.jug_da.standapp.llm.model.ModelResolver
import dev.standapp.engine.entity.CommitInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.ainet.data.source.PipelineStage
import sk.ainet.decisions.AcceptancePolicy
import sk.ainet.decisions.BooleanAcceptance
import sk.ainet.decisions.BooleanCriteria
import sk.ainet.decisions.BooleanProbabilityQuestion
import sk.ainet.decisions.ChoiceAcceptance
import sk.ainet.decisions.ChoiceOption
import sk.ainet.decisions.ChoiceQuestion
import sk.ainet.decisions.Decision
import sk.ainet.decisions.DecisionAdapter
import sk.ainet.decisions.DecisionQuestion
import sk.ainet.decisions.DecisionRequest
import sk.ainet.decisions.DecisionResult
import sk.ainet.decisions.ModelSelector
import sk.ainet.decisions.Prediction
import sk.ainet.decisions.QuestionOutcome
import sk.ainet.decisions.ScoreAcceptance
import sk.ainet.decisions.ScoreLevel
import sk.ainet.decisions.ScoreQuestion
import sk.ainet.decisions.runtime.DecisionRuntime
import sk.ainet.models.laya.LayaDecisionAdapter
import java.nio.file.Path

/**
 * A decision model the `decide { }` stage can ask: its installed id and a factory that
 * loads the adapter when the batch arrives (and whose `close()` releases it again).
 */
class DecisionModelSource(val modelId: String, val adapter: suspend () -> DecisionAdapter)

/** Laya multilingual: resolved like the GGUFs (flag, env, `hf://` download), loaded on the FP32 CPU path. */
fun layaDecisionModel(
    checkpoint: Path? = null,
    spec: LayaCheckpointSpec = LayaCheckpointSpec.MULTILINGUAL,
    log: (String) -> Unit = System.err::println,
): DecisionModelSource = DecisionModelSource(spec.id) {
    val resolver = ModelResolver(log = log)
    val dir = try {
        LayaCheckpointResolver(resolver, log = log).resolve(spec, checkpoint)
    } catch (t: Throwable) {
        resolver.close()
        throw t
    }
    val agent = LayaCheckpointLoader.load(dir, log)
    LayaDecisionAdapter(agent = agent, modelId = spec.id, onClose = { resolver.close() })
}

/**
 * The questions one `decide { }` stage asks about every commit, plus the acceptance policy
 * that turns the model's candidates into accepted decisions or abstentions.
 */
@StandupFlowDsl
class TriageQuestions {
    internal val questions = mutableListOf<DecisionQuestion>()

    /** Inclusive thresholds; a candidate below them abstains instead of being silently accepted. */
    var policy: AcceptancePolicy = DEFAULT_POLICY

    /** One option out of [options] (`id` to model-facing description); keep it to six or fewer. */
    fun choice(id: String, instructions: String, options: Map<String, String>) {
        questions += ChoiceQuestion(id, instructions, options.map { (key, description) -> ChoiceOption(key, description) })
    }

    /** One option out of [options], each its own description. */
    fun choice(id: String, instructions: String, vararg options: String) =
        choice(id, instructions, options.associateWith { it })

    /** An ordinal scale; [levels] are the descriptions from lowest to highest. */
    fun score(id: String, instructions: String, levels: List<String>) {
        questions += ScoreQuestion(id, instructions, levels.map { ScoreLevel(it) })
    }

    /** P(statement is true), with the two criteria the model scores against. */
    fun noul(id: String, statement: String, whenFalse: String = "no", whenTrue: String = "yes") {
        questions += BooleanProbabilityQuestion(id, statement, BooleanCriteria(whenFalse, whenTrue))
    }

    companion object {
        /**
         * Stand-up defaults: a choice needs 0.2 entropy confidence and a 0.1 margin to the runner-up,
         * a score needs 0.35 mass on its top level, a boolean is decided outside (0.4, 0.6).
         */
        val DEFAULT_POLICY = AcceptancePolicy(
            "standapp-triage",
            "1",
            ChoiceAcceptance(0.2, 0.1),
            ScoreAcceptance.MinTopProbability(0.35),
            BooleanAcceptance(0.4, 0.6),
        )
    }
}

/**
 * Asks a decision model the same typed questions about every commit of the batch and stores
 * the [DecisionResult] per commit id in [CommitBatch.triage]. The adapter is loaded when the
 * batch arrives and released when the stage is done, like the releasing [ModelProvider].
 */
class DecisionTriageStage(
    override val name: String,
    private val model: DecisionModelSource,
    private val questions: List<DecisionQuestion>,
    private val policy: AcceptancePolicy,
    private val state: (CommitInfo) -> String = ::commitState,
) : PipelineStage<CommitBatch, CommitBatch> {

    override suspend fun process(input: CommitBatch): CommitBatch {
        require(questions.isNotEmpty()) { "$name: no questions declared" }
        val started = System.nanoTime()
        val runtime = DecisionRuntime(listOf(model.adapter()))
        val loaded = (System.nanoTime() - started) / 1_000_000
        val results = LinkedHashMap<String, DecisionResult>()
        try {
            runtime.openSession(ModelSelector.Installed(model.modelId)).use { session ->
                for (commit in input.commits) {
                    val request = DecisionRequest.builder("commit-${commit.id}")
                        .state(state(commit))
                        .model(model.modelId)
                        .apply { questions.forEach { question(it) } }
                        .build()
                    val result = withContext(Dispatchers.Default) { session.decide(request, policy) }
                    results[commit.id] = result
                    StageLog.stage("TRIAGE", "${commit.id} ${result.summary()} (${result.provenance.durationMillis} ms)")
                }
            }
        } finally {
            runtime.close()
        }
        val total = (System.nanoTime() - started) / 1_000_000
        val outcomes = results.values.flatMap { it.outcomes }
        StageLog.stage(
            "TRIAGE",
            "${results.size} commit(s) x ${questions.size} question(s) on ${model.modelId}: " +
                "accepted=${outcomes.count { it is QuestionOutcome.Accepted }} " +
                "abstained=${outcomes.count { it is QuestionOutcome.Abstained }} " +
                "failed=${outcomes.count { it is QuestionOutcome.Failed }}; " +
                "load ${loaded} ms, decide ${total - loaded} ms",
        )
        return input.copy(triage = input.triage + results)
    }

    companion object {
        /** The state text the model sees for one commit. */
        fun commitState(commit: CommitInfo): String =
            "Git commit ${commit.id} by ${commit.authorName} on ${commit.date}:\n${commit.message}"
    }
}

// ---- reading triage results --------------------------------------------------

/** `kind=bugfix risk=2/5 standup=yes adr=?` — one token per question, `?` for an abstention. */
fun DecisionResult.summary(): String = outcomes.joinToString(" ") { "${it.questionId}=${it.shortValue()}" }

fun QuestionOutcome.shortValue(): String = when (this) {
    is QuestionOutcome.Accepted -> when (val d = decision) {
        is Decision.Choice -> d.optionId
        is Decision.Score -> "${d.level + 1}/${d.prediction.distribution.size}"
        is Decision.Boolean -> if (d.value) "yes" else "no"
    }
    is QuestionOutcome.Abstained -> "?"
    is QuestionOutcome.Failed -> "!"
}

/** The outcome of [questionId] for [commitId], or null when the commit was not triaged. */
fun CommitBatch.outcome(commitId: String, questionId: String): QuestionOutcome? =
    triage[commitId]?.outcomes?.firstOrNull { it.questionId == questionId }

/** P(true) of a boolean question, from the accepted decision or the abstained candidate. */
fun QuestionOutcome.probabilityTrue(): Double? = when (this) {
    is QuestionOutcome.Accepted -> (decision.prediction as? Prediction.BooleanProbability)?.probabilityTrue
    is QuestionOutcome.Abstained -> (candidate as? Prediction.BooleanProbability)?.probabilityTrue
    is QuestionOutcome.Failed -> null
}

/**
 * Drops the commits whose boolean [questionId] was accepted as `false`. Abstentions, failures
 * and untriaged commits stay: an uncertain model never silently removes a commit. When every
 * commit would go, none goes: the summariser still needs an input, and "nothing happened" is
 * a judgement the stand-up should make with the commits in view.
 */
fun CommitBatch.dropIrrelevant(questionId: String): CommitBatch {
    val kept = commits.filter { commit ->
        val outcome = outcome(commit.id, questionId)
        !(outcome is QuestionOutcome.Accepted && (outcome.decision as? Decision.Boolean)?.value == false)
    }
    if (kept.isEmpty() && commits.isNotEmpty()) {
        StageLog.stage("TRIAGE", "all ${commits.size} commit(s) decided $questionId=no; keeping them all so the summary has input")
        return this
    }
    if (kept.size != commits.size) {
        StageLog.stage("TRIAGE", "dropped ${commits.size - kept.size} commit(s) with $questionId=no, ${kept.size} left")
    }
    return copy(commits = kept)
}

/** Prefixes every triaged commit message with its decisions, e.g. `[kind=bugfix risk=2/5 standup=yes adr=?] …`. */
fun CommitBatch.annotateTriage(): CommitBatch = copy(
    commits = commits.map { commit ->
        val result = triage[commit.id] ?: return@map commit
        commit.copy(message = "[${result.summary()}] ${commit.message}")
    },
)
