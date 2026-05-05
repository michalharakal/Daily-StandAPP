package de.jug_da.standapp.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sk.ainet.apps.kllama.chat.AgentConfig
import sk.ainet.apps.kllama.chat.ChatMessage
import sk.ainet.apps.kllama.chat.ChatRole
import sk.ainet.apps.kllama.chat.ModelMetadata
import sk.ainet.apps.kllama.chat.Tool
import sk.ainet.apps.kllama.chat.ToolCallingSupportResolver
import sk.ainet.apps.kllama.chat.ToolDefinition
import sk.ainet.apps.kllama.chat.java.JavaAgentLoop
import sk.ainet.apps.kllama.chat.java.JavaTool
import sk.ainet.apps.kllama.java.KLlamaJava
import sk.ainet.apps.kllama.java.KLlamaSession
import java.nio.file.Path

/**
 * Tool-calling [LLMService]. Drives the model through the skainet-transformers
 * [JavaAgentLoop] with a registered set of [Tool]s.
 *
 * Why a separate service: [SKaiNetLLMService] uses the raw `KLlamaSession.generate`
 * path — it wraps the prompt in Llama 3 turn tags directly and never parses
 * tool calls. This service goes through the agent loop, which:
 *
 *  1. Renders the message list with the model's actual chat template
 *     (resolved via [ToolCallingSupportResolver]).
 *  2. Generates until EOS or `maxTokensPerRound`.
 *  3. Parses tool-call markers out of the raw output.
 *  4. Executes each tool, appends a `TOOL` message, re-renders, repeats.
 *
 * The service prints `[STAGE/...]` lines to stderr at every interesting
 * transition so a human running the CLI can follow what the model is doing.
 *
 * Note: [JavaAgentLoop] only exposes a streaming-token callback (no listener
 * for tool-call events). Mid-stream tool-call JSON is still visible because
 * Llama 3 emits it inline. A richer listener overload upstream would let
 * us log structured `onToolCalls` / `onToolResult` transitions; until then
 * the round-0 chat template render below is the most useful structured signal.
 */
class ToolCallingLLMService private constructor(
    private val session: KLlamaSession,
    private val tools: List<Tool>,
    private val systemPrompt: String,
    private val templateName: String,
) : LLMService, AutoCloseable {

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
    ): String = withContext(Dispatchers.Default) {
        // Force temperature ≥ 0.6 — same reasoning as in SKaiNetLLMService:
        // very low temps with this small model produce repetitive output.
        val effectiveTemp = maxOf(temperature, 0.6f)
        logStage("INPUT", "system=${systemPrompt.length}ch user=${prompt.length}ch tools=${tools.size} template=$templateName temp=$effectiveTemp maxTokens=$maxTokens")
        logBlock("SYSTEM PROMPT", systemPrompt)
        logBlock("USER PROMPT", prompt)
        tools.forEach { tool ->
            logStage("TOOL REGISTERED", "${tool.definition.name} — ${tool.definition.description}")
        }

        // Render the round-0 chat template manually so we can inspect what
        // the agent loop is about to send to the tokenizer. The agent loop
        // will re-render this on every round; we only log round 0.
        val provider = ToolCallingSupportResolver.resolveOrFallback(ModelMetadata(), templateName)
        val renderTemplate = provider.createChatTemplate()
        val previewMessages = listOf(
            ChatMessage(role = ChatRole.SYSTEM, content = systemPrompt),
            ChatMessage(role = ChatRole.USER, content = prompt),
        )
        val rendered = renderTemplate.apply(
            previewMessages,
            tools.map { it.definition },
            addGenerationPrompt = true,
        )
        logBlock("RENDERED CHAT TEMPLATE (round 0)", rendered)

        val agent = JavaAgentLoop.builder()
            .session(session)
            .systemPrompt(systemPrompt)
            .config(
                AgentConfig(
                    maxToolRounds = 5,
                    maxTokensPerRound = maxTokens,
                    temperature = effectiveTemp,
                )
            )
            .template(templateName)
            .metadata(ModelMetadata())
            .also { b -> tools.forEach { b.tool(it.toJavaTool()) } }
            .build()

        val started = System.nanoTime()
        var firstTokenLogged = false
        var tokensProduced = 0

        val finalAnswer = agent.chat(prompt) { token ->
            if (!firstTokenLogged) {
                val ttfb = (System.nanoTime() - started) / 1_000_000
                logStage("PREFILL DONE", "first token after ${ttfb}ms")
                firstTokenLogged = true
            }
            tokensProduced++
            System.out.print(token)
            System.out.flush()
        }

        val totalMs = (System.nanoTime() - started) / 1_000_000
        val tokSec = if (totalMs > 0) "%.2f".format(tokensProduced * 1000.0 / totalMs) else "n/a"
        logStage("COMPLETE", "tokensSeen=$tokensProduced totalMs=$totalMs ($tokSec tok/s) finalChars=${finalAnswer.length}")
        logBlock("FINAL ANSWER", finalAnswer)

        finalAnswer
    }

    /**
     * Bridge our [Tool] (the public skainet-transformers interface, which
     * uses kotlinx.serialization JsonObject) into a [JavaTool] (Map-based,
     * required by [JavaAgentLoop.Builder.tool]). The Map-to-JsonObject
     * conversion is a thin best-effort — adequate for our flat tool args.
     */
    private fun Tool.toJavaTool(): JavaTool {
        val outer: Tool = this
        return object : JavaTool {
            override val definition: ToolDefinition get() = outer.definition
            override fun execute(arguments: Map<String, Any?>): String {
                val jsonText = Json.encodeToString(
                    kotlinx.serialization.json.JsonElement.serializer(),
                    mapToJsonObject(arguments),
                )
                val parsed = Json.parseToJsonElement(jsonText) as JsonObject
                return outer.execute(parsed)
            }
        }
    }

    private fun mapToJsonObject(m: Map<String, Any?>): JsonObject {
        val entries = m.mapValues { (_, v) -> anyToJson(v) }
        return JsonObject(entries)
    }

    private fun anyToJson(v: Any?): kotlinx.serialization.json.JsonElement = when (v) {
        null -> kotlinx.serialization.json.JsonNull
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is String -> JsonPrimitive(v)
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            mapToJsonObject(v as Map<String, Any?>)
        }
        is List<*> -> kotlinx.serialization.json.JsonArray(v.map { anyToJson(it) })
        else -> JsonPrimitive(v.toString())
    }

    override fun close() {
        session.close()
    }

    private fun logStage(stage: String, detail: String) {
        System.err.println("[STAGE/$stage] $detail")
    }

    private fun logBlock(label: String, body: String) {
        System.err.println("[STAGE/$label] >>>")
        System.err.println(body)
        System.err.println("[STAGE/$label] <<<")
    }

    companion object {

        const val DEFAULT_SYSTEM_PROMPT: String =
            "You are a developer assistant. You can call tools to fetch data " +
                "from the user's git repository, then summarise the results. " +
                "When asked about recent commits, call the `get_recent_commits` " +
                "tool exactly once, then write a short standup summary."

        fun create(
            modelPath: Path,
            tools: List<Tool>,
            systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
            templateName: String = "llama3",
        ): ToolCallingLLMService {
            System.err.println("[STAGE/MODEL LOAD] path=$modelPath template=$templateName")
            val session = KLlamaJava.loadGGUF(modelPath, null)
            System.err.println("[STAGE/MODEL LOAD] done")
            return ToolCallingLLMService(session, tools, systemPrompt, templateName)
        }
    }
}
