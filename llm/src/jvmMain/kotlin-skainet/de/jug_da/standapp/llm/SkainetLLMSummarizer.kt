package de.jug_da.standapp.llm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch

class SkainetLLMSummarizer(private val service: SkainetKLlamaService) : LLMSummarizer {
    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun summarize(text: String): String = service.generate(
        prompt = text,
        maxTokens = 100,
        temperature = 0.7f,
        topP = 1f
    )

    override fun summarize(text: String, callback: (String) -> Unit): String {
        scope.launch {
            val result = try {
                service.generate(text, maxTokens = 100, temperature = 0.7f, topP = 1f)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            callback(result)
        }
        return "Summarizing..."
    }

    override fun summarizeStream(text: String): Flow<String> = callbackFlow {
        try {
            service.generateStream(text, 100, 0.7f, 1f) { token ->
                trySend(token)
            }
            close()
        } catch (e: Exception) {
            close(e)
        }
        awaitClose()
    }
}
