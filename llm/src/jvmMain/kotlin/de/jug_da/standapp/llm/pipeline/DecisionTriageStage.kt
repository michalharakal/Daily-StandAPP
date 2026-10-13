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
import sk.ainet.decisions.Decision
import sk.ainet.decisions.DecisionAdapter
import sk.ainet.decisions.DecisionResult
import sk.ainet.decisions.ModelSelector
import sk.ainet.decisions.QuestionOutcome
import sk.ainet.decisions.dsl.DecisionSchema
import sk.ainet.decisions.dsl.acceptancePolicy
import sk.ainet.decisions.outcomeOrNull
import sk.ainet.decisions.runtime.DecisionRuntime
import sk.ainet.decisions.summary
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
 * Stand-up defaults for a schema that declares no policy of its own: a choice needs 0.2 entropy
 * confidence and a 0.1 margin to the runner-up, a score needs 0.35 mass on its top level, a
 * boolean is decided outside (0.4, 0.6). Inclusive thresholds; a candidate below them abstains.
 */
val DEFAULT_TRIAGE_POLICY: AcceptancePolicy = acceptancePolicy("standapp-triage", "1") {
    choice { minConfidence = 0.2; minMargin = 0.1 }
    score { minTopProbability(0.35) }
    boolean { abstainBetween(0.4, 0.6) }
}

/**
 * Asks a decision model the [schema]'s questions about every commit of the batch and stores
 * the [DecisionResult] per commit id in [CommitBatch.triage]. The schema's policy applies,
 * [DEFAULT_TRIAGE_POLICY] when it has none. The adapter is loaded when the
 * batch arrives and released when the stage is done, like the releasing [ModelProvider].
 */
class DecisionTriageStage(
    override val name: String,
    private val model: DecisionModelSource,
    private val schema: DecisionSchema,
    private val state: (CommitInfo) -> String = ::commitState,
) : PipelineStage<CommitBatch, CommitBatch> {

    private val policy: AcceptancePolicy = schema.policy ?: DEFAULT_TRIAGE_POLICY

    override suspend fun process(input: CommitBatch): CommitBatch {
        val started = System.nanoTime()
        val runtime = DecisionRuntime(listOf(model.adapter()))
        val loaded = (System.nanoTime() - started) / 1_000_000
        val results = LinkedHashMap<String, DecisionResult>()
        try {
            runtime.openSession(ModelSelector.Installed(model.modelId)).use { session ->
                for (commit in input.commits) {
                    // The stage's model wins over whatever the schema names: the adapter is loaded here.
                    val request = schema.request("commit-${commit.id}", state(commit), ModelSelector.Installed(model.modelId))
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
            "${results.size} commit(s) x ${schema.questions.size} question(s) on ${model.modelId}: " +
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
// `DecisionResult.summary()`, `choice/score/boolean(id)`, `probabilityTrue(id)` and
// `QuestionOutcome.candidate` come from `sk.ainet.decisions`; only the batch lookup is ours.

/** The outcome of [questionId] for [commitId], or null when the commit was not triaged. */
fun CommitBatch.outcome(commitId: String, questionId: String): QuestionOutcome? =
    triage[commitId]?.outcomeOrNull(questionId)

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

/** Prefixes every triaged commit message with its decisions, e.g. `[kind=bugfix risk=1 standup=true adr=?] …` (`?` abstained, `!` failed, score levels 0-based). */
fun CommitBatch.annotateTriage(): CommitBatch = copy(
    commits = commits.map { commit ->
        val result = triage[commit.id] ?: return@map commit
        commit.copy(message = "[${result.summary()}] ${commit.message}")
    },
)
