package de.jug_da.standapp.llm

import kotlinx.coroutines.flow.Flow

interface LLMSummarizer {
    suspend fun summarize(text: String): String
    fun summarize(text: String, callback: (String) -> Unit): String
    fun summarizeStream(text: String): Flow<String>
}

expect fun getLLMSummarizer(): LLMSummarizer

