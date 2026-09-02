package de.jug_da.standapp.llm.pipeline

import de.jug_da.standapp.llm.GitCommitSource
import de.jug_da.standapp.llm.RecordingGitCommitsTool
import de.jug_da.standapp.llm.model.LocalModel
import de.jug_da.standapp.llm.model.ModelCatalog
import de.jug_da.standapp.llm.model.ModelProvider
import de.jug_da.standapp.llm.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.ainet.apps.kllama.chat.AgentConfig
import sk.ainet.apps.kllama.chat.AgentListener
import sk.ainet.apps.kllama.chat.AgentLoop
import sk.ainet.apps.kllama.chat.ChatMessage
import sk.ainet.apps.kllama.chat.ChatRole
import sk.ainet.apps.kllama.chat.QwenChatTemplate
import sk.ainet.apps.kllama.chat.Tool
import sk.ainet.apps.kllama.chat.ToolCall
import sk.ainet.apps.kllama.chat.ToolRegistry
import sk.ainet.apps.llm.PrefillStrategy
import sk.ainet.data.source.PipelineStage
import kotlin.time.Clock

/**
 * Function-calling stage: Qwen3 reads the request and calls
 * `get_recent_commits`; the tool records the structured commit list.
 *
 * The agent loop runs exactly one tool round (`maxToolRounds = 1`): the
 * model generates, the parsed `<tool_call>` is validated and executed, and
 * the loop returns without a second generation — Qwen's prose answer is
 * never needed. Thinking is disabled through the official empty
 * `<think></think>` prefill so a 0.6B model spends its tokens on the call.
 *
 * If the model does not produce a usable call (no call, wrong tool, schema
 * validation failure, token budget exhausted) the stage logs why and fetches
 * the commits directly, so the app always delivers a summary.
 */
class QwenCommitsStage(
    private val models: ModelProvider,
    private val spec: ModelSpec = ModelCatalog.QWEN3_0_6B,
    private val source: GitCommitSource = GitCommitSource.jgit(),
    private val extraTools: List<Tool> = emptyList(),
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    private val maxTokens: Int = DEFAULT_MAX_TOKENS,
) : PipelineStage<StandupRequest, CommitBatch> {

    override val name: String = "qwen-commits"

    /** The tool is day-based; explicit windows must use [GitCommitsStage]. */
    override fun validate(input: StandupRequest): Boolean = input.since == null

    override suspend fun process(input: StandupRequest): CommitBatch {
        val tool = RecordingGitCommitsTool(input.repoDir, source, { Clock.System.now() })
        val heard = mutableListOf<ToolCall>()
        val validationFailures = mutableListOf<String>()
        val assistantTexts = mutableListOf<String>()

        val outcome = runCatching {
            models.withModel(spec) { model ->
                withContext(Dispatchers.Default) {
                    runAgent(model, input, tool, heard, validationFailures, assistantTexts)
                }
            }
        }

        tool.failure?.let { throw GitAccessException("git tool failed: ${it.message}", it) }
        val recorded = tool.recorded
        if (outcome.isSuccess && recorded != null) {
            return CommitBatch(input, recorded.map { it.toCommitInfo() }, CommitSource.QWEN_TOOL_CALL)
        }

        val reason = buildString {
            append("calls=").append(heard.map { "${it.name}${it.arguments}" })
            if (validationFailures.isNotEmpty()) append(" validation=").append(validationFailures)
            outcome.exceptionOrNull()?.let { append(" error=").append(it.toString()) }
            if (assistantTexts.isNotEmpty()) append(" lastResponse=").append(assistantTexts.last().take(200).replace('\n', ' '))
        }
        StageLog.stage("FALLBACK", "Qwen did not produce a usable ${RecordingGitCommitsTool.TOOL_NAME} call ($reason); fetching commits directly")
        val direct = GitCommitsStage(source, commitSource = CommitSource.DIRECT_FALLBACK)
        return CommitBatch(input, direct.fetch(input).map { it.toCommitInfo() }, CommitSource.DIRECT_FALLBACK)
    }

    private fun runAgent(
        model: LocalModel,
        input: StandupRequest,
        tool: RecordingGitCommitsTool,
        heard: MutableList<ToolCall>,
        validationFailures: MutableList<String>,
        assistantTexts: MutableList<String>,
    ): String {
        val registry = ToolRegistry().apply {
            register(tool)
            extraTools.forEach(::register)
        }
        val template = QwenChatTemplate(enableThinking = false)
        val agent = AgentLoop(
            runtime = model.runtime,
            template = template,
            toolRegistry = registry,
            eosTokenId = model.primaryEosTokenId,
            config = AgentConfig(
                maxToolRounds = 1,
                maxTokensPerRound = maxTokens,
                temperature = 0.0f,
                prefillStrategy = PrefillStrategy.Batched(64),
            ),
            decode = { model.tokenizer.decode(it) },
        )
        val messages = mutableListOf(
            ChatMessage(ChatRole.SYSTEM, systemPrompt),
            ChatMessage(ChatRole.USER, userPrompt(input)),
        )
        StageLog.stage("QWEN", "tools=${registry.definitions().map { it.name }} maxTokens=$maxTokens temp=0.0 thinking=off")
        StageLog.block("QWEN RENDERED PROMPT", template.apply(messages, registry.definitions(), addGenerationPrompt = true))

        val started = System.nanoTime()
        var firstToken = true
        val listener = object : AgentListener {
            override fun onToken(token: String) {
                if (firstToken) {
                    StageLog.stage("QWEN PREFILL DONE", "first token after ${(System.nanoTime() - started) / 1_000_000}ms")
                    firstToken = false
                }
            }

            override fun onAssistantMessage(text: String) {
                assistantTexts += text
                StageLog.block("QWEN RESPONSE", text)
            }

            override fun onToolCalls(calls: List<ToolCall>) {
                heard += calls
                StageLog.stage("TOOL CALLS", calls.joinToString { "${it.name}(${it.arguments})" })
            }

            override fun onToolResult(call: ToolCall, result: String) {
                StageLog.stage("TOOL RESULT", "${call.name}: $result")
            }

            override fun onToolCallValidationFailed(call: ToolCall, reason: String) {
                validationFailures += "${call.name}: $reason"
                StageLog.stage("TOOL VALIDATION FAILED", "${call.name}: $reason")
            }
        }
        val result = agent.runWithEncoder(messages, encode = { model.tokenizer.encode(it) }, listener = listener)
        StageLog.stage("QWEN COMPLETE", "${(System.nanoTime() - started) / 1_000_000}ms")
        return result
    }

    private fun userPrompt(request: StandupRequest): String = buildString {
        append("Fetch the git commits from the last ${request.days} day(s)")
        request.author?.let { append(" by author \"$it\"") }
        append(" using ${RecordingGitCommitsTool.TOOL_NAME}. Pass days=${request.days} as a number")
        request.author?.let { append(" and author=\"$it\"") }
        append('.')
    }

    companion object {
        const val DEFAULT_MAX_TOKENS = 192

        const val DEFAULT_SYSTEM_PROMPT: String =
            "You are a git assistant. You must call the ${RecordingGitCommitsTool.TOOL_NAME} tool to look up " +
                "commits; never answer from memory. Call the tool exactly once with the days (integer) " +
                "and, only if the user names one, the author from the request."
    }
}
