plugins {
    kotlin("multiplatform") version "2.1.0"
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("ai.koog:koog-agents:0.6.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }

        jvmMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
            implementation("io.ktor:ktor-server-core:3.2.2")
            implementation("io.ktor:ktor-server-cio:3.2.2")
            implementation("io.ktor:ktor-server-content-negotiation:3.2.2")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.2")
        }
    }
}

tasks.register<JavaExec>("runAgent") {
    group = "application"
    mainClass.set("MainKt")
    val jvmJar = tasks.named("jvmJar")
    dependsOn(jvmJar)
    classpath = files(
        jvmJar.map { (it as Jar).archiveFile },
        configurations.named("jvmRuntimeClasspath")
    )
}

tasks.register<JavaExec>("runServer") {
    group = "application"
    mainClass.set("ServerMain")
    val jvmJar = tasks.named("jvmJar")
    dependsOn(jvmJar)
    classpath = files(
        jvmJar.map { (it as Jar).archiveFile },
        configurations.named("jvmRuntimeClasspath")
    )
    standardInput = System.`in`
}
