package de.jug_da.standapp.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

actual fun getLLMSummarizer(): LLMSummarizer = AndroidLLMSummarizer()

class AndroidLLMSummarizer : LLMSummarizer {
    override suspend fun summarize(text: String): String {
        // Mock implementation for Android
        return "Android Summary: ${text.take(50)}..."
    }

    override fun summarize(text: String, callback: (String) -> Unit): String {
        // Mock implementation for Android
        val result = "Android Summary: ${text.take(50)}..."
        callback(result)
        return "Summarizing on Android..."
    }

    override fun summarizeStream(text: String): Flow<String> {
        // Mock implementation for Android - returns a simple flow
        return flowOf("Android", " Summary:", " ${text.take(30)}...")
    }
}