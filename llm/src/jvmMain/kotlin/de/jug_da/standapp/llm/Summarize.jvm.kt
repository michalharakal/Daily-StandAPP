package de.jug_da.standapp.llm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

actual fun getLLMSummarizer(): LLMSummarizer = JvmLLMSummarizer(512)

class JvmLLMSummarizer(maxSeqLen: Int) : LLMSummarizer {
    private val scope = CoroutineScope(Dispatchers.IO)

    val llmService: LLMService = LLMServiceFactory.create()


    override suspend fun summarize(text: String): String = llmService.generate(
        prompt = text,
        maxTokens = 100,
        temperature = 0.7f,
        topP = 1f
    )

    override fun summarize(text: String, callback: (String) -> Unit): String {
        scope.launch {
            val result = try {
                llmService.generate(text, maxTokens = 100, temperature = 0.7f, topP = 1f)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            callback(result)
        }
        return "Summarizing..."
    }
}