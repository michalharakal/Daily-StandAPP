package de.jug_da.standapp.llm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

actual fun getLLMSummarizer(): LLMSummarizer = when {
    System.getProperty("test.mode") == "true" -> MockJvmLLMSummarizer()
    else -> DefaultLLMSummarizer(LLMServiceFactory.create())
}

/**
 * Generic [LLMSummarizer] backed by any [LLMService] implementation.
 */
class DefaultLLMSummarizer(private val service: LLMService) : LLMSummarizer {

    private val scope = CoroutineScope(Dispatchers.IO)

    override suspend fun summarize(text: String): String = service.generate(
        prompt = text,
        temperature = LLMService.DEFAULT_TEMPERATURE,
        topP = LLMService.DEFAULT_TOP_P,
    )

    override fun summarize(text: String, callback: (String) -> Unit): String {
        scope.launch {
            val result = try {
                summarize(text)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            callback(result)
        }
        return "Summarizing..."
    }

    override fun summarizeStream(text: String): Flow<String> = flow {
        emit(summarize(text))
    }
}

class MockJvmLLMSummarizer : LLMSummarizer {
    override suspend fun summarize(text: String): String {
        return "Mock Summary: ${text.take(50)}..."
    }

    override fun summarize(text: String, callback: (String) -> Unit): String {
        val result = "Mock Summary: ${text.take(50)}..."
        callback(result)
        return "Summarizing..."
    }

    override fun summarizeStream(text: String): Flow<String> {
        return flowOf("Mock", " Summary:", " ${text.take(30)}...")
    }
}
