package com.example.llm.server

fun main() {
    val server = createLocalAIServer(port = 8080)
    server.start(wait = true)
}
