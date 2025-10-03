package de.jug_da.standapp.llm

interface LLMSummarizer {
    suspend fun summarize(text: String): String
    fun summarize(text: String, callback: (String) -> Unit): String
}

expect fun getLLMSummarizer(): LLMSummarizer

