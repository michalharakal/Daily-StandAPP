package com.example.llmclient

import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    LlmClient().use { client ->
        // List models
        val models = client.listModels()
        println("Available models:")
        models.data.forEach { println("  - ${it.id}") }

        // Send a chat completion
        val response = client.chatCompletion(
            model = "local-stub",
            userMessage = "What is Kotlin Multiplatform?",
            systemMessage = "You are a helpful assistant.",
        )

        println("\nChat completion response:")
        println("  ID:    ${response.id}")
        println("  Model: ${response.model}")
        response.choices.forEach { choice ->
            println("  [${choice.index}] ${choice.message.content}")
        }
        println("  Usage: ${response.usage.totalTokens} tokens")
    }
}
