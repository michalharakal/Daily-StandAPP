plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation(project(":model"))

    implementation("io.ktor:ktor-client-core:${property("ktorVersion")}")
    implementation("io.ktor:ktor-client-cio:${property("ktorVersion")}")
    implementation("io.ktor:ktor-client-content-negotiation:${property("ktorVersion")}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktorVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("coroutinesVersion")}")
}

application {
    mainClass.set("com.example.llm.client.ClientMainKt")
}
