import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

val osName: String = System.getProperty("os.name").toLowerCase()
val osArch: String = System.getProperty("os.arch").toLowerCase()

val detectedOs = when {
    osName.contains("mac") -> "osx"
    osName.contains("linux") -> "linux"
    osName.contains("windows") -> "windows"
    else -> throw GradleException("Unsupported OS: $osName")
}

val detectedArch = when (osArch) {
    "x86_64", "amd64" -> "x86_64"
    "aarch64", "arm64" -> "aarch_64"
    else -> throw GradleException("Unsupported Arch: $osArch")
}

kotlin {

    jvmToolchain(21)  // Use JDK 17 toolchain for compilation (if using Java 17 features)
    jvm {
        compilations.all {
            kotlinOptions {
                // Target Java 17 bytecode (required for JDK 17 features)
                jvmTarget = "21"
                // Add JVM arguments: enable preview features and add incubator modules
                freeCompilerArgs += listOf(
                    "-Xadd-modules=jdk.incubator.vector"
                    // Note: Kotlin has no direct --enable-preview flag, see below
                )
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            // JLama backend
            implementation("com.github.tjake:jlama-core:0.8.4") {
                exclude("org.slf4j", "slf4j-log4j12")
            }
            implementation("com.github.tjake:jlama-native:0.8.4:$detectedOs-$detectedArch")

            implementation(libs.skainet.kllama)
            // SKaiNET LLM + Agent APIs (generateUntilStop, ChatMLTemplate, Tokenizer)
            implementation(libs.skainet.llm)
            implementation(libs.skainet.kllama.agents)
            // Explicit transitive deps needed for SKaiNET types used in source
            implementation(libs.skainet.lang.core)
            implementation(libs.skainet.io.core)
            implementation(libs.skainet.io.gguf)
            implementation(libs.skainet.backend.cpu)

            // Ktor HTTP client for REST API backend
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}
