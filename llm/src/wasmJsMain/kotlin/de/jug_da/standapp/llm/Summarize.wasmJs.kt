package de.jug_da.standapp.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

actual fun getLLMSummarizer(): LLMSummarizer = WasmLLMSummarizer()

class WasmLLMSummarizer : LLMSummarizer {
    override suspend fun summarize(text: String): String {
        // Mock implementation for WASM
        return "WASM Summary: ${text.take(50)}..."
    }

    override fun summarize(text: String, callback: (String) -> Unit): String {
        // Mock implementation for WASM
        val result = "WASM Summary: ${text.take(50)}..."
        callback(result)
        return "Summarizing on WASM..."
    }

    override fun summarizeStream(text: String): Flow<String> {
        // Mock implementation for WASM - returns a simple flow
        return flowOf("WASM", " Summary:", " ${text.take(30)}...")
    }
}