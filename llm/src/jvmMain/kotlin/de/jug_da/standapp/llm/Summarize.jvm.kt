package de.jug_da.standapp.llm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose

actual fun getLLMSummarizer(): LLMSummarizer = if (System.getProperty("test.mode") == "true") {
    MockJvmLLMSummarizer()
} else {
    JvmLLMSummarizer(512)
}

class JvmLLMSummarizer(maxSeqLen: Int) : LLMSummarizer {
    private val scope = CoroutineScope(Dispatchers.IO)

    val llmService: JLamaService = JLamaService.create(
        modelPath = "",
        tokenizerPath = "",
        maxSequenceLength = maxSeqLen
    )


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

    override fun summarizeStream(text: String): Flow<String> = callbackFlow {
        try {
            llmService.generateStream(
                prompt = text,
                maxTokens = 100,
                temperature = 0.7f,
                topP = 1f
            ) { token ->
                trySend(token)
            }
            close()
        } catch (e: Exception) {
            trySend("Error: ${e.message}")
            close(e)
        }
        awaitClose()
    }
}

class MockJvmLLMSummarizer : LLMSummarizer {
    override suspend fun summarize(text: String): String {
        return "JVM Mock Summary: ${text.take(50)}..."
    }

    override fun summarize(text: String, callback: (String) -> Unit): String {
        val result = "JVM Mock Summary: ${text.take(50)}..."
        callback(result)
        return "Summarizing on JVM Mock..."
    }

    override fun summarizeStream(text: String): Flow<String> {
        return kotlinx.coroutines.flow.flowOf("JVM", " Mock", " Summary:", " ${text.take(30)}...")
    }
}