import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

kotlin {

    jvmToolchain(21)
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                listOf(
                    "-Xjvm-default=all",
                    "-Xjdk-release=21",
                )
            )
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
        }
    }

    macosX64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain {
            kotlin.srcDir("src/jvmMain/kotlin-skainet")
            dependencies {
                // SKaiNET kllama - pure Kotlin LLM inference
                implementation(libs.skainet.apps.kllama)
                implementation(libs.skainet.lang.core)
                implementation(libs.skainet.lang.models)
                implementation(libs.skainet.backend.cpu)
                implementation(libs.skainet.io.core)
                implementation(libs.skainet.io.gguf)

                implementation(libs.skainet.kllama)
                // SKaiNET LLM + Agent APIs (generateUntilStop, ChatMLTemplate, Tokenizer)
                implementation(libs.skainet.llm)
                implementation(libs.skainet.kllama.agents)

                // Ktor HTTP client for REST API backend
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
    }
}

tasks.withType<Test> {
    systemProperty("test.mode", "true")
}
