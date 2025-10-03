package de.jug_da.standapp.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

actual fun getLLMSummarizer(): LLMSummarizer = NativeLLMSummarizer()

class NativeLLMSummarizer : LLMSummarizer {
    override suspend fun summarize(text: String): String {
        // Mock implementation for Native
        return "Native Summary: ${text.take(50)}..."
    }

    override fun summarize(text: String, callback: (String) -> Unit): String {
        // Mock implementation for Native
        val result = "Native Summary: ${text.take(50)}..."
        callback(result)
        return "Summarizing on Native..."
    }

    override fun summarizeStream(text: String): Flow<String> {
        // Mock implementation for Native - returns a simple flow
        return flowOf("Native", " Summary:", " ${text.take(30)}...")
    }
}