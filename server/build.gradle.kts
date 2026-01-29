plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    api(project(":model"))

    implementation("io.ktor:ktor-server-core:${property("ktorVersion")}")
    implementation("io.ktor:ktor-server-cio:${property("ktorVersion")}")
    implementation("io.ktor:ktor-server-content-negotiation:${property("ktorVersion")}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktorVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("coroutinesVersion")}")
}

application {
    mainClass.set("com.example.llm.server.ServerMainKt")
}
