package de.jug_da.standapp.llm

import kotlinx.coroutines.flow.Flow

actual fun getLLMSummarizer(): LLMSummarizer = when {
    System.getProperty("test.mode") == "true" -> MockJvmLLMSummarizer()
    else -> {
        val config = LLMEngineConfig.getSkainetConfig()
        SkainetLLMSummarizer(SkainetKLlamaService.create(config))
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
        return kotlinx.coroutines.flow.flowOf("Mock", " Summary:", " ${text.take(30)}...")
    }
}
