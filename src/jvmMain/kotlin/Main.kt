import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val server = createLocalAIServer(port = 8080)
    server.start(wait = false)
    println("Local AI server started on http://localhost:8080")

    try {
        val settings = OpenAIClientSettings(baseUrl = "http://localhost:8080")
        val client = OpenAILLMClient(apiKey = "local-no-key-needed", settings = settings)
        val executor = SingleLLMPromptExecutor(client)

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = OpenAIModels.Chat.GPT4o,
            systemPrompt = "You are a helpful assistant that answers questions concisely.",
        )

        val result: String = agent.run("What is Kotlin Multiplatform and why is it useful?")
        println(result)
    } finally {
        server.stop(1000, 2000)
        println("Local AI server stopped.")
    }
}
