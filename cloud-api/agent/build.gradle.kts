plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

dependencies {
    implementation(project(":cloud-api:server"))

    implementation(libs.koog.agents)
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("com.example.llm.agent.AgentMainKt")
}
