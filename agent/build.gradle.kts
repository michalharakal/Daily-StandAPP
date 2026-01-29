plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation(project(":server"))

    implementation("ai.koog:koog-agents:${property("koogVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("coroutinesVersion")}")
}

application {
    mainClass.set("com.example.llm.agent.AgentMainKt")
}
